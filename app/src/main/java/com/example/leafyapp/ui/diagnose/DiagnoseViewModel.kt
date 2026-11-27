package com.example.leafyapp.ui.diagnose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.repository.DiagnosisDraft
import com.example.leafyapp.data.repository.DiagnosisRepository
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.util.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

sealed class DiagnoseUiState {
    data class Pick(val plant: PlantProfile?, val photoPath: String?) : DiagnoseUiState()
    data class Loading(val photoPath: String) : DiagnoseUiState()
    data class Error(val message: String, val canRetry: Boolean) : DiagnoseUiState()
    data class Result(
        val photoPath: String,
        val draft: DiagnosisDraft,
        val selectedTasks: Set<String>,
        val appliedChanges: Set<String>,
        val saving: Boolean = false,
        val saveError: String? = null
    ) : DiagnoseUiState()
    data class Saved(val diagnosisId: String, val taskCount: Int) : DiagnoseUiState()
}

/**
 * 8.4 Kiểm tra sức khỏe cây (từ hồ sơ cây): ảnh lá → /predict_disease → gộp kế hoạch loài × bệnh × mức độ
 * → người dùng chọn việc điều trị / đổi lịch (QT06) → lưu (QT08: chỉ báo thành công sau khi Firestore xác nhận).
 */
class DiagnoseViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<DiagnoseUiState>()
    val state: LiveData<DiagnoseUiState> = _state

    private var plant: PlantProfile? = null
    private var photoPath: String? = null
    private var compressed: File? = null
    private var job: Job? = null
    private var preset: com.example.leafyapp.api.DiseaseResult? = null

    /** Mở với kết quả đã có từ màn hình chính (8.2): bỏ qua bước chụp/gọi API, gắn kết quả vào cây đã chọn. */
    fun loadWithResult(plantId: String, path: String, result: com.example.leafyapp.api.DiseaseResult) {
        if (plant != null) return
        photoPath = path
        preset = result
        _state.value = DiagnoseUiState.Loading(path)
        viewModelScope.launch {
            try {
                plant = DiagnosisRepository.getPlant(plantId)
                analyze()
            } catch (e: Exception) {
                _state.value = DiagnoseUiState.Error(ErrorMapper.map(e, "Tải hồ sơ cây").userMessage, canRetry = false)
            }
        }
    }

    fun load(plantId: String) {
        if (plant != null) return
        _state.value = DiagnoseUiState.Pick(null, photoPath)
        viewModelScope.launch {
            try {
                plant = DiagnosisRepository.getPlant(plantId)
                _state.value = DiagnoseUiState.Pick(plant, photoPath)
            } catch (e: Exception) {
                _state.value = DiagnoseUiState.Error(ErrorMapper.map(e, "Tải hồ sơ cây").userMessage, canRetry = false)
            }
        }
    }

    fun setPhoto(path: String) {
        photoPath = path
        preset = null
        compressed = null
        _state.value = DiagnoseUiState.Pick(plant, path)
    }

    fun backToPick() {
        _state.value = DiagnoseUiState.Pick(plant, photoPath)
    }

    fun analyze() {
        val p = plant ?: return
        val path = photoPath ?: return
        job?.cancel()
        _state.value = DiagnoseUiState.Loading(path)
        job = viewModelScope.launch {
            try {
                val r = preset ?: DiagnosisRepository.analyze(
                    compressed ?: ImageUtils.compress(getApplication(), path).also { compressed = it })
                val draft = DiagnosisRepository.prepare(
                    plant = p,
                    aiLabel = r.diseaseId.orEmpty(),
                    confidence = r.confidence,
                    modelSeverityLevel = r.severity?.level,
                    lesionAreaPercent = r.severity?.percentArea ?: 0.0,
                    modelVersion = null,
                    modelNote = r.note
                )
                // Mặc định chọn sẵn mọi việc điều trị; đề xuất đổi lịch để người dùng tự tích (QT06)
                val tasks = if (draft.snapshot?.normalCondition == true) emptySet()
                else draft.snapshot?.tasks.orEmpty().map { it.key }.toSet()
                _state.value = DiagnoseUiState.Result(path, draft, tasks, emptySet())
            } catch (e: Exception) {
                _state.value = DiagnoseUiState.Error(ErrorMapper.map(e, "Chẩn đoán bệnh trên lá").userMessage, canRetry = true)
            }
        }
    }

    fun toggleTask(key: String) = updateResult { it.copy(selectedTasks = it.selectedTasks.toggle(key)) }
    fun toggleChange(scheduleId: String) = updateResult { it.copy(appliedChanges = it.appliedChanges.toggle(scheduleId)) }

    fun save() {
        val r = _state.value as? DiagnoseUiState.Result ?: return
        if (r.saving) return
        _state.value = r.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                val id = DiagnosisRepository.save(r.draft, r.selectedTasks, r.appliedChanges)
                _state.value = DiagnoseUiState.Saved(id, r.selectedTasks.size)
            } catch (e: Exception) {
                _state.value = r.copy(saving = false, saveError = ErrorMapper.map(e, "Lưu chẩn đoán").userMessage)
            }
        }
    }

    private fun updateResult(f: (DiagnoseUiState.Result) -> DiagnoseUiState.Result) {
        val r = _state.value as? DiagnoseUiState.Result ?: return
        if (!r.saving) _state.value = f(r)
    }

    private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v
}

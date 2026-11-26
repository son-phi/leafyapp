package com.example.leafyapp.ui.diagnose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.api.DiseaseResult
import com.example.leafyapp.data.model.DiseaseInfo
import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.TreatmentSnapshot
import com.example.leafyapp.data.repository.DiagnosisRepository
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.data.repository.IdentifyResult
import com.example.leafyapp.data.repository.SpeciesRepository
import com.example.leafyapp.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

sealed class QuickUiState {
    data class Loading(val photoPath: String) : QuickUiState()
    data class Error(val message: String) : QuickUiState()
    data class Result(
        val photoPath: String,
        val identify: IdentifyResult?,
        /** Loài đang chọn: tốt nhất nếu ≥ 60%, hoặc người dùng chọn lại (QT01). */
        val species: Species?,
        val disease: DiseaseResult,
        val diseaseInfo: DiseaseInfo?,
        val severity: Severity,
        val plan: TreatmentSnapshot?
    ) : QuickUiState()
}

/**
 * 8.2 Chẩn đoán từ màn hình chính: ảnh → /analyze (loài + bệnh) → kế hoạch theo loài × bệnh × mức độ.
 * Chưa lưu gì (QT03): chỉ lưu khi người dùng Thêm cây trồng hoặc gắn ảnh vào một cây trong vườn.
 */
class QuickDiagnoseViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<QuickUiState>()
    val state: LiveData<QuickUiState> = _state

    private var photoPath = ""
    var compressed: File? = null
        private set

    fun start(path: String) {
        if (photoPath == path && _state.value != null) return
        photoPath = path
        analyze()
    }

    fun analyze() {
        _state.value = QuickUiState.Loading(photoPath)
        viewModelScope.launch {
            try {
                val file = compressed ?: ImageUtils.compress(getApplication(), photoPath).also { compressed = it }
                val a = DiagnosisRepository.analyzeHome(file)
                val best = a.species?.takeIf { it.isReliable }?.best?.species
                show(a.species, best, a.disease)
            } catch (e: Exception) {
                _state.value = QuickUiState.Error(ErrorMapper.map(e, "Chẩn đoán bệnh trên lá").userMessage)
            }
        }
    }

    fun selectCandidate(aiLabel: String) {
        val r = _state.value as? QuickUiState.Result ?: return
        val sp = r.identify?.candidates?.firstOrNull { it.aiLabel == aiLabel }?.species ?: return
        viewModelScope.launch { show(r.identify, sp, r.disease) }
    }

    fun selectSpecies(speciesId: String) {
        val r = _state.value as? QuickUiState.Result ?: return
        viewModelScope.launch {
            val sp = runCatching { SpeciesRepository.getAll().firstOrNull { it.id == speciesId } }.getOrNull() ?: return@launch
            show(r.identify, sp, r.disease)
        }
    }

    private suspend fun show(identify: IdentifyResult?, species: Species?, disease: DiseaseResult) {
        val label = disease.diseaseId.orEmpty()
        val (info, plan) = runCatching {
            DiagnosisRepository.previewPlan(species?.id, label, disease.severity?.level)
        }.getOrDefault(null to null)
        _state.value = QuickUiState.Result(photoPath, identify, species, disease, info,
            DiagnosisRepository.severityOf(label, info, disease.severity?.level), plan)
    }

    suspend fun myPlants(): List<PlantProfile> = DiagnosisRepository.myPlants()
}

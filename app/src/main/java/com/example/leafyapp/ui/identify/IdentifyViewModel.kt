package com.example.leafyapp.ui.identify

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.data.repository.IdentificationRepository
import com.example.leafyapp.data.repository.IdentifyResult
import com.example.leafyapp.data.repository.SpeciesRepository
import com.example.leafyapp.util.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

sealed class IdentifyUiState {
    data class Preview(val photoPath: String) : IdentifyUiState()
    data class Loading(val photoPath: String) : IdentifyUiState()
    data class Error(val message: String) : IdentifyUiState()

    /**
     * @param selectedAiLabel ứng viên AI đang chọn (null nếu chưa chọn hoặc chọn từ danh mục)
     * @param selectedSpecies loài trong danh mục đang chọn (null = chưa chọn / chưa có trong danh mục)
     */
    data class Result(
        val photoPath: String,
        val result: IdentifyResult,
        val selectedAiLabel: String?,
        val selectedSpecies: Species?,
        val fromCatalog: Boolean
    ) : IdentifyUiState() {
        val selectedCandidateConfidence: Double?
            get() = if (fromCatalog) null else result.candidates.find { it.aiLabel == selectedAiLabel }?.confidence
    }
}

class IdentifyViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<IdentifyUiState>()
    val state: LiveData<IdentifyUiState> = _state

    private var photoPath: String = ""
    var compressedFile: File? = null
        private set
    var identificationId: String? = null
        private set
    private var job: Job? = null

    fun setPhoto(path: String) {
        if (photoPath == path && _state.value != null) return
        photoPath = path
        _state.value = IdentifyUiState.Preview(path)
    }

    /** 8.1 bước 2–5: nén ảnh → POST /predict → tra species theo aiLabel → lưu identifications. */
    fun identify() {
        if (photoPath.isBlank()) return
        job?.cancel()
        _state.value = IdentifyUiState.Loading(photoPath)
        job = viewModelScope.launch {
            try {
                val file = compressedFile ?: ImageUtils.compress(getApplication(), photoPath).also { compressedFile = it }
                val result = IdentificationRepository.identify(file)
                val best = result.best
                // Độ tin cậy >= 60%: chọn sẵn loài tốt nhất. Dưới 60%: để người dùng tự chọn (QT01).
                _state.value = if (result.isReliable) {
                    IdentifyUiState.Result(photoPath, result, best.aiLabel, best.species, fromCatalog = false)
                } else {
                    IdentifyUiState.Result(photoPath, result, null, null, fromCatalog = false)
                }
                saveIdentification(result)
            } catch (e: Exception) {
                _state.value = IdentifyUiState.Error(ErrorMapper.map(e, "Nhận diện loài cây").userMessage)
            }
        }
    }

    private fun saveIdentification(result: IdentifyResult) {
        viewModelScope.launch {
            try {
                identificationId = IdentificationRepository.saveIdentification(result)
                (state.value as? IdentifyUiState.Result)?.selectedSpecies?.let { persistSelection(it) }
            } catch (e: Exception) {
                // Lưu lịch sử nhận diện là phụ: không chặn người dùng, chỉ ghi log.
                Log.w("IdentifyViewModel", "Không lưu được identifications: ${e.message}")
            }
        }
    }

    fun selectCandidate(aiLabel: String) {
        val current = _state.value as? IdentifyUiState.Result ?: return
        val candidate = current.result.candidates.find { it.aiLabel == aiLabel } ?: return
        _state.value = current.copy(selectedAiLabel = aiLabel, selectedSpecies = candidate.species, fromCatalog = false)
        candidate.species?.let { persistSelection(it) }
    }

    fun selectFromCatalog(speciesId: String) {
        val current = _state.value as? IdentifyUiState.Result ?: return
        viewModelScope.launch {
            val species = try { SpeciesRepository.getById(speciesId) } catch (e: Exception) { null } ?: return@launch
            _state.value = current.copy(selectedAiLabel = null, selectedSpecies = species, fromCatalog = true)
            persistSelection(species)
        }
    }

    private fun persistSelection(species: Species) {
        val id = identificationId ?: return
        viewModelScope.launch {
            try {
                IdentificationRepository.updateSelectedSpecies(id, species.id)
            } catch (e: Exception) {
                Log.w("IdentifyViewModel", "Không cập nhật selectedSpeciesId: ${e.message}")
            }
        }
    }
}

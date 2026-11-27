package com.example.leafyapp.ui.addplant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.data.repository.ImageUploadError
import com.example.leafyapp.data.repository.NewPlantRequest
import com.example.leafyapp.data.repository.PlantRepository
import com.example.leafyapp.data.repository.ScheduleDraft
import com.example.leafyapp.data.repository.SpeciesRepository
import com.example.leafyapp.AppConfig
import com.example.leafyapp.ui.notifications.CareReminderScheduler
import com.example.leafyapp.util.ImageUtils
import com.example.leafyapp.util.LocalPlantImages
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Date

/** Một dòng lịch chăm sóc đề xuất trên form (người dùng bật/tắt, sửa chu kỳ, ngày, giờ). */
data class ScheduleDraftUi(
    val id: String,
    val taskType: CareTaskType,
    val title: String,
    val note: String,
    val enabled: Boolean,
    val intervalDays: Int,
    val startDate: Date,
    val timeOfDay: String
)

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val plantId: String) : SaveState()
    data class Error(val message: String) : SaveState()
    data class UploadError(val message: String) : SaveState()
}

class AddPlantViewModel(app: Application) : AndroidViewModel(app) {

    private val _species = MutableLiveData<Species?>()
    val species: LiveData<Species?> = _species

    private val _photoPath = MutableLiveData<String?>()
    val photoPath: LiveData<String?> = _photoPath

    private val _plantedDate = MutableLiveData(today())
    val plantedDate: LiveData<Date> = _plantedDate

    private val _drafts = MutableLiveData<List<ScheduleDraftUi>>(emptyList())
    val drafts: LiveData<List<ScheduleDraftUi>> = _drafts

    /** Bản hiện hành của danh sách lịch (gồm cả thay đổi chu kỳ đang gõ, không phát lại UI). */
    private var currentDrafts: List<ScheduleDraftUi> = emptyList()
    val draftsSnapshot: List<ScheduleDraftUi> get() = currentDrafts

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private val _loadError = MutableLiveData<String?>()
    val loadError: LiveData<String?> = _loadError

    /** Tên gợi ý tự động; nếu người dùng đã tự sửa tên thì không ghi đè. */
    var suggestedName: String = ""
        private set
    private var identificationId: String? = null
    private var initialized = false

    // Id sinh trước để "Thử lại" ghi đè đúng tài liệu, không tạo cây trùng (QT08).
    private val plantId: String by lazy { PlantRepository.newPlantId() }

    fun init(speciesId: String?, photoPath: String?, identificationId: String?) {
        if (initialized) return
        initialized = true
        this.identificationId = identificationId
        _photoPath.value = photoPath
        if (!speciesId.isNullOrBlank()) selectSpecies(speciesId)
    }

    fun selectSpecies(speciesId: String) {
        viewModelScope.launch {
            try {
                val s = SpeciesRepository.getById(speciesId)
                if (s != null) applySpecies(s) else _loadError.value = "Không tìm thấy loài trong danh mục."
            } catch (e: Exception) {
                _loadError.value = ErrorMapper.map(e, "Tải thông tin loài").userMessage
            }
        }
    }

    private fun applySpecies(s: Species) {
        _species.value = s
        suggestedName = s.commonName
        currentDrafts = buildDrafts(s)
        _drafts.value = currentDrafts
    }

    /**
     * Lịch gợi ý theo careTemplates của loài. Ngày bắt đầu:
     * - Tưới nước: hôm nay.
     * - Bón phân: sau 14 ngày (cây mới mua/mới trồng cần ổn định rễ trước khi bón).
     * - Các việc khác: sau một chu kỳ.
     * Người dùng xác nhận trên form mới được lưu (QT06).
     */
    private fun buildDrafts(s: Species): List<ScheduleDraftUi> = s.suggestedTemplates().map { t ->
        val type = CareTaskType.parse(t.taskType)
        val offset = when (type) {
            CareTaskType.WATERING -> 0
            CareTaskType.FERTILIZING -> minOf(14, t.repeatIntervalDays)
            else -> t.repeatIntervalDays
        }
        ScheduleDraftUi(
            id = PlantRepository.newScheduleId(plantId),
            taskType = type,
            title = t.title.ifBlank { type.name },
            note = t.note,
            enabled = type == CareTaskType.WATERING || type == CareTaskType.FERTILIZING || type == CareTaskType.HEALTH_CHECK,
            intervalDays = t.repeatIntervalDays,
            startDate = addDays(today(), offset),
            timeOfDay = "08:00"
        )
    }

    fun updateDraft(id: String, transform: (ScheduleDraftUi) -> ScheduleDraftUi) {
        currentDrafts = currentDrafts.map { if (it.id == id) transform(it) else it }
        _drafts.value = currentDrafts
    }

    /** Cập nhật chu kỳ khi người dùng đang gõ: không phát lại LiveData để ô nhập không mất con trỏ. */
    fun updateDraftSilently(id: String, intervalDays: Int) {
        currentDrafts = currentDrafts.map { if (it.id == id) it.copy(intervalDays = intervalDays) else it }
    }

    fun setPlantedDate(date: Date) {
        _plantedDate.value = date
    }

    fun setPhoto(source: String?) {
        if (source == null) {
            _photoPath.value = null
            return
        }
        viewModelScope.launch {
            try {
                _photoPath.value = ImageUtils.compress(getApplication(), source).absolutePath
            } catch (e: Exception) {
                _loadError.value = ErrorMapper.map(e, "Nén ảnh").userMessage
            }
        }
    }

    fun isPlantedDateValid(date: Date = _plantedDate.value ?: today()): Boolean = !date.after(endOfToday())

    /** 8.3 bước 4: tải ảnh → ghi plants (+ careSchedules đã chọn) → chờ xác nhận. */
    fun save(name: String, uploadPhoto: Boolean = true) {
        val s = _species.value ?: return
        if (_saveState.value == SaveState.Saving) return
        val date = _plantedDate.value ?: today()
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val request = NewPlantRequest(
                    plantId = plantId,
                    species = s,
                    name = name,
                    plantedDate = date,
                    photo = _photoPath.value?.let { File(it) }?.takeIf { it.exists() },
                    uploadPhoto = uploadPhoto && AppConfig.STORAGE_ENABLED,
                    schedules = currentDrafts.filter { it.enabled }.map {
                        ScheduleDraft(
                            id = it.id, taskType = it.taskType, title = it.title,
                            repeatIntervalDays = it.intervalDays, startAt = it.startDate,
                            timeOfDay = it.timeOfDay, note = it.note
                        )
                    },
                    identificationId = identificationId
                )
                val savedId = PlantRepository.addPlant(request)
                request.photo?.let { LocalPlantImages.save(getApplication(), savedId, it) }
                scheduleReminders(savedId, name.trim(), request.schedules)
                _saveState.value = SaveState.Success(savedId)
            } catch (e: ImageUploadError) {
                _saveState.value = SaveState.UploadError(e.message ?: "")
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(ErrorMapper.map(e, "Thêm cây trồng").userMessage)
            }
        }
    }

    private fun scheduleReminders(plantId: String, plantName: String, schedules: List<ScheduleDraft>) {
        schedules.forEach { d ->
            val start = com.google.firebase.Timestamp(PlantRepository.withTime(d.startAt, d.timeOfDay))
            CareReminderScheduler.schedule(
                getApplication(),
                CareSchedule(
                    id = d.id, plantId = plantId, taskType = d.taskType.name, title = d.title,
                    startAt = start, nextDueAt = start, repeatIntervalDays = d.repeatIntervalDays,
                    note = d.note
                ),
                plantName
            )
        }
    }

    fun consumeSaveState() {
        if (_saveState.value !is SaveState.Saving) _saveState.value = SaveState.Idle
    }

    companion object {
        fun today(): Date = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        fun endOfToday(): Date = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time

        fun addDays(date: Date, days: Int): Date = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, days)
        }.time
    }
}

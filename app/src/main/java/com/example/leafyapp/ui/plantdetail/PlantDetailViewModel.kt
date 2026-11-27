package com.example.leafyapp.ui.plantdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.model.CareLog
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.data.repository.PlantDetailRepository
import com.example.leafyapp.data.repository.SpeciesRepository
import com.example.leafyapp.ui.notifications.CareReminderScheduler
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date

data class PlantDetailData(
    val plant: PlantProfile,
    val species: Species?,
    val schedules: List<CareSchedule>,
    val logs: List<CareLog>,
    val diagnoses: List<Diagnosis>
) {
    val activeCare get() = schedules.filter { it.kind == CareSchedule.KIND_CARE && it.active && it.status != CareSchedule.STATUS_PAUSED }
    val pausedCare get() = schedules.filter { it.kind == CareSchedule.KIND_CARE && (!it.active || it.status == CareSchedule.STATUS_PAUSED) }
    val activeTreatment get() = schedules.filter { it.kind == CareSchedule.KIND_TREATMENT && it.active && it.status == CareSchedule.STATUS_ACTIVE }
    /** Đợt điều trị của lần chẩn đoán gần nhất (kể cả việc đã xong) để tính tiến độ. */
    val currentPlanTasks: List<CareSchedule> get() {
        val did = diagnoses.firstOrNull()?.id ?: return emptyList()
        return schedules.filter { it.kind == CareSchedule.KIND_TREATMENT && it.sourceDiagnosisId == did }
    }
    fun dueSorted(list: List<CareSchedule>) = list.sortedBy { (it.nextDueAt ?: it.startAt)?.toDate()?.time ?: Long.MAX_VALUE }
}

/** 8.6 Hồ sơ cây trồng: tổng quan, lịch chăm sóc (thêm/sửa/tạm dừng/xóa/đánh dấu xong), lịch sử chẩn đoán & chăm sóc. */
class PlantDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val _data = MutableLiveData<PlantDetailData?>()
    val data: LiveData<PlantDetailData?> = _data
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    private val _deleted = MutableLiveData(false)
    val deleted: LiveData<Boolean> = _deleted

    private var plantId = ""
    private var species: Species? = null

    fun load(id: String) {
        if (plantId == id) return
        plantId = id
        viewModelScope.launch {
            combine(
                PlantDetailRepository.plant(id), PlantDetailRepository.schedules(id),
                PlantDetailRepository.logs(id), PlantDetailRepository.diagnoses(id)
            ) { p, s, l, d -> Triple(p, s, l to d) }
                .catch { _message.value = ErrorMapper.map(it, "Tải hồ sơ cây").userMessage }
                .collect { (p, s, ld) ->
                    if (p == null) { if (_data.value != null) _deleted.value = true; return@collect }
                    if (species?.id != p.speciesId) species = runCatching { SpeciesRepository.getById(p.speciesId) }.getOrNull()
                    // completedCount / lastDoneAt suy ra từ nhật ký (không lưu trong careSchedules)
                    s.forEach { sch ->
                        val mine = ld.first.filter { it.careScheduleId == sch.id }
                        sch.completedCount = mine.size
                        sch.lastDoneAt = mine.mapNotNull { it.performedAt }.maxByOrNull { it.toDate().time }
                    }
                    _data.value = PlantDetailData(p, species, s, ld.first, ld.second)
                    syncReminders(p, s)
                }
        }
    }

    private fun syncReminders(p: PlantProfile, schedules: List<CareSchedule>) {
        val ctx = getApplication<Application>()
        schedules.forEach {
            if (it.active && it.status != CareSchedule.STATUS_PAUSED) CareReminderScheduler.schedule(ctx, it, p.name)
            else CareReminderScheduler.cancel(ctx, it.id)
        }
    }

    private fun run(action: String, done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); done?.let { _message.value = it } }
            catch (e: Exception) { _message.value = ErrorMapper.map(e, action).userMessage }
        }
    }

    fun complete(s: CareSchedule) = run("Đánh dấu hoàn thành", "Đã ghi vào nhật ký chăm sóc") { PlantDetailRepository.complete(plantId, s) }
    fun setPaused(s: CareSchedule, paused: Boolean) = run("Cập nhật lịch") { PlantDetailRepository.setPaused(plantId, s, paused) }
    fun deleteSchedule(s: CareSchedule) = run("Xóa lịch") {
        PlantDetailRepository.deleteSchedule(plantId, s.id)
        CareReminderScheduler.cancel(getApplication(), s.id)
    }
    fun saveSchedule(id: String?, type: CareTaskType, title: String, interval: Int, time: String, firstDue: Date, note: String) =
        run("Lưu lịch chăm sóc", "Đã lưu lịch chăm sóc") { PlantDetailRepository.saveSchedule(plantId, id, type, title, interval, time, firstDue, note) }
    fun addLog(type: CareTaskType, title: String, at: Long, note: String) =
        run("Ghi nhật ký", "Đã ghi vào nhật ký chăm sóc") { PlantDetailRepository.addLog(plantId, type, title, at, note) }
    fun rename(name: String) = run("Đổi tên cây") { PlantDetailRepository.rename(plantId, name) }
    fun deletePlant() = run("Xóa cây") {
        _data.value?.schedules?.forEach { CareReminderScheduler.cancel(getApplication(), it.id) }
        PlantDetailRepository.deletePlant(plantId)
        com.example.leafyapp.util.LocalPlantImages.delete(getApplication(), plantId)
        _deleted.value = true
    }
    fun consumeMessage() { _message.value = null }
}

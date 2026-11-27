package com.example.leafyapp.util

import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.Threshold

/** Logic ngưỡng môi trường dùng chung (mục 5). Không lặp lại logic này ở nơi khác. */
object ThresholdUtils {

    /** Ngưỡng hiệu lực = useSpeciesThresholds ? species.threshold : customThreshold. */
    fun effective(plant: PlantProfile, species: Species?): Threshold? =
        if (plant.useSpeciesThresholds || plant.customThreshold == null) species?.threshold
        else plant.customThreshold

    /** Trả về danh sách lỗi (rỗng = hợp lệ): mọi cặp min < max. */
    fun validate(t: Threshold): List<String> {
        val errors = mutableListOf<String>()
        if (t.soilMoistureMin >= t.soilMoistureMax) errors += "Độ ẩm đất tối thiểu phải nhỏ hơn tối đa"
        if (t.temperatureMin >= t.temperatureMax) errors += "Nhiệt độ tối thiểu phải nhỏ hơn tối đa"
        if (t.airHumidityMin >= t.airHumidityMax) errors += "Độ ẩm không khí tối thiểu phải nhỏ hơn tối đa"
        if (t.lightMin >= t.lightMax) errors += "Ánh sáng tối thiểu phải nhỏ hơn tối đa"
        return errors
    }

    private fun n(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

    fun soilText(t: Threshold) = "${n(t.soilMoistureMin)}–${n(t.soilMoistureMax)} %"
    fun temperatureText(t: Threshold) = "${n(t.temperatureMin)}–${n(t.temperatureMax)} °C"
    fun humidityText(t: Threshold) = "${n(t.airHumidityMin)}–${n(t.airHumidityMax)} %"
    fun lightText(t: Threshold) = "${"%,d".format(t.lightMin.toLong())}–${"%,d".format(t.lightMax.toLong())} lux"
}

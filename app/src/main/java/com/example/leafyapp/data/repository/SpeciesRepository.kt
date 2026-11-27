package com.example.leafyapp.data.repository

import com.example.leafyapp.data.model.Species
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

/**
 * Danh mục loài cây (collection `species`). Danh mục nhỏ (~50 tài liệu) nên tải một lần
 * rồi giữ trong bộ nhớ; Firestore offline cache giúp vẫn đọc được khi mất mạng.
 */
object SpeciesRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val mutex = Mutex()
    @Volatile private var cache: List<Species>? = null

    suspend fun getAll(forceRefresh: Boolean = false): List<Species> = mutex.withLock {
        cache?.takeIf { !forceRefresh }?.let { return it }
        val snapshot = db.collection("species").get().await()
        val list = snapshot.toObjects(Species::class.java).sortedBy { it.commonName }
        cache = list
        list
    }

    suspend fun getById(id: String): Species? = getAll().find { it.id == id }

    /** Ánh xạ kết quả AI sang danh mục: so khớp CHÍNH XÁC theo aiLabel, không so tên hiển thị. */
    suspend fun findByAiLabel(aiLabel: String): Species? = getAll().find { it.aiLabel == aiLabel }

    suspend fun search(query: String): List<Species> {
        val all = getAll()
        val q = normalize(query)
        if (q.isBlank()) return all
        return all.filter { s ->
            normalize(s.commonName).contains(q) || normalize(s.scientificName).contains(q) ||
                normalize(s.englishName).contains(q) ||
                s.otherNames.any { normalize(it).contains(q) }
        }
    }

    // ----- Tương thích các màn cũ dùng model Plant (id số) — đọc từ `species`, không còn đọc danh mục cũ `plants` -----

    /** id số cho các màn cũ dùng model Plant: số thứ tự ổn định theo id loài (bắt đầu từ 1). */
    suspend fun plantIdOf(species: Species): Int = 1 + getAll().map { it.id }.sorted().indexOf(species.id)

    suspend fun asPlant(s: Species): com.example.leafyapp.data.model.Plant = com.example.leafyapp.data.model.Plant(
        id = plantIdOf(s),
        name = s.commonName,
        scientificName = s.scientificName,
        description = s.description,
        light = listOf(s.lightNeedLabel, s.lightDescription).filter { it.isNotBlank() }.joinToString(". "),
        watering = s.wateringDescription,
        soil = s.soilDescription,
        fertilizer = s.fertilizerDescription,
        temperature = s.temperatureDescription,
        humidity = s.humidityDescription,
        image = com.example.leafyapp.util.ImageUtils.directImageUrl(s.imageUrl).orEmpty(),
        popularity = if (s.imageUrl.isNullOrBlank()) 0 else 1
    )

    /** Toàn bộ danh mục dưới dạng Plant; loài có ảnh lên trước (dùng cho Trending / gợi ý). */
    suspend fun allAsPlants(): List<com.example.leafyapp.data.model.Plant> =
        getAll().map { asPlant(it) }.sortedWith(compareByDescending<com.example.leafyapp.data.model.Plant> { it.popularity }.thenBy { it.id })

    suspend fun findByPlantId(id: Int): Species? = getAll().firstOrNull { plantIdOf(it) == id }

    fun normalize(text: String): String {
        val lower = text.lowercase().replace('đ', 'd')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
    }
}

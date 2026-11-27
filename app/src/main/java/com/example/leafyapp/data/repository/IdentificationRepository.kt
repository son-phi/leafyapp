package com.example.leafyapp.data.repository

import com.example.leafyapp.api.ApiClient
import com.example.leafyapp.data.model.IdentificationCandidate
import com.example.leafyapp.data.model.Species
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** Một loài model đề xuất, đã ánh xạ sang danh mục (species = null nếu chưa có trong danh mục). */
data class SpeciesCandidate(
    val aiLabel: String,
    val confidence: Double, // %
    val species: Species?
)

data class IdentifyResult(val candidates: List<SpeciesCandidate>) {
    val best: SpeciesCandidate get() = candidates.first()
    val isReliable: Boolean get() = best.confidence >= IdentificationRepository.RELIABLE_THRESHOLD
}

/** Nhận diện loài cây (luồng 8.1): gọi POST /predict và lưu `identifications`. */
object IdentificationRepository {

    /** QT01: dưới 60% là chưa chắc chắn. */
    const val RELIABLE_THRESHOLD = com.example.leafyapp.data.model.RELIABLE_CONFIDENCE
    private const val TOP_K = 3
    private const val WRITE_TIMEOUT_MS = 20_000L

    private val db get() = FirebaseFirestore.getInstance()
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw RepositoryError("Bạn cần đăng nhập để sử dụng chức năng này.")

    /** [imageFile] là ảnh đã nén (ImageUtils.compress). */
    suspend fun identify(imageFile: File): IdentifyResult {
        val response = try {
            val body = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", imageFile.name, body)
            ApiClient.instance.identifySpecies(part, TOP_K)
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Gọi API /predict")
        }
        return resultFrom(response)
    }

    /** Ánh xạ phản hồi loài (/predict hoặc phần `species` của /analyze) sang danh mục `species`. */
    suspend fun resultFrom(response: com.example.leafyapp.api.PredictionResponse): IdentifyResult {
        val predictions = response.predictions.orEmpty()
            .sortedBy { it.rank }
            .filter { it.species.isNotBlank() }
            .take(TOP_K)
        if (predictions.isEmpty()) throw RepositoryError("Không nhận diện được cây trong ảnh. Hãy chụp lại rõ hơn.")

        val catalog = try {
            SpeciesRepository.getAll()
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Tải danh mục loài")
        }
        return IdentifyResult(predictions.map { p ->
            SpeciesCandidate(
                aiLabel = p.species,
                confidence = if (p.percent > 0f) p.percent.toDouble() else p.confidence * 100.0,
                species = catalog.find { it.aiLabel == p.species }
            )
        })
    }

    /** Lưu một bản ghi `identifications`. Trả về id. */
    suspend fun saveIdentification(result: IdentifyResult, imageUrl: String? = null): String {
        val ref = db.collection("users").document(uid).collection("identifications").document()
        val best = result.best
        // Đúng các trường của thiết kế. speciesId = loài khớp nhãn tốt nhất (≥ 60%); người dùng chọn lại thì cập nhật.
        val data = hashMapOf(
            "userId" to uid,
            "speciesId" to (if (result.isReliable) best.species?.id else null),
            "imageUrl" to imageUrl,
            "aiLabel" to best.aiLabel,
            "confidence" to best.confidence,
            "topCandidates" to result.candidates.map {
                IdentificationCandidate(it.aiLabel, it.confidence, it.species?.id)
            },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            withTimeout(WRITE_TIMEOUT_MS) { ref.set(data).await() }
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Lưu kết quả nhận diện")
        }
        return ref.id
    }

    suspend fun updateSelectedSpecies(identificationId: String, speciesId: String) {
        try {
            withTimeout(WRITE_TIMEOUT_MS) {
                db.collection("users").document(uid).collection("identifications").document(identificationId)
                    .update("speciesId", speciesId).await()
            }
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Cập nhật loài đã chọn")
        }
    }
}

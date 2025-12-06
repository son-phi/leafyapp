import android.util.Log
import com.example.leafyapp.DatabaseHelper
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseMigrator {

    fun migrateAllData(dbHelper: DatabaseHelper) {
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch() // Dùng Batch để ghi nhanh và an toàn hơn

        // 1. Di cư PLANTS
        val plants = dbHelper.getAllPlants()
        Log.d("MIGRATE", "Tìm thấy ${plants.size} cây để upload.")

        for (plant in plants) {
            // Dùng ID số (ví dụ "1", "2") làm Document ID để dễ mapping với UserPlant
            val docRef = firestore.collection("plants").document(plant.id.toString())
            batch.set(docRef, plant)
        }

        // 2. Di cư DISEASES
        val diseases = dbHelper.getAllDiseases()
        Log.d("MIGRATE", "Tìm thấy ${diseases.size} bệnh để upload.")

        for (disease in diseases) {
            val docRef = firestore.collection("diseases").document(disease.id.toString())
            batch.set(docRef, disease)
        }

        // 3. Thực thi (Commit)
        batch.commit()
            .addOnSuccessListener {
                Log.d("MIGRATE", "🎉 THÀNH CÔNG! Đã chuyển toàn bộ dữ liệu lên Firebase.")
            }
            .addOnFailureListener { e ->
                Log.e("MIGRATE", "🔥 THẤT BẠI: ${e.message}", e)
            }
    }
}
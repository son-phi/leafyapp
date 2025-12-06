package com.example.leafyapp.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.WeatherResponse
import com.example.leafyapp.data.network.RetrofitClient
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.ArrayList

class HomeViewModel : ViewModel() {
    private val _location = MutableLiveData("Viet Nam")
    val location: LiveData<String> = _location

    fun setLocation(name: String) { _location.value = name }


    val weatherData = MutableLiveData<WeatherResponse>()
    val error = MutableLiveData<String>()


    // Danh sách gốc (chứa toàn bộ cây tải từ Firebase)
    private val _allPlants = ArrayList<Plant>()

    // LiveData để Fragment quan sát (hiển thị lên UI)
    private val _plants = MutableLiveData<List<Plant>>()
    val plants: LiveData<List<Plant>> get() = _plants

    // Trạng thái loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    init {
        fetchPlantsFromFirebase()
    }

    private fun fetchPlantsFromFirebase() {
        _isLoading.value = true
        val db = FirebaseFirestore.getInstance()

        db.collection("plants")
            .get()
            .addOnSuccessListener { result ->
                val list = ArrayList<Plant>()
                for (document in result) {
                    // Chuyển đổi Document thành Plant Object
                    try {
                        val plant = document.toObject(Plant::class.java)
                        list.add(plant)
                    } catch (e: Exception) {
                        Log.e("Firebase", "Lỗi convert data: ${e.message}")
                    }
                }

                // Lưu vào danh sách gốc
                _allPlants.clear()
                _allPlants.addAll(list)

                // Cập nhật lên UI
                _plants.value = list
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                Log.e("Firebase", "Lỗi lấy dữ liệu: ", exception)
                _isLoading.value = false
            }
    }
    fun fetchWeather(city: String, apiKey: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getWeather(city, apiKey)
                weatherData.postValue(response)
                Log.d("Weather", "Temp: ${response.main.temp}")
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }
}

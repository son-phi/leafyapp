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
import com.google.firebase.firestore.Query
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

    // Biến LiveData riêng cho Trending
    private val _trendingPlants = MutableLiveData<List<Plant>>()
    val trendingPlants: LiveData<List<Plant>> = _trendingPlants

    init {
        fetchPlantsFromFirebase()
    }

    /** Trending: 5 loài trong danh mục `species` (loài có ảnh lên trước). */
    fun fetchTrendingPlants() {
        viewModelScope.launch {
            runCatching { com.example.leafyapp.data.repository.SpeciesRepository.allAsPlants() }
                .onSuccess { _trendingPlants.value = it.take(5) }
                .onFailure { Log.e("HomeViewModel", "Tải trending", it) }
        }
    }

    private fun fetchPlantsFromFirebase() {
        _isLoading.value = true
        viewModelScope.launch {
            runCatching { com.example.leafyapp.data.repository.SpeciesRepository.allAsPlants() }
                .onSuccess { list ->
                    _allPlants.clear(); _allPlants.addAll(list)
                    _plants.value = list
                }
                .onFailure { Log.e("Firebase", "Lỗi lấy dữ liệu: ", it) }
            _isLoading.value = false
        }
    }

    // Hàm lấy 5 cây phổ biến nhất
//    fun fetchTrendingPlants(): LiveData<List<Plant>> {
//        val trendingLiveData = MutableLiveData<List<Plant>>()
//
//        FirebaseFirestore.getInstance().collection("plants")
//            .orderBy("popularity", Query.Direction.DESCENDING) // Sắp xếp giảm dần
//            .limit(5) // Chỉ lấy 5 cây
//            .get()
//            .addOnSuccessListener { snapshot ->
//                val list = snapshot.toObjects(Plant::class.java)
//                trendingLiveData.value = list
//            }
//            .addOnFailureListener {
//                // Xử lý lỗi nếu cần
//            }
//
//        return trendingLiveData
//    }

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

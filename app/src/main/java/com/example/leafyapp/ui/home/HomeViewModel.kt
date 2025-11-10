package com.example.leafyapp.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.leafyapp.data.model.WeatherResponse
import com.example.leafyapp.data.network.RetrofitClient
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _location = MutableLiveData("Viet Nam")
    val location: LiveData<String> = _location

    fun setLocation(name: String) { _location.value = name }


    val weatherData = MutableLiveData<WeatherResponse>()
    val error = MutableLiveData<String>()
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

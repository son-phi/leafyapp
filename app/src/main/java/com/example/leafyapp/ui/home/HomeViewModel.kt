package com.example.leafyapp.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _location = MutableLiveData("Hà Nội")
    val location: LiveData<String> = _location

    fun setLocation(name: String) { _location.value = name }
}

package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val resultText = intent.getStringExtra("RESULT") ?: "Unknown"
        val mode = intent.getStringExtra("MODE") ?: "Plant"

        val fragment = if (mode == "Plant") {
            PlantFragment.newInstance(resultText)
        } else {
            DiseaseFragment.newInstance(resultText)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.resultContainer, fragment)
            .commit()
    }
}

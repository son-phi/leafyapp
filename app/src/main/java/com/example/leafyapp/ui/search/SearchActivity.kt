package com.example.leafyapp.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.ui.home.PlantAdapter
import com.example.leafyapp.ui.information.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnCancel: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: PlantAdapter

    private lateinit var dbHelper: DatabaseHelper

    private val handler = Handler(Looper.getMainLooper())
    private var workRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etSearch = findViewById(R.id.et_search)
        btnCancel = findViewById(R.id.btn_cancel)
        rv = findViewById(R.id.rv_results)

        // Initialize DB helper BEFORE any DB calls
        dbHelper = DatabaseHelper(this)

        // Adapter: reuse existing PlantAdapter (shows name + scientificName)
        adapter = PlantAdapter { plant ->
            // Open ResultActivity which will host PlantFragment
            // NOTE: PlantFragment uses getPlantById(plantId + 1) internally,
            // so we pass plant.id - 1 so that final DB lookup becomes plant.id.
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("RESULT_ID", (plant.id - 1))           // see note above
                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_CONF", 1.0f)                   // confidence 1.0 by default
                putExtra("RESULT_MODE", "Plant")                // mode = "Plant" so ResultActivity loads PlantFragment
            }
            startActivity(intent)
            // optionally finish search activity so back returns to Home
            finish()
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Load first 5 plants from DB (do this on IO)
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                // searchPlants("") returns all rows (LIKE '%%'), so take first 5
                dbHelper.searchPlants("").take(5)
            }
            adapter.submitList(list)
        }

        btnCancel.setOnClickListener {
            closeKeyboard()
            finish()
        }

        // Focus and open keyboard
        etSearch.requestFocus()
        showKeyboard()

        // Text change with debounce -> call DB search on IO
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                workRunnable?.let { handler.removeCallbacks(it) }
                workRunnable = Runnable {
                    val q = s?.toString()?.trim() ?: ""
                    performSearch(q)
                }
                handler.postDelayed(workRunnable!!, 250)
            }
        })
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                if (query.isBlank()) {
                    // show first 5 when empty, or show all: dbHelper.searchPlants("")
                    dbHelper.searchPlants("").take(5)
                } else {
                    dbHelper.searchPlants(query)
                }
            }
            adapter.submitList(results)
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun closeKeyboard() {
        val view = currentFocus ?: return
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

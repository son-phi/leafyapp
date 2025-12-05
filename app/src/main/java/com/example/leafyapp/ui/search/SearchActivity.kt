package com.example.leafyapp.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.R
import com.example.leafyapp.ui.home.PlantAdapter
import com.example.leafyapp.ui.information.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: AppCompatEditText
    private lateinit var btnCancel: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: PlantAdapter
    private lateinit var btnClear: ImageButton

    private lateinit var dbHelper: DatabaseHelper

    private val handler = Handler(Looper.getMainLooper())
    private var workRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // find views (single etSearch variable only)
        etSearch = findViewById(R.id.et_search)
        btnCancel = findViewById(R.id.btn_cancel)
        rv = findViewById(R.id.rv_results)
        btnClear = findViewById(R.id.btn_clear)

        // DB + adapter init
        dbHelper = DatabaseHelper(this)
        adapter = PlantAdapter { plant ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("RESULT_ID", (plant.id - 1))
                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_CONF", 1.0f)
                putExtra("RESULT_MODE", "Plant")
            }
            startActivity(intent)
            finish()
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Clear button behaviour
        btnClear.setOnClickListener {
            etSearch.text?.clear()
            etSearch.requestFocus()
            performSearch("")
            // don't call it.performClick() here — this would re-trigger the listener
        }

        // Show/hide clear button based on text
        btnClear.isVisible = !etSearch.text.isNullOrEmpty()
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // show/hide clear immediately
                btnClear.isVisible = !s.isNullOrEmpty()

                workRunnable?.let { handler.removeCallbacks(it) }
                workRunnable = Runnable {
                    val q = s?.toString()?.trim() ?: ""
                    performSearch(q)
                }
                handler.postDelayed(workRunnable!!, 250)
            }
        })

        // Load initial list
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                dbHelper.searchPlants("").take(5)
            }
            adapter.submitList(list)
        }

        btnCancel.setOnClickListener {
            closeKeyboard()
            finish()
        }

        etSearch.requestFocus()
        showKeyboard()
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                if (query.isBlank()) {
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

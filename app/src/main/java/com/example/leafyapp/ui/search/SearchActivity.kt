package com.example.leafyapp.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.ui.information.ResultActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: AppCompatEditText
    private lateinit var btnCancel: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: SearchAdapter // Sử dụng SearchAdapter của bạn
    private lateinit var btnClear: ImageButton

    // Thay DatabaseHelper bằng List lưu trữ tạm
    private var allPlants: List<Plant> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var workRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Find views
        etSearch = findViewById(R.id.et_search)
        btnCancel = findViewById(R.id.btn_cancel)
        rv = findViewById(R.id.rv_results)
        btnClear = findViewById(R.id.btn_clear)

        // Setup Adapter
        adapter = SearchAdapter { plant ->

            Log.d("DEBUG_CLICK", "Bạn vừa click vào: ${plant.name} - ID: ${plant.id}")

            val intent = Intent(this, ResultActivity::class.java).apply {
                // Lưu ý: ID trên Firebase có thể là String hoặc Int tùy lúc migrate.
                // Nếu ResultActivity cần Int, hãy đảm bảo plant.id là Int
                putExtra("RESULT_ID", plant.id) // Bỏ -1 nếu ID đã chuẩn
                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_CONF", 1.0f)
                putExtra("RESULT_MODE", "Plant")
            }
            startActivity(intent)
            // finish() // Có thể bỏ finish nếu muốn quay lại search
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Tải dữ liệu từ Firebase ngay khi mở màn hình
        fetchPlantsFromFirebase()

        // Xử lý nút Clear
        btnClear.setOnClickListener {
            etSearch.text?.clear()
            etSearch.requestFocus()
            performSearch("")
        }

        // Xử lý nhập liệu tìm kiếm
        btnClear.isVisible = !etSearch.text.isNullOrEmpty()
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClear.isVisible = !s.isNullOrEmpty()

                workRunnable?.let { handler.removeCallbacks(it) }
                workRunnable = Runnable {
                    val q = s?.toString()?.trim() ?: ""
                    performSearch(q)
                }
                handler.postDelayed(workRunnable!!, 250) // Delay nhỏ để mượt hơn
            }
        })

        btnCancel.setOnClickListener {
            closeKeyboard()
            finish()
        }

        etSearch.requestFocus()
        showKeyboard()
    }

    private fun fetchPlantsFromFirebase() {
        val db = FirebaseFirestore.getInstance()
        db.collection("plants")
            .get()
            .addOnSuccessListener { result ->
                val list = ArrayList<Plant>()
                for (document in result) {
                    try {
                        val plant = document.toObject(Plant::class.java)
                        list.add(plant)
                    } catch (e: Exception) {
                        Log.e("SearchActivity", "Error parsing plant", e)
                    }
                }
                // Lưu toàn bộ danh sách vào biến tạm
                allPlants = list

                // Hiển thị 5 cây đầu tiên gợi ý khi chưa gõ gì
                adapter.submitList(allPlants.take(5))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performSearch(query: String) {
        val filteredList = if (query.isBlank()) {
            allPlants.take(5) // Gợi ý mặc định
        } else {
            // Lọc cục bộ (nhanh và không tốn tiền mạng)
            allPlants.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.scientificName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filteredList)
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
package com.example.leafyapp.ui.profile // Hoặc package tương ứng của bạn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.databinding.ActivityFaqBinding
// import com.example.leafyapp.data.model.FaqItem (Nếu bạn để FaqItem ở file riêng)

// Nếu bạn chưa tách file model, có thể để class FaqItem ở cuối file này cũng được
data class FaqItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)

class FaqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaqBinding
    private lateinit var faqAdapter: FaqAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupRecyclerView()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish() // Đóng màn hình quay về Profile
        }
    }

    private fun setupRecyclerView() {
        // 1. Lấy dữ liệu (Trả lời câu hỏi: Dữ liệu ở đâu?)
        val data = getFaqList()

        // 2. Khởi tạo Adapter
        faqAdapter = FaqAdapter(data)

        // 3. Gán vào RecyclerView
        binding.rvFaq.apply {
            layoutManager = LinearLayoutManager(this@FaqActivity)
            adapter = faqAdapter
            setHasFixedSize(true)
        }
    }

    // --- NGUỒN DỮ LIỆU (Hardcode) ---
    private fun getFaqList(): List<FaqItem> {
        return listOf(
            FaqItem(
                question = "How to identify a plant?",
                answer = "When making a snap, make sure the light is good, and your plant is in the frame. You can photograph flowers, leaves, or an entire plant. Don't put multiple species in one frame - it can lead to misidentification."
            ),
            FaqItem(
                question = "Can not identify a plant",
                answer = "If you cannot identify the plant, try to use another photo. Make sure the plant is visible clearly and is a unique object in the picture. Try moving to better lighting."
            ),
            FaqItem(
                question = "How many plants can LeafyApp identify?",
                answer = "Our database supports over 20,000 species of plants, flowers, and trees, and we are constantly updating it with new data every day."
            ),
            FaqItem(
                question = "How can I quickly tell which plants need attention?",
                answer = "Go to 'My Garden' tab. Plants that need watering or care will have a red alert icon next to them based on your schedule."
            ),
            FaqItem(
                question = "How can I update plant care schedule?",
                answer = "Open the specific plant detail page, click on the 'Edit' (pencil icon) at the top right, and adjust the watering or fertilizing frequency."
            ),
            FaqItem(
                question = "I need some feature/functionality",
                answer = "We love hearing from you! Please go to Settings > Feedback and send us your request. We read every message."
            ),
            FaqItem(
                question = "End of Trial And Subscription Renewal",
                answer = "Your subscription will automatically renew unless auto-renew is turned off at least 24-hours before the end of the current period."
            )
        )
    }
}
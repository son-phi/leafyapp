package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.leafyapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class NoInfoFragment : Fragment(R.layout.fragment_no_info) {

    companion object {
        private const val ARG_LABEL = "arg_label"
        private const val ARG_CONF = "arg_conf"
        private const val ARG_MODE = "arg_mode"

        fun newInstance(label: String, confidence: Float, mode: String) =
            NoInfoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LABEL, label)
                    putFloat(ARG_CONF, confidence)
                    putString(ARG_MODE, mode)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mode = arguments?.getString(ARG_MODE) ?: "Plant"
        val isDisease = mode.equals("Disease", true)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvTips = view.findViewById<TextView>(R.id.tvTips)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnAgain = view.findViewById<MaterialButton>(R.id.btnIdentifyAgain)

        // Các block lỗi
        val blockTooClose = view.findViewById<View>(R.id.blockTooClose)
        val blockTooFar = view.findViewById<View>(R.id.blockTooFar)
        val blockMulti = view.findViewById<View>(R.id.blockMulti)

        // Ảnh ví dụ đúng + sai
        val imgGood = view.findViewById<ShapeableImageView>(R.id.imgGoodExample)
        val imgBad1 = view.findViewById<ShapeableImageView>(R.id.imgBad1)
        val imgBad2 = view.findViewById<ShapeableImageView>(R.id.imgBad2)
        val imgBad3 = view.findViewById<ShapeableImageView>(R.id.imgBad3)

        // Caption dưới mỗi ảnh sai
        val tvBad1 = view.findViewById<TextView>(R.id.tvBad1)
        val tvBad2 = view.findViewById<TextView>(R.id.tvBad2)
        val tvBad3 = view.findViewById<TextView>(R.id.tvBad3)

        // Title theo mode
        tvTitle.text = if (isDisease) "Không thể nhận diện bệnh" else "Không thể nhận diện cây"
        tvSubtitle.text = "Hãy thử chụp lại theo gợi ý bên dưới để nhận diện chính xác hơn."

        if (isDisease) {
            // ===== DISEASE MODE =====
            // Tips: nhấn mạnh chụp cận lá + chỉ lỗi “Quá xa”
            tvTips.text = "Chụp cận lá bị bệnh, lấy nét rõ.\nTránh chụp quá xa (lá quá nhỏ) sẽ khó nhận diện."

            // Chỉ giữ ô “Quá xa” — 2 ô còn lại INVISIBLE để layout cân
            blockTooClose.visibility = View.INVISIBLE
            blockMulti.visibility = View.INVISIBLE
            blockTooFar.visibility = View.VISIBLE

            imgGood.setImageResource(R.drawable.disease_good)
            imgBad2.setImageResource(R.drawable.disease_bad_far)

            // Caption
            tvBad2.text = "Quá xa"

        } else {
            // ===== PLANT MODE =====
            tvTips.text = "Chụp đủ sáng, lấy nét rõ, chỉ để 1 đối tượng trong khung hình."

            // Hiện đủ 3 ô
            blockTooClose.visibility = View.VISIBLE
            blockTooFar.visibility = View.VISIBLE
            blockMulti.visibility = View.VISIBLE

            // Ảnh minh hoạ cây (đã có sẵn trong XML bạn dùng)
            imgGood.setImageResource(R.drawable.plant_placeholder)
            imgBad1.setImageResource(R.drawable.plant_bad_1)
            imgBad2.setImageResource(R.drawable.plant_bad_2)
            imgBad3.setImageResource(R.drawable.plant_bad_3)

            // Caption
            tvBad1.text = "Quá gần"
            tvBad2.text = "Quá xa"
            tvBad3.text = "Nhiều cây"
        }

        // Back / Identify Again: đóng ResultActivity để quay về Camera
        btnBack.setOnClickListener { requireActivity().finish() }
        btnAgain.setOnClickListener { requireActivity().finish() }
    }
}

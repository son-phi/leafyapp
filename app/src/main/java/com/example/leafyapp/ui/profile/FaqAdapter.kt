package com.example.leafyapp.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FaqAdapter(private val faqList: List<FaqItem>) :
    RecyclerView.Adapter<FaqAdapter.FaqViewHolder>() {

    inner class FaqViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestion: TextView = itemView.findViewById(com.example.leafyapp.R.id.tv_question)
        val tvAnswer: TextView = itemView.findViewById(com.example.leafyapp.R.id.tv_answer)
        val ivArrow: ImageView = itemView.findViewById(com.example.leafyapp.R.id.iv_expand_arrow)
        val layoutAnswer: View = itemView.findViewById(com.example.leafyapp.R.id.layout_answer)
        val layoutQuestion: View = itemView.findViewById(com.example.leafyapp.R.id.layout_question)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.example.leafyapp.R.layout.item_faq, parent, false)
        return FaqViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        val item = faqList[position]

        holder.tvQuestion.text = item.question
        holder.tvAnswer.text = item.answer

        // Kiểm tra trạng thái isExpanded để ẩn/hiện
        val isExpanded = item.isExpanded
        holder.layoutAnswer.visibility = if (isExpanded) View.VISIBLE else View.GONE

        // Xoay mũi tên: Lên nếu mở, Xuống nếu đóng
        holder.ivArrow.rotation = if (isExpanded) 180f else 0f

        // Sự kiện Click vào câu hỏi
        holder.layoutQuestion.setOnClickListener {
            // Đảo ngược trạng thái
            item.isExpanded = !item.isExpanded
            // Cập nhật lại giao diện item này
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = faqList.size
}
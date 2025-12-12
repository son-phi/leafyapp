package com.example.leafyapp.data.model

import java.io.Serializable

data class Garden(
    var id: String = "",          // Để var để sau này gán ID từ Firebase vào dễ hơn
    val name: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis() // Thêm cái này để biết vườn tạo bao giờ
) : Serializable {
    // Constructor rỗng cho Firebase (Bắt buộc)
    constructor() : this("", "", "", "", emptyList(), 0L)
}
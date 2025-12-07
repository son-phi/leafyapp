package com.example.leafyapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    // Lấy user hiện tại
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Xử lý Liên kết (Link) tài khoản Google
    fun linkGoogleAccount(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            // 1. Nếu đang là Anonymous -> Thử Link
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthUserCollisionException) {
                        // 2. Nếu Email đã tồn tại -> Đăng nhập thẳng (Chuyển đổi user)
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError(it.message ?: "Lỗi chuyển đổi tài khoản") }
                    } else {
                        onError(e.message ?: "Lỗi liên kết")
                    }
                }
        } else {
            // 3. Nếu chưa đăng nhập -> Đăng nhập mới
            auth.signInWithCredential(credential)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.message ?: "Đăng nhập thất bại") }
        }
    }

    // Đăng xuất
    fun logout() {
        auth.signOut()
    }

    // Xóa tài khoản
    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onSuccess()
            } else {
                onError(task.exception?.message ?: "Lỗi xóa tài khoản")
            }
        }
    }
}
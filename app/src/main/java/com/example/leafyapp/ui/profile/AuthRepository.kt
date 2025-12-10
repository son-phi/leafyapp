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


    // Đăng ký/Liên kết bằng Email & Password
    fun linkEmailAccount(email: String, pass: String, name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = auth.currentUser

        // Tạo Credential từ Email/Pass
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, pass)

        if (currentUser != null && currentUser.isAnonymous) {
            // 1. Đang là Anonymous -> Link Account
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener {
                    // Cập nhật tên hiển thị (Full Name)
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    currentUser.updateProfile(profileUpdates).addOnCompleteListener {
                        onSuccess()
                    }
                }
                .addOnFailureListener { e ->
                    // Nếu Email đã tồn tại -> Báo lỗi (hoặc gợi ý đăng nhập)
                    onError(e.message ?: "Lỗi đăng ký")
                }
        } else {
            // 2. Chưa có user -> Tạo mới hoàn toàn (Sign Up)
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val user = result.user
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                        onSuccess()
                    }
                }
                .addOnFailureListener {
                    onError(it.message ?: "Đăng ký thất bại")
                }
        }
    }

    // Đăng nhập bằng Email & Password
    fun signInEmailAccount(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Hàm này không cần xử lý link với Anonymous vì user đã có tài khoản
        // và muốn đăng nhập thẳng.
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener {
                // Xử lý các lỗi: sai mật khẩu, email không tồn tại...
                onError(it.message ?: "Đăng nhập thất bại")
            }
    }
}
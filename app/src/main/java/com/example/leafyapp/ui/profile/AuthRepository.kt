package com.example.leafyapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Lấy user hiện tại
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Xử lý Liên kết (Link) tài khoản Google
    fun linkGoogleAccount(idToken: String,googlePhotoUrl: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        val handleSuccess = {
            val user = auth.currentUser
            if (user != null) {
                // Tách tên
                val parts = user.displayName?.split(" ") ?: listOf("")
                val firstName = parts.firstOrNull() ?: ""
                val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

                // Xử lý ảnh: Ưu tiên ảnh từ Google truyền vào (lấy nét hơn)
                // Nếu googlePhotoUrl null thì mới thử lấy user.photoUrl
                var finalPhotoUrl = googlePhotoUrl ?: user.photoUrl?.toString()

                // Mẹo: Ảnh Google mặc định khá mờ (s96-c), đổi sang s400-c để nét hơn
                if (finalPhotoUrl != null && finalPhotoUrl.contains("googleusercontent.com")) {
                    finalPhotoUrl = finalPhotoUrl.replace("s96-c", "s400-c")
                }

                // A. Cập nhật ngược lại vào Firebase Auth (để lần sau user.photoUrl có dữ liệu)
                if (finalPhotoUrl != null) {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setPhotoUri(android.net.Uri.parse(finalPhotoUrl))
                        .build()
                    user.updateProfile(profileUpdates)
                }

                // B. Lưu vào Firestore (QUAN TRỌNG: Đây là chỗ điền vào field photoUrl đang bị trống)
                saveUserToFirestore(
                    uid = user.uid,
                    first = firstName,
                    last = lastName,
                    full = user.displayName ?: "",
                    email = user.email ?: "",
                    photoUrl = finalPhotoUrl, // Truyền URL ảnh vào đây
                    onSuccess = onSuccess,
                    onError = onError
                )
            } else {
                onSuccess()
            }
        }

        if (currentUser != null && currentUser.isAnonymous) {
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener { handleSuccess() }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthUserCollisionException) {
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { handleSuccess() }
                            .addOnFailureListener { onError(it.message ?: "Lỗi chuyển đổi") }
                    } else {
                        onError(e.message ?: "Lỗi liên kết")
                    }
                }
        } else {
            auth.signInWithCredential(credential)
                .addOnSuccessListener { handleSuccess() }
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

    // Cập nhật tên người dùng (Lưu lên Firebase)
    fun updateUserProfile(firstName: String, lastName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return
        val fullName = "$firstName $lastName".trim()

        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(fullName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Lấy photoUrl hiện tại (từ Google hoặc đã lưu trước đó) để lưu lại vào Firestore
                    val currentPhotoUrl = user.photoUrl?.toString() ?: ""

                    saveUserToFirestore(user.uid, firstName, lastName, fullName, user.email ?: "", currentPhotoUrl, onSuccess, onError)
                } else {
                    onError(task.exception?.message ?: "Lỗi cập nhật Auth")
                }
            }
    }

    // Lấy thông tin chi tiết user từ Firestore (để lấy photoUrl chuẩn)
    fun getUserDetailsFromFirestore(onSuccess: (Map<String, Any>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onSuccess(document.data ?: emptyMap())
                }
            }
            .addOnFailureListener {
                // Nếu lỗi thì thôi, không làm gì (UI sẽ dùng dữ liệu mặc định của Auth)
            }
    }

    // Hàm phụ để lưu vào Firestore
    private fun saveUserToFirestore(
        uid: String,
        first: String,
        last: String,
        full: String,
        email: String,
        photoUrl: String?, // Thêm tham số này
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userMap = hashMapOf(
            "firstName" to first,
            "lastName" to last,
            "displayName" to full,
            "email" to email,
            "photoUrl" to (photoUrl ?: ""), // Lưu URL ảnh
            "lastUpdated" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .set(userMap, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Lỗi lưu Firestore") }
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
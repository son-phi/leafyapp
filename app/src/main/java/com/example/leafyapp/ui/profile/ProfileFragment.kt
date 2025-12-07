package com.example.leafyapp.ui.profile

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentProfileBinding
import com.example.leafyapp.ui.splash.SplashActivity // Import Activity Splash để quay về sau khi logout
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Firebase Auth instance
    private val auth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cấu hình Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.example.leafyapp.R.string.default_web_client_id)) // Lấy từ google-services.json
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        setupUI()
        setupClicks()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Đăng nhập Google thành công -> Lấy tài khoản
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(context, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            // TRƯỜNG HỢP 1: Đang là Anonymous -> Thực hiện LINK tài khoản
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener {
                    Toast.makeText(context, "Đã liên kết tài khoản Google thành công!", Toast.LENGTH_SHORT).show()
                    updateUIAfterLogin() // Cập nhật giao diện (ẩn banner Sign Up, hiện Logout)
                }
                .addOnFailureListener { e ->
                    // Nếu tài khoản Google này ĐÃ TỪNG được dùng ở máy khác rồi
                    // Firebase sẽ báo lỗi "CredentialAlreadyInUse"
                    if (e is FirebaseAuthUserCollisionException) {
                        Toast.makeText(context, "Email này đã có tài khoản. Đang chuyển đổi...", Toast.LENGTH_SHORT).show()
                        // Đăng nhập thẳng vào tài khoản đó (chấp nhận bỏ qua dữ liệu Anonymous hiện tại)
                        auth.signInWithCredential(credential).addOnSuccessListener {
                            updateUIAfterLogin()
                        }
                    } else {
                        Toast.makeText(context, "Lỗi liên kết: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // TRƯỜNG HỢP 2: Chưa đăng nhập gì cả (Login thường)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    updateUIAfterLogin()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Đăng nhập thất bại.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUIAfterLogin() {
        setupUI() // Gọi lại hàm setupUI để refresh giao diện
    }

    private fun setupUI() {
        // Lấy User ID từ Firebase và hiển thị
        val user = auth.currentUser
        if (user != null) {
            binding.tvUserId.text = user.uid
            // LOGIC KIỂM TRA ANONYMOUS
            if (user.isAnonymous) {
                // 1. Trạng thái ẨN DANH (GUEST)
//                binding.tvUsername.text = "Guest Gardener"
//                binding.tvEmail.text = "Unregistered Account"

                // HIỆN banner Sign Up
                binding.cardSignUpBanner.visibility = View.VISIBLE

                // ẨN nút Logout và Delete
                binding.btnLogout.visibility = View.GONE
                binding.btnDeleteAccount.visibility = View.GONE

                // ẨN cả các dòng kẻ (Divider) liên quan để đẹp hơn (tuỳ chọn)
                // binding.dividerLogout.visibility = View.GONE

            } else {
                // 2. Trạng thái ĐÃ ĐĂNG NHẬP
//                binding.tvUsername.text = user.displayName ?: "Gardener"
//                binding.tvEmail.text = user.email

                // ẨN banner Sign Up
                binding.cardSignUpBanner.visibility = View.GONE

                // HIỆN nút Logout và Delete
                binding.btnLogout.visibility = View.VISIBLE
                binding.btnDeleteAccount.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClicks() {
        // --- CÁC NÚT CŨ ---
        binding.rowLocation.setOnClickListener { openAppSettings() }

        // ... (các nút notification, restore cũ giữ nguyên) ...

        // --- CÁC NÚT MỚI (ACCOUNT) ---

        // 1. COPY USER ID
        binding.btnCopyUserid.setOnClickListener {
            val uid = binding.tvUserId.text.toString()
            copyToClipboard(uid)
        }

        // SỰ KIỆN CLICK VÀO BANNER SIGN UP
        binding.cardSignUpBanner.setOnClickListener {
            showSignUpBottomSheet()
        }

        // 2. LOGOUT
        binding.btnLogout.setOnClickListener {
            showConfirmationDialog(
                title = "Đăng xuất",
                message = "Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng?",
                positiveButtonTitle = "Đăng xuất"
            ) {
                performLogout()
            }
        }



        // 3. DELETE ACCOUNT
        binding.btnDeleteAccount.setOnClickListener {
            showConfirmationDialog(
                title = "Xóa tài khoản",
                message = "Cảnh báo: Hành động này không thể hoàn tác. Tất cả dữ liệu cây trồng của bạn sẽ bị xóa vĩnh viễn.",
                positiveButtonTitle = "Xóa vĩnh viễn"
            ) {
                performDeleteAccount()
            }
        }
    }

    // --- HÀM XỬ LÝ LOGIC ---

    private fun showSignUpBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(com.example.leafyapp.R.layout.bottom_sheet_login_options, null)
        dialog.setContentView(view)

        // Xử lý click trong BottomSheet
        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_login).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(com.example.leafyapp.R.id.btn_login_google).setOnClickListener {
            dialog.dismiss()
            // Gọi hàm đăng nhập Google của bạn ở đây
             performGoogleLogin()
            Toast.makeText(context, "Clicked Google Login", Toast.LENGTH_SHORT).show()
        }

        // Tương tự cho Facebook, Email...

        dialog.show()
    }

    // Hàm này được gọi khi bấm nút "Continue with Google"
    private fun performGoogleLogin() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }



    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("User ID", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép ID vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
    }

    private fun performLogout() {
        auth.signOut()
        navigateToSplash()
    }

    private fun performDeleteAccount() {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Tài khoản đã được xóa thành công.", Toast.LENGTH_LONG).show()
                navigateToSplash()
            } else {
                Toast.makeText(context, "Lỗi xóa tài khoản: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToSplash() {
        // Chuyển về màn hình Splash (hoặc Login) và xóa toàn bộ Activity stack cũ
        val intent = Intent(requireContext(), SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButtonTitle: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonTitle) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // ... (Hàm openAppSettings cũ giữ nguyên) ...
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.data = uri
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
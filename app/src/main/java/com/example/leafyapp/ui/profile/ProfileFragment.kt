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
import com.example.leafyapp.data.repository.AuthRepository
import com.example.leafyapp.databinding.FragmentProfileBinding
import com.example.leafyapp.ui.splash.SplashActivity
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
    private val authRepository = AuthRepository()
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

        setupGoogleClient()
        updateUI()
        setupClicks()
    }

    private fun setupGoogleClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.example.leafyapp.R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!

                // Gọi Repo để xử lý Link với Firebase
                authRepository.linkGoogleAccount(account.idToken!!,
                    onSuccess = {
                        Toast.makeText(context, "Kết nối thành công!", Toast.LENGTH_SHORT).show()
                        navigateToSplash()
                    },
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: ApiException) {
                Toast.makeText(context, "Google Sign In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val user = authRepository.getCurrentUser() // Lấy user từ Repository

        if (user != null) {
            binding.tvUserId.text = user.uid

            if (user.isAnonymous) {
                // TRẠNG THÁI: GUEST
//                binding.tvUsername.text = "Guest Gardener"
//                binding.tvEmail.text = "Unregistered Account"
                binding.cardSignUpBanner.visibility = View.VISIBLE
                binding.btnLogout.visibility = View.GONE
                binding.btnDeleteAccount.visibility = View.GONE
            } else {
                // TRẠNG THÁI: USER CHÍNH THỨC
//                binding.tvUsername.text = user.displayName ?: "Gardener"
//                binding.tvEmail.text = user.email
                binding.cardSignUpBanner.visibility = View.GONE
                binding.btnLogout.visibility = View.VISIBLE
                binding.btnDeleteAccount.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClicks() {
        binding.rowLocation.setOnClickListener { openAppSettings() }

        binding.rowNotifications.setOnClickListener {
            openNotificationSettings()
        }

        binding.btnCopyUserid.setOnClickListener {
            copyToClipboard(binding.tvUserId.text.toString())
        }

        binding.cardSignUpBanner.setOnClickListener {
            showSignUpBottomSheet()
        }

        binding.rowFaq.setOnClickListener {
            val intent = Intent(requireContext(), FaqActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            showConfirmationDialog("Đăng xuất", "Bạn muốn đăng xuất?", "Đồng ý") {
                // 1. Đăng xuất khỏi Firebase (Xử lý backend)
                authRepository.logout()

                // 2. Đăng xuất khỏi Google Client (QUAN TRỌNG: Để xóa cache tài khoản Google)
                googleSignInClient.signOut().addOnCompleteListener {
                    // 3. Sau khi Google sign out xong thì mới chuyển màn hình
                    navigateToSplash()
                }
            }
        }

        binding.btnDeleteAccount.setOnClickListener {
            showConfirmationDialog("Xóa tài khoản", "Hành động này sẽ xóa vĩnh viễn dữ liệu.", "Xóa vĩnh viễn") {
                // Gọi Repo xử lý xóa
                authRepository.deleteAccount(
                    onSuccess = {
                        Toast.makeText(context, "Đã xóa tài khoản.", Toast.LENGTH_SHORT).show()
                        navigateToSplash()
                    },
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // --- HÀM XỬ LÝ LOGIC ---

    private fun showSignUpBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(com.example.leafyapp.R.layout.bottom_sheet_login_options, null)
        dialog.setContentView(view)

        // --- BƯỚC 1: Mở rộng toàn màn hình để có chỗ đẩy lên ---
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

        // --- BƯỚC 2: Cấu hình để Dialog tự co lại khi bàn phím hiện (Quan trọng nhất) ---
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Xử lý click trong BottomSheet
        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_login).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(com.example.leafyapp.R.id.btn_login_google).setOnClickListener {
            dialog.dismiss()
            // Gọi hàm đăng nhập Google của bạn ở đây
             performGoogleLogin()
        }

        // NÚT EMAIL
        view.findViewById<View>(com.example.leafyapp.R.id.btn_login_email).setOnClickListener {
            dialog.dismiss() // Đóng menu chọn
            showEmailSignUpForm() // Mở form điền thông tin
        }

        view.findViewById<View>(com.example.leafyapp.R.id.tv_login_link).setOnClickListener {
            dialog.dismiss()
            showEmailLoginForm() // Mở form đăng nhập
        }

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

    // 2. Hàm hiển thị Form Đăng Ký Email
    private fun showEmailSignUpForm() {
        val dialog = BottomSheetDialog(requireContext())
        // Để form full màn hình khi bàn phím hiện lên (tùy chọn)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

        val view = layoutInflater.inflate(com.example.leafyapp.R.layout.bottom_sheet_signup_email, null)
        dialog.setContentView(view)

        val etName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_fullname)
        val etEmail = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_email)
        val etPass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_password)
        val etRePass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_repassword)
        val cbPolicy = view.findViewById<android.widget.CheckBox>(com.example.leafyapp.R.id.cb_policy)
        val btnSignUp = view.findViewById<View>(com.example.leafyapp.R.id.btn_signup_confirm)

        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_signup).setOnClickListener { dialog.dismiss() }

        btnSignUp.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val rePass = etRePass.text.toString().trim()

            // Validate cơ bản
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "Vui lòng điền đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != rePass) {
                Toast.makeText(context, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                Toast.makeText(context, "Mật khẩu phải trên 6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!cbPolicy.isChecked) {
                Toast.makeText(context, "Bạn cần đồng ý với điều khoản", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gọi Repository để xử lý
            authRepository.linkEmailAccount(email, pass, name,
                onSuccess = {
                    Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    navigateToSplash()
                },
                onError = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            )
        }

        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_signup).setOnClickListener { dialog.dismiss() }

        // Thêm: Sự kiện chuyển sang màn hình Login
        view.findViewById<View>(com.example.leafyapp.R.id.tv_switch_login).setOnClickListener {
            dialog.dismiss()
            showEmailLoginForm() // Mở form đăng nhập
        }

        dialog.show()
    }

    // 3. Hàm hiển thị Form Đăng Nhập Email
    private fun showEmailLoginForm() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

        val view = layoutInflater.inflate(com.example.leafyapp.R.layout.bottom_sheet_login_email, null)
        dialog.setContentView(view)

        val etEmail = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_login_email)
        val etPass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_login_password)
        val btnLogin = view.findViewById<View>(com.example.leafyapp.R.id.btn_login_confirm)

        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_login_email).setOnClickListener { dialog.dismiss() }

        // Sự kiện chuyển sang màn hình Sign Up
        view.findViewById<View>(com.example.leafyapp.R.id.tv_switch_signup).setOnClickListener {
            dialog.dismiss()
            showEmailSignUpForm()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "Vui lòng điền Email và Mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gọi Repository để xử lý
            authRepository.signInEmailAccount(email, pass,
                onSuccess = {
                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    navigateToSplash()
                },
                onError = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            )
        }

        dialog.show()
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

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", requireContext().packageName)
                    putExtra("app_uid", requireContext().applicationInfo.uid)
                }
            }
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
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
import com.bumptech.glide.Glide
import com.example.leafyapp.data.repository.AuthRepository
import com.example.leafyapp.databinding.FragmentProfileBinding
import com.example.leafyapp.ui.splash.SplashActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable


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
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!

                // LẤY ẢNH TỪ GOOGLE Ở ĐÂY
                val googlePhotoUrl = account.photoUrl?.toString()
                android.util.Log.d("CHECK_AVATAR", "1. Link ảnh từ Google: $googlePhotoUrl")

                // Truyền googlePhotoUrl vào hàm
                authRepository.linkGoogleAccount(account.idToken!!, googlePhotoUrl,
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
        val user = authRepository.getCurrentUser()

        if (user != null) {
            // --- GIAI ĐOẠN 1: Hiển thị tạm thời từ Auth (nhanh) ---
            binding.tvEmail.text = user.email

            // Logic hiển thị Guest/User
            if (user.isAnonymous) {
                binding.tvUsername.text = "Guest Gardener"
                binding.ivUserAvatar.setImageResource(com.example.leafyapp.R.drawable.ic_user_solid_full)
                binding.btnEditProfile.visibility = View.GONE
                binding.btnLogout.visibility = View.GONE
                binding.btnDeleteAccount.visibility = View.GONE
                binding.cardSignUpBanner.visibility = View.VISIBLE
                return // Nếu là Guest thì dừng, không cần load Firestore
            } else {
                binding.tvUsername.text = user.displayName ?: "Gardener"
                binding.btnEditProfile.visibility = View.VISIBLE
                binding.btnLogout.visibility = View.VISIBLE
                binding.btnDeleteAccount.visibility = View.VISIBLE
                binding.cardSignUpBanner.visibility = View.GONE

                // --- THÊM LOG TẠI ĐÂY ---
                android.util.Log.d("CHECK_AVATAR", "2. Link ảnh từ Firebase Auth: ${user.photoUrl}")

                val authPhotoUrl = user.photoUrl
                if (authPhotoUrl != null) {
                    Glide.with(this)
                        .load(authPhotoUrl)
                        .placeholder(com.example.leafyapp.R.drawable.ic_user_solid_full)
                        .circleCrop()
                        .into(binding.ivUserAvatar)
                }
            }

            // --- GIAI ĐOẠN 2: Lấy dữ liệu chuẩn từ Firestore (Chậm hơn chút nhưng chính xác) ---
            authRepository.getUserDetailsFromFirestore { userData ->
                // 1. Cập nhật Tên (Lấy từ Firestore: displayName hoặc nối First+Last)
                val firestoreName = userData["displayName"] as? String
                if (!firestoreName.isNullOrEmpty()) {
                    binding.tvUsername.text = firestoreName
                }

                // 2. Cập nhật Avatar (Nếu Firestore có ảnh xịn hơn thì load đè lên)
                val firestorePhoto = userData["photoUrl"] as? String
                if (!firestorePhoto.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(firestorePhoto)
                        .placeholder(com.example.leafyapp.R.drawable.ic_user_solid_full)
                        .circleCrop()
                        .into(binding.ivUserAvatar)
                }
            }
        }
    }

    private fun setupClicks() {
        binding.rowLocation.setOnClickListener { openAppSettings() }

        binding.rowNotifications.setOnClickListener {
            openNotificationSettings()
        }
//
//        binding.btnCopyUserid.setOnClickListener {
//            copyToClipboard(binding.tvUserId.text.toString())
//        }

        // THÊM SỰ KIỆN EDIT PROFILE
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
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

        // 1. Click Terms of Use -> Mở Activity mới
        binding.rowTerm.setOnClickListener {
            val intent = Intent(requireContext(), TextContentActivity::class.java)
            intent.putExtra("EXTRA_TITLE", "Terms of Use")
            intent.putExtra("EXTRA_CONTENT", """
                1. CHẤP THUẬN ĐIỀU KHOẢN
                Chào mừng bạn đến với LeafyApp. Bằng việc truy cập hoặc sử dụng ứng dụng của chúng tôi, bạn đồng ý tuân thủ và bị ràng buộc bởi các Điều khoản và Điều kiện này.

                2. QUYỀN SỞ HỮU TRÍ TUỆ
                Toàn bộ nội dung, tính năng và chức năng (bao gồm nhưng không giới hạn ở thông tin, phần mềm, văn bản, hình ảnh hiển thị, video và âm thanh) là sở hữu của LeafyApp.

                3. TÀI KHOẢN NGƯỜI DÙNG
                Khi bạn tạo tài khoản với chúng tôi, bạn phải cung cấp thông tin chính xác, đầy đủ và cập nhật. Việc không làm như vậy cấu thành vi phạm Điều khoản, có thể dẫn đến việc chấm dứt ngay lập tức tài khoản của bạn.

                4. GIỚI HẠN TRÁCH NHIỆM
                Trong mọi trường hợp, LeafyApp sẽ không chịu trách nhiệm pháp lý đối với bất kỳ thiệt hại gián tiếp, ngẫu nhiên hoặc trừng phạt nào phát sinh từ việc bạn sử dụng dịch vụ.

                5. THAY ĐỔI DỊCH VỤ
                Chúng tôi bảo lưu quyền rút lại hoặc sửa đổi Dịch vụ của mình theo quyết định riêng của chúng tôi mà không cần thông báo trước.
            """.trimIndent())
            startActivity(intent)
        }

        // 2. Click Privacy Policy -> Mở Activity mới
        binding.rowPrivacy.setOnClickListener {
            val intent = Intent(requireContext(), TextContentActivity::class.java)
            intent.putExtra("EXTRA_TITLE", "Privacy Policy")
            intent.putExtra("EXTRA_CONTENT", """
                1. THU THẬP THÔNG TIN
                Chúng tôi thu thập thông tin bạn cung cấp trực tiếp cho chúng tôi khi bạn tạo tài khoản, cập nhật hồ sơ, hoặc sử dụng tính năng nhận diện cây. Các loại thông tin bao gồm: Email, Tên hiển thị, và Hình ảnh cây trồng.

                2. CÁCH CHÚNG TÔI SỬ DỤNG THÔNG TIN
                Chúng tôi sử dụng thông tin thu thập được để:
                - Cung cấp, duy trì và cải thiện dịch vụ.
                - Gửi thông báo kỹ thuật, cập nhật bảo mật và tin nhắn hỗ trợ.
                - Đồng bộ hóa dữ liệu vườn cây của bạn trên nhiều thiết bị.

                3. CHIA SẺ THÔNG TIN
                Chúng tôi không chia sẻ thông tin cá nhân của bạn với bên thứ ba trừ khi có sự đồng ý của bạn hoặc để tuân thủ pháp luật.

                4. BẢO MẬT DỮ LIỆU
                Chúng tôi thực hiện các biện pháp hợp lý để giúp bảo vệ thông tin về bạn khỏi bị mất, trộm cắp, lạm dụng và truy cập trái phép.

                5. QUYỀN CỦA BẠN
                Bạn có thể xem lại, sửa đổi hoặc xóa thông tin cá nhân của mình bất cứ lúc nào bằng cách đăng nhập vào tài khoản và truy cập trang Cài đặt.
            """.trimIndent())
            startActivity(intent)
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

    // Hàm hiển thị Dialog sửa tên
    private fun showEditProfileDialog() {
        val user = authRepository.getCurrentUser() ?: return

        // Tách First/Last name từ DisplayName hiện tại
        val fullName = user.displayName ?: ""
        val parts = fullName.split(" ")
        val currentFirst = if (parts.isNotEmpty()) parts.first() else ""
        val currentLast = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

        // Tạo layout cho dialog
        val dialogView = layoutInflater.inflate(com.example.leafyapp.R.layout.dialog_edit_profile, null) // Cần tạo layout này ở bước dưới
        val etFirstName = dialogView.findViewById<android.widget.EditText>(com.example.leafyapp.R.id.et_firstname)
        val etLastName = dialogView.findViewById<android.widget.EditText>(com.example.leafyapp.R.id.et_lastname)

        // Fill dữ liệu cũ
        etFirstName.setText(currentFirst)
        etLastName.setText(currentLast)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newFirst = etFirstName.text.toString().trim()
                val newLast = etLastName.text.toString().trim()

                if (newFirst.isEmpty()) {
                    Toast.makeText(context, "First name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Gọi Repo lưu lên Firebase
                authRepository.updateUserProfile(newFirst, newLast,
                    onSuccess = {
                        Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                        updateUI() // Refresh lại giao diện Profile

                        // Home sẽ tự cập nhật khi reload lại App hoặc chuyển tab
                    },
                    onError = {
                        Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        // Switch to Sign Up
        view.findViewById<View>(com.example.leafyapp.R.id.tv_switch_signup).setOnClickListener {
            dialog.dismiss()
            showSignUpBottomSheet()
        }

        // --- NEW: Handle Google Login inside Email Sheet ---
        view.findViewById<View>(com.example.leafyapp.R.id.btn_google_login_email_sheet)?.setOnClickListener {
            dialog.dismiss() // Close the email sheet
            performGoogleLogin() // Trigger the Google Login logic
        }
        // --------------------------------------------------

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "Vui lòng điền Email và Mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call Repository
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
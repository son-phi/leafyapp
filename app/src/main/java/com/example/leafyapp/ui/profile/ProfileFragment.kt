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
                        updateUI() // Refresh lại giao diện
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

        binding.btnCopyUserid.setOnClickListener {
            copyToClipboard(binding.tvUserId.text.toString())
        }

        binding.cardSignUpBanner.setOnClickListener {
            showSignUpBottomSheet()
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

        // Xử lý click trong BottomSheet
        view.findViewById<View>(com.example.leafyapp.R.id.btn_close_login).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(com.example.leafyapp.R.id.btn_login_google).setOnClickListener {
            dialog.dismiss()
            // Gọi hàm đăng nhập Google của bạn ở đây
             performGoogleLogin()
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
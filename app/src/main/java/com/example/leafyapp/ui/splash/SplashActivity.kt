package com.example.leafyapp.ui.splash

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.MainActivity
import com.example.leafyapp.databinding.ActivitySplashBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    // Hai biến cờ để kiểm soát tiến trình
    private var isAuthReady = false
    private var isAnimReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ẩn thanh trạng thái (Full màn hình)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 1. Bắt đầu chạy Animation
        startAnimation()

        // 2. Bắt đầu kiểm tra Đăng nhập song song
        checkUserLogin()
    }

    private fun startAnimation() {
        // 1. Bắt đầu chạy Lottie (Cây nở ra)
        binding.lottieLogo.playAnimation()

        // 2. Cùng lúc đó, ẢNH LOGO từ từ hiện lên (Fade In + Slide Up)
        binding.ivLogoText.animate()
            .alpha(1f)              // Hiện rõ (từ 0 -> 1)
            .translationY(0f)       // Trôi về vị trí gốc (từ 50 -> 0)
            .setDuration(1500)      // Hiệu ứng diễn ra trong 1.5 giây
            .setStartDelay(300)     // Chờ 0.3s sau khi cây bắt đầu nở mới hiện chữ
            .start()

        // 3. Lắng nghe khi Lottie chạy xong hết thì chuyển màn
        binding.lottieLogo.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Đợi thêm 0.5s cho người dùng ngắm logo hoàn chỉnh
                Handler(Looper.getMainLooper()).postDelayed({
                    goToMain()
                }, 500)
            }
        })
    }

    private fun checkUserLogin() {
        val auth = Firebase.auth
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Đã đăng nhập -> Đánh dấu Auth đã xong
            isAuthReady = true
            checkNavigation() // Kiểm tra xem có chuyển màn hình được chưa
        } else {
            // Chưa đăng nhập -> Đăng nhập ẩn danh
            auth.signInAnonymously()
                .addOnCompleteListener {
                    // Dù thành công hay thất bại cũng cho qua (hoặc xử lý lỗi nếu muốn)
                    isAuthReady = true
                    checkNavigation()
                }
        }
    }

    // Hàm quyết định chuyển màn hình
    // Chỉ chạy khi CẢ Animation VÀ Auth đều đã xong
    private fun checkNavigation() {
        if (isAuthReady && isAnimReady) {
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        // Hiệu ứng chuyển màn hình mờ dần
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
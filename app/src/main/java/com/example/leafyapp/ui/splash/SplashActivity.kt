package com.example.leafyapp.ui.splash

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.MainActivity
import com.example.leafyapp.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ẩn thanh trạng thái (Full màn hình)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        startAnimation()
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

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        // Hiệu ứng chuyển màn hình mờ dần
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
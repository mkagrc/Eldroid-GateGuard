package com.example.eldroid

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleAlreadyHaveText()

        binding.btnGetStarted.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.tvAlreadyHave.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun styleAlreadyHaveText() {
        val fullText  = getString(R.string.splash_already_have)
        val spannable = SpannableString(fullText)
        val linkText  = getString(R.string.splash_log_in_span)
        val start     = fullText.indexOf(linkText)
        if (start >= 0) {
            val end = start + linkText.length
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvAlreadyHave.text = spannable
    }
}

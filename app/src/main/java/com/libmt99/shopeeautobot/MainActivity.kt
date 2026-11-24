package com.libmt99.shopeeautobot

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }

        val tvTitle = TextView(this).apply {
            text = "Shopee Auto Bot"
            textSize = 22f
            setPadding(0, 0, 0, 20)
        }

        val tvStatus = TextView(this).apply {
            text = "1. Bật Accessibility cho Shopee Auto Bot\n2. Vào Shopee giỏ hàng/thanh toán để tự chọn voucher giảm cao nhất\n3. Có dừng an toàn trước nút Đặt hàng (có thể đổi trong code)"
            textSize = 15f
            setPadding(0, 0, 0, 36)
        }

        val btnSettings = Button(this).apply {
            text = "Mở Cài đặt Accessibility"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        rootLayout.addView(tvTitle)
        rootLayout.addView(tvStatus)
        rootLayout.addView(btnSettings)
        setContentView(rootLayout)
    }
}

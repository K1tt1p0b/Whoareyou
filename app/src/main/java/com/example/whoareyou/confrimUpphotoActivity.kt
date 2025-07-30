package com.kittipob.whoareyou

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class confrimUpphotoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confrim_upphoto)

        val sharedPreferences: SharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val allowButton: Button = findViewById(R.id.allow_button)
        val denyButton: Button = findViewById(R.id.deny_button)

        allowButton.setOnClickListener {
            // ✅ บันทึกว่าให้ Consent แล้ว
            editor.putBoolean("isConsentGiven", true)
            editor.apply()

            // ✅ ไปที่ `activity_addphoto`
            startActivity(Intent(this, AddphotoActivity::class.java))
            finish()
        }

        denyButton.setOnClickListener {
            // ❌ ถ้าปฏิเสธ → กลับไปที่หน้า Login
            editor.putBoolean("isConsentGiven", false)
            editor.apply()

            startActivity(Intent(this, homeActivity::class.java))
            finish()
        }
    }
}

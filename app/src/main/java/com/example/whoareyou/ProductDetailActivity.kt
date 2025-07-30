package com.example.project101

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import android.widget.Toast
import com.kittipob.whoareyou.R
import com.kittipob.whoareyou.homeActivity

class ProductDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)

        // 1. รับ ID ของสินค้าที่ถูกส่งมาจากหน้าแรก
        val productId = intent.getIntExtra("PRODUCT_ID", -1)

        // 2. ตรวจสอบว่าได้รับ ID มาจริงหรือไม่
        if (productId == -1) {
            // ถ้าไม่ได้รับ ID ให้ปิดหน้านี้ไป
            Toast.makeText(this, "Error: ไม่พบข้อมูลสินค้า", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 3. (ตัวอย่าง) แสดง ID ที่ได้รับมาบนหน้าจอ
        // ในไฟล์ activity_product_detail.xml คุณอาจจะต้องมี TextView ที่มี id เป็น detail_text_view
        // val detailTextView: TextView = findViewById(R.id.detail_text_view)
        // detailTextView.text = "ข้อมูลของสินค้า ID: $productId"

        // จากตรงนี้ คุณสามารถใช้ productId ไปดึงข้อมูลสินค้าชิ้นนั้นๆ มาแสดงผลต่อไปได้
        val backButton: ImageButton = findViewById(R.id.backButton)

        // 2. ตั้งค่าให้ปุ่มทำงานเมื่อถูกกด
        backButton.setOnClickListener {
            // 3. สั่งให้ย้อนกลับไปหน้าก่อนหน้า
            onBackPressedDispatcher.onBackPressed()
        }

        val loginButton: Button = findViewById(R.id.loginButton)
        loginButton.setOnClickListener {
            // 3. สร้าง Intent เพื่อระบุว่าจะเปิดหน้า LoginActivity
            val intent = Intent(this, homeActivity::class.java)

            // 4. สั่งให้เปิดหน้าใหม่
            startActivity(intent)
        }
    }
}
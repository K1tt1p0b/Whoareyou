package com.example.project101

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.Html
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import com.kittipob.whoareyou.R
import com.kittipob.whoareyou.homeActivity

class PrizeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prize)

        val skinColorTextView: TextView = findViewById(R.id.skinColorTextView)
        val budgetSpinner: Spinner = findViewById(R.id.budgetSpinner)
        val styleSpinner: Spinner = findViewById(R.id.styleSpinner)
        val nextButton: Button = findViewById(R.id.nextButton)
        val backButton: Button = findViewById(R.id.backButton)
        val loginButton: Button = findViewById(R.id.loginButton)

        // ตั้งค่าข้อความ "ผิวเหลือง" ให้มีขีดเส้นใต้ (ถ้าต้องการ)
        // สำหรับการขีดเส้นใต้ ถ้าคุณอยากให้เป็นเส้นใต้จริงๆ ไม่ใช่แค่ขีดฆ่า
        // คุณอาจจะต้องจัดการด้วย SpannableString หรือใช้วิธีอื่น
        // วิธีที่ง่ายที่สุดคือใช้ HTML:
        skinColorTextView.text = Html.fromHtml("<u>ผิวเหลือง</u>", Html.FROM_HTML_MODE_COMPACT)

        // ตั้งค่า Spinner งบประมาณ
        ArrayAdapter.createFromResource(
            this,
            R.array.budget_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            budgetSpinner.adapter = adapter
        }

        budgetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedBudget = parent.getItemAtPosition(position).toString()
                // ทำอะไรบางอย่างกับค่าที่เลือก เช่น แสดงใน Log
                println("Selected budget: $selectedBudget")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // ตั้งค่า Spinner สไตล์การแต่งหน้า
        ArrayAdapter.createFromResource(
            this,
            R.array.style_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            styleSpinner.adapter = adapter
        }

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedStyle = parent.getItemAtPosition(position).toString()
                println("Selected style: $selectedStyle")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // ตั้งค่า Listener สำหรับปุ่ม
        nextButton.setOnClickListener {
            // 1. ดึงค่าที่ผู้ใช้เลือกจาก Spinner ทั้งสอง
            val selectedBudget = budgetSpinner.selectedItem.toString()
            val selectedStyle = styleSpinner.selectedItem.toString()

            // 2. สร้าง Intent เพื่อเตรียมเปิดหน้า ProductActivity
            val intent = Intent(this, ProductActivity::class.java)

            // 3. แนบข้อมูลที่เลือกไปกับ Intent ด้วย putExtra
            //    "EXTRA_BUDGET" และ "EXTRA_STYLE" คือ "กุญแจ" สำหรับให้หน้าต่อไปใช้ดึงข้อมูล
            intent.putExtra("EXTRA_BUDGET", selectedBudget)
            intent.putExtra("EXTRA_STYLE", selectedStyle)

            // 4. สั่งเปิดหน้าใหม่
            startActivity(intent)
        }

        backButton.setOnClickListener {
            // โค้ดที่จะทำงานเมื่อกดปุ่ม "ย้อนกลับ"
            // เช่น กลับไปหน้าจอก่อนหน้า: onBackPressed()
        }

        loginButton.setOnClickListener {
            // 3. สร้าง Intent เพื่อระบุว่าจะเปิดหน้า LoginActivity
            val intent = Intent(this, homeActivity::class.java)

            // 4. สั่งให้เปิดหน้าใหม่
            startActivity(intent)
        }
    }
}
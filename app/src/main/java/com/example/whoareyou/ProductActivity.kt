package com.example.project101

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kittipob.whoareyou.R
import com.kittipob.whoareyou.homeActivity

class ProductActivity : AppCompatActivity() {

    private lateinit var productAdapter: ProductAdapter
    private lateinit var budgetSpinner: AppCompatSpinner
    private lateinit var styleSpinner: AppCompatSpinner

    private val allProducts = listOf(
        Product(1, "SKINTIFIC Cushion", "...", 150.0, "url_to_image_1", "เกาหลี"),
        Product(2, "Maybelline Superstay", "...", 250.0, "url_to_image_2", "ญี่ปุ่น"),
        Product(3, "Mille Snail Bright Primer", "...", 140.0, "url_to_image_3", "ฝรั่ง"),
        Product(4, "Etude Drawing Eyebrow", "...", 107.0, "url_to_image_4", "ธรรมชาติ"),
        Product(5, "SKINTIFIC Cushion", "...", 1500.0, "url_to_image_1", "เกาหลี"),
        Product(6, "Maybelline Superstay", "...", 2000.0, "url_to_image_2", "ญี่ปุ่น"),
        Product(7, "Mille Snail Bright Primer", "...", 3000.0, "url_to_image_3", "ฝรั่ง"),
        Product(8, "Etude Drawing Eyebrow", "...", 600.0, "url_to_image_4", "ธรรมชาติ")

        // ...
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        // ค้นหา View
        budgetSpinner = findViewById(R.id.budget_spinner)
        styleSpinner = findViewById(R.id.style_spinner)

        // ตั้งค่าการทำงานต่างๆ
        setupClickListeners()
        setupRecyclerView()
        setupSpinnersAndInitialFilter() // <-- แก้ไข: รวมการตั้งค่าและการกรองครั้งแรกไว้ด้วยกัน
    }

    private fun setupClickListeners() {
        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val loginButton: Button = findViewById(R.id.loginButton)
        loginButton.setOnClickListener { startActivity(Intent(this, homeActivity::class.java)) }
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.product_recycler_view)
        // ตอนเริ่มต้น ให้สร้าง Adapter ด้วยลิสต์ว่างๆ ก่อน เพื่อไม่ให้เห็นสินค้าทั้งหมดแวบหนึ่ง
        productAdapter = ProductAdapter(emptyList()) { clickedProduct ->
            val intent = Intent(this, ProductDetailActivity::class.java)
            intent.putExtra("PRODUCT_ID", clickedProduct.id)
            startActivity(intent)
        }
        recyclerView.adapter = productAdapter
        recyclerView.layoutManager = GridLayoutManager(this, 2)
    }

    private fun setupSpinnersAndInitialFilter() {
        // --- 1. ตั้งค่าตัวเลือกใน Spinner ---
        val budgets = arrayOf("ทุกงบประมาณ", "0 - 100 บาท", "100 - 500 บาท", "501 - 1000 บาท", "1001 - 2000 บาท", "2000 บาทขึ้นไป")
        val budgetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, budgets)
        budgetSpinner.adapter = budgetAdapter

        val styles = arrayOf("ทุกสไตล์", "เกาหลี", "ญี่ปุ่น", "ฝรั่ง", "ธรรมชาติ")
        val styleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, styles)
        styleSpinner.adapter = styleAdapter

        // --- 2. รับค่าจาก Intent และตั้งค่าเริ่มต้นให้ Spinner ---
        val initialBudget = intent.getStringExtra("EXTRA_BUDGET") ?: "ทุกงบประมาณ"
        val initialStyle = intent.getStringExtra("EXTRA_STYLE") ?: "ทุกสไตล์"

        // หาตำแหน่ง (index) ของค่าที่รับมาใน Array ของ Spinner
        val budgetPosition = budgets.indexOf(initialBudget)
        val stylePosition = styles.indexOf(initialStyle)

        // ตั้งค่า Spinner ให้แสดงผลตามตำแหน่งที่เจอ (ถ้าเจอ)
        if (budgetPosition >= 0) {
            budgetSpinner.setSelection(budgetPosition, false) // false คือไม่ให้ trigger listener ตอนตั้งค่า
        }
        if (stylePosition >= 0) {
            styleSpinner.setSelection(stylePosition, false)
        }

        // --- 3. กรองข้อมูลครั้งแรกหลังจากตั้งค่า Spinner เสร็จ ---
        filterProducts()

        // --- 4. ตั้งค่า Listener เพื่อให้กรองข้อมูลใหม่เมื่อผู้ใช้เลือกเอง ---
        val filterListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterProducts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        budgetSpinner.onItemSelectedListener = filterListener
        styleSpinner.onItemSelectedListener = filterListener
    }

    private fun filterProducts() {
        val selectedBudget = budgetSpinner.selectedItem.toString()
        val selectedStyle = styleSpinner.selectedItem.toString()

        var filteredList = allProducts

        // กรองตามงบประมาณ
        when (selectedBudget) {
            "0 - 100 บาท" -> {
                filteredList = filteredList.filter { it.price <= 100 }
            }
            "100 - 500 บาท" -> {
                filteredList = filteredList.filter { it.price > 100 && it.price <= 500 }
            }
            "501 - 1000 บาท" -> {
                filteredList = filteredList.filter { it.price > 501 && it.price <= 1000 }
            }
            "1001 - 2000 บาท" -> {
                filteredList = filteredList.filter { it.price > 1001 && it.price <= 2000 }
            }
            "มากกว่า 2000 บาท" -> {
                filteredList = filteredList.filter { it.price > 2000 }
            }
        }

        // กรองตามสไตล์
        if (selectedStyle != "ทุกสไตล์") {
            filteredList = filteredList.filter { it.category.equals(selectedStyle, ignoreCase = true) }
        }

        // อัปเดตข้อมูลใน Adapter
        productAdapter.updateData(filteredList)
    }
}
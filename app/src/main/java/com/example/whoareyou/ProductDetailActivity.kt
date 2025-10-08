package com.kittipob.whoareyou

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var BASE_URL: String

    private lateinit var topBar: MaterialToolbar
    private lateinit var progressBar: CircularProgressIndicator

    private lateinit var ivProduct: ImageView
    private lateinit var tvPrice: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvRating: TextView
    private lateinit var btnShopee: MaterialButton
    private lateinit var btnLazada: MaterialButton

    private var fallbackUrl: String? = null

    private val authClient: OkHttpClient by lazy {
        val auth = Interceptor { chain ->
            val prefs = getSharedPreferences("UserSession", MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)
            val req = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(req)
        }
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)

        BASE_URL = getString(R.string.root_url).trim().removeSuffix("/")

        topBar     = findViewById(R.id.topBar)
        progressBar= findViewById(R.id.progressBar)
        ivProduct  = findViewById(R.id.productImageView)
        tvPrice    = findViewById(R.id.productPriceTextView)
        tvTitle    = findViewById(R.id.productTitleTextView)
        tvMeta     = findViewById(R.id.productMetaTextView)
        tvDesc     = findViewById(R.id.productDescriptionTextView)
        tvRating   = findViewById(R.id.productRatingTextView)
        btnShopee  = findViewById(R.id.shopeeButton)
        btnLazada  = findViewById(R.id.lazadaButton)

        topBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val productId = intent.getIntExtra("PRODUCT_ID", -1)
        fallbackUrl   = intent.getStringExtra("FALLBACK_URL")

        if (productId == -1) {
            Toast.makeText(this, "ไม่พบรหัสสินค้า", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        btnShopee.setOnClickListener { openUrlSafe(fallbackUrl) }
        btnLazada.setOnClickListener { openUrlSafe(fallbackUrl) }

        fetchDetail(productId)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openUrlSafe(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "ยังไม่มีลิงก์ร้านสำหรับสินค้านี้", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "เปิดลิงก์ไม่สำเร็จ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchDetail(id: Int) {
        showLoading(true)
        val url = "$BASE_URL/ai/cosmetics/$id"
        val req = Request.Builder().url(url).get().build()

        authClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@ProductDetailActivity, "โหลดรายละเอียดไม่สำเร็จ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                runOnUiThread {
                    showLoading(false)
                    if (!response.isSuccessful) {
                        Toast.makeText(this@ProductDetailActivity, "API ผิดพลาด: ${response.code}", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    try {
                        val obj = JSONObject(bodyStr)
                        val item = obj.getJSONObject("item")

                        val brand = item.optString("brandName")
                        val name  = item.optString("Name")
                        val type  = item.optString("Type", "")
                        val shade = item.optString("Shade", "")
                        val price = item.optDouble("Price", Double.NaN)
                        val img   = item.optString("ImageURL", null)
                        val prodLink = item.optString("ProductLink", null)
                        val desc  = item.optString("Description",
                            listOfNotNull(type.ifBlank { null }, shade.ifBlank { null }).joinToString(" • ")
                        )

                        // Title
                        tvTitle.text = if (brand.isNotBlank()) "$brand\n$name" else name

                        // Meta
                        tvMeta.text = buildString {
                            if (type.isNotBlank()) append("ประเภท: $type")
                            if (shade.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("เฉด: $shade")
                            }
                        }.ifBlank { "-" }

                        // Price
                        tvPrice.text = if (!price.isNaN())
                            NumberFormat.getNumberInstance(Locale("th","TH")).format(price) + " บาท"
                        else "-"

                        tvDesc.text = desc.ifBlank { "-" }
                        tvRating.text = "-" // ไม่มีเรตติ้งจากแบ็กเอนด์แล้ว

                        Glide.with(this@ProductDetailActivity)
                            .load(img ?: R.drawable.logo)
                            .placeholder(R.drawable.logo)
                            .error(R.drawable.logo)
                            .into(ivProduct)

                        // ใช้ลิงก์ ProductLink เป็นหลัก
                        if (!prodLink.isNullOrBlank()) {
                            btnShopee.visibility = View.VISIBLE
                            btnLazada.visibility = View.VISIBLE
                            fallbackUrl = prodLink
                        } else {
                            // หากไม่มีลิงก์เลย ซ่อนปุ่ม
                            btnShopee.visibility = if (fallbackUrl.isNullOrBlank()) View.GONE else View.VISIBLE
                            btnLazada.visibility = if (fallbackUrl.isNullOrBlank()) View.GONE else View.VISIBLE
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this@ProductDetailActivity, "แปลงข้อมูลไม่ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}

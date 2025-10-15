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
import kotlin.math.roundToInt
import com.kittipob.whoareyou.net.ApiConfig


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
        tvRating   = findViewById(R.id.productRatingTextView) // ใช้แสดง “ระดับความเหมาะสม”
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
                        val typeFromApi  = item.optString("Type", "")
                        val shade = item.optString("Shade", "")
                        val price = item.optDouble("Price", Double.NaN)
                        val img   = item.optString("ImageURL", null)
                        val imgUrl = ApiConfig.fullUrl(img)
                        val prodLink = item.optString("ProductLink", null)
                        val descApi  = item.optString(
                            "Description",
                            listOfNotNull(typeFromApi.ifBlank { null }, shade.ifBlank { null }).joinToString(" • ")
                        )

                        // ---------- เติม “ระดับความเหมาะสม” จาก extras (ที่ส่งมาจากลิสต์) ----------
                        val typeExtra   = intent.getStringExtra("EXTRA_TYPE")
                        val deltaExtra  = intent.getDoubleExtra("EXTRA_DELTAE00", Double.NaN)
                        val confExtra   = intent.getIntExtra("EXTRA_CONF", -1)
                        val levelExtra  = intent.getStringExtra("EXTRA_CONF_LEVEL")
                        val reasonExtra = intent.getStringExtra("EXTRA_REASON")

                        val typeForSuit = (typeExtra ?: typeFromApi)
                        val suitability = if (!deltaExtra.isNaN())
                            mapSuitability(typeForSuit, deltaExtra)
                        else null

                        // Title
                        tvTitle.text = if (brand.isNotBlank()) "$brand\n$name" else name

                        // Meta
                        tvMeta.text = buildString {
                            if (typeFromApi.isNotBlank()) append("ประเภท: $typeFromApi")
                            if (shade.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("เฉด: $shade")
                            }
                        }.ifBlank { "-" }

                        // Price
                        tvPrice.text = if (!price.isNaN())
                            NumberFormat.getNumberInstance(Locale("th","TH")).format(price) + " บาท"
                        else "-"

                        // Description
                        tvDesc.text = buildString {
                            append(descApi.ifBlank { "-" })
                            // ต่อท้ายเหตุผลถ้ามี
                            val extraReason = suitability?.second?.takeIf { it.isNotBlank() } ?: reasonExtra
                            if (!extraReason.isNullOrBlank()) {
                                append("\n• "); append(extraReason)
                            }
                        }

                        // แทนค่า “rating” เดิมด้วย “ระดับความเหมาะสม”
                        tvRating.text = buildString {
                            append("ระดับความเหมาะสม: ")
                            if (suitability != null) {
                                append(suitability.first)               // เช่น เหมาะมาก / คอนทราสต์สวย
                            } else {
                                append("-")
                            }
                            // โชว์ความมั่นใจรวมจากลิสต์ ถ้ามี
                            if (confExtra >= 0 && !levelExtra.isNullOrBlank()) {
                                append("  •  ")
                                append("ความมั่นใจ ~ ${confExtra.coerceIn(0,100)}% ($levelExtra)")
                            }
                        }

                        Glide.with(this@ProductDetailActivity)
                            .load(ApiConfig.fullUrl(img) ?: R.drawable.logo)  // ใช้ fullUrl ตรงนี้!
                            .placeholder(R.drawable.logo)
                            .error(R.drawable.logo)
                            .into(ivProduct)

                        // ลิงก์ร้าน
                        if (!prodLink.isNullOrBlank()) {
                            btnShopee.visibility = View.VISIBLE
                            btnLazada.visibility = View.VISIBLE
                            fallbackUrl = prodLink
                        } else {
                            val show = !fallbackUrl.isNullOrBlank()
                            btnShopee.visibility = if (show) View.VISIBLE else View.GONE
                            btnLazada.visibility = if (show) View.VISIBLE else View.GONE
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this@ProductDetailActivity, "แปลงข้อมูลไม่ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // ---------- แปลง ΔE00 เป็นคำอธิบายเข้าใจง่าย ----------
    private fun mapSuitability(productType: String?, deltaE00: Double?): Pair<String, String> {
        if (deltaE00 == null) return "ไม่พบข้อมูลสี" to "สินค้ายังไม่มีค่าสีสำหรับประเมิน"
        val d = deltaE00
        val t = (productType ?: "").lowercase()

        val isComplexion = listOf("foundation","concealer","bb","cc","tinted","powder","cushion","contour","bronzer","base").any { t.contains(it) }
        val isLipOrBlush = listOf("lip","lipstick","gloss","oil","tint","stain","kit","liner","blush","cheek").any { t.contains(it) }
        val isEye        = listOf("eyeshadow","eye shadow","eyeliner","mascara").any { t.contains(it) }
        val isBrow       = listOf("brow","eyebrow").any { t.contains(it) }



        return when {
            isComplexion -> when {
                d <= 2  -> "เหมาะมาก"   to "สีผิวแทบตรงกัน"
                d <= 4  -> "ใกล้เคียง"  to "เฉดใกล้ผิว แนะนำลองที่แนวกราม"
                d <= 6  -> "พอใช้"     to "อาจต้องบาลานซ์ด้วยไฮไลต์/คอนซีลเลอร์"
                else    -> "ต่างจากผิว" to "มีโอกาสเพี้ยนเมื่อทาทั่วหน้า"
            }
            isLipOrBlush -> when {
                d in 15.0..25.0 -> "คอนทราสต์สวย" to "ช่วยให้ใบหน้าดูมีชีวิตชีวา"
                d in 25.0..40.0 -> "เด่นชัด"     to "สีจัดขึ้น เหมาะกับลุคชัด"
                else            -> "โทนสุภาพ"    to "คอนทราสต์ไม่แรง ใช้ได้ทุกวัน"
            }
            isEye -> when {
                d in 20.0..45.0 -> "ดวงตาเด่น"   to "คอนทราสต์กำลังดี ช่วยขับตา"
                d in 45.0..60.0 -> "ลุคจัดชัด"   to "โทนชัด เหมาะกับแต่งเต็ม"
                else            -> "โทนอ่อน"     to "สุภาพ/ธรรมชาติ"
            }
            isBrow -> when {
                d <= 5   -> "กลืนผิว"   to "โทนคิ้วใกล้ธรรมชาติ"
                d <= 12  -> "ใกล้เคียง" to "อาจต้องปรับความเข้มเล็กน้อย"
                d <= 20  -> "พอใช้"     to "ควรเบลนด์เพื่อให้เนียน"
                else     -> "ต่างโทน"   to "อาจเข้มหรืออ่อนเกินไป"
            }
            else -> if (d <= 10) "ค่อนข้างใกล้" to "สีใกล้โทนผิว"
            else          "ต่างปานกลาง"  to "เหมาะใช้สร้างเลเยอร์/ไฮไลต์"
        }
    }
}

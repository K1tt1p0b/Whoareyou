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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
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
    private lateinit var tvSuitReason: TextView     // ✅ เหตุผล/คำแนะนำ (บรรทัดแยก)
    private lateinit var labelDescription: TextView // ✅ หัวข้อ "รายละเอียด"
    private lateinit var labelSuitability: TextView // ✅ หัวข้อ "ความเหมาะสมกับผิว"
    private lateinit var btnShopee: MaterialButton
    private lateinit var btnBack: MaterialButton

    // meta chips
    private lateinit var chipGroupMeta: ChipGroup
    private lateinit var chipType: Chip
    private lateinit var chipShade: Chip

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

        topBar      = findViewById(R.id.topBar)
        progressBar = findViewById(R.id.progressBar)
        ivProduct   = findViewById(R.id.productImageView)
        tvPrice     = findViewById(R.id.productPriceTextView)
        tvTitle     = findViewById(R.id.productTitleTextView)
        tvMeta      = findViewById(R.id.productMetaTextView)
        tvDesc      = findViewById(R.id.productDescriptionTextView)
        tvRating    = findViewById(R.id.productRatingTextView)
        tvSuitReason     = findViewById(R.id.productSuitReasonTextView)
        labelDescription = findViewById(R.id.labelDescription)
        labelSuitability = findViewById(R.id.labelSuitability)
        btnShopee   = findViewById(R.id.shopeeButton)
        btnBack     = findViewById(R.id.backButton)

        chipGroupMeta = findViewById(R.id.chipGroupMeta)
        chipType      = findViewById(R.id.chipType)
        chipShade     = findViewById(R.id.chipShade)

        // appbar back
        topBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val productId = intent.getIntExtra("PRODUCT_ID", -1)
        fallbackUrl   = intent.getStringExtra("FALLBACK_URL")

        if (productId == -1) {
            toast("ไม่พบรหัสสินค้า")
            finish(); return
        }

        btnShopee.setOnClickListener { openUrlSafe(fallbackUrl) }
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        fetchDetail(productId)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openUrlSafe(url: String?) {
        if (url.isNullOrBlank()) {
            toast("ยังไม่มีลิงก์ร้านสำหรับสินค้านี้")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("เปิดลิงก์ไม่สำเร็จ")
        }
    }

    private fun fetchDetail(id: Int) {
        showLoading(true)
        val url = "$BASE_URL/ai/cosmetics/$id"
        val req = Request.Builder().url(url).get().build()

        authClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                showLoading(false)
                toast("โหลดรายละเอียดไม่สำเร็จ: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {

                val bodyStr = response.body?.string().orEmpty()
                runOnUiThread {
                    showLoading(false)
                    if (!response.isSuccessful) {
                        toast("API ผิดพลาด: ${response.code}")
                        return@runOnUiThread
                    }
                    try {
                        val obj = JSONObject(bodyStr)
                        val item = obj.getJSONObject("item")

                        val brand = item.optString("brandName")
                        val name  = item.optString("Name")

                        val typeFromApi  = item.optString("Type", "").trim()

                        // ใช้ ShadeName เป็นหลัก ถ้าไม่มีให้ใช้ ShadeCode
                        val shadeName = item.optString("ShadeName", "").trim()
                        val shadeCode = item.optString("ShadeCode", "").trim()
                        val displayShade = when {
                            shadeName.isNotEmpty() -> shadeName
                            shadeCode.isNotEmpty() -> shadeCode
                            else -> ""
                        }

                        val price = item.optDouble("Price", Double.NaN)
                        val img   = item.optString("ImageURL", null)
                        val imgUrl = ApiConfig.fullUrl(img)
                        val prodLink = item.optString("ProductLink", null)

                        // Description: ถ้า API ไม่มี ให้ประกอบจาก type + displayShade
                        val descApi  = item.optString(
                            "Description",
                            listOfNotNull(
                                typeFromApi.ifBlank { null },
                                displayShade.ifBlank { null }
                            ).joinToString(" • ")
                        )

                        // ---------- ความเหมาะสมจาก extras ----------
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
                        tvTitle.text = buildString {
                            if (brand.isNotBlank()) append(brand).append('\n')
                            append(name)
                        }

                        // Chips meta
                        applyMetaChips(typeFromApi, displayShade)

                        // Fallback meta text (ถ้ามีชิปแล้วซ่อนข้อความ)
                        tvMeta.text = metaText(typeFromApi, displayShade)
                        tvMeta.visibility = if (chipGroupMeta.visibility == View.VISIBLE) View.GONE else View.VISIBLE

                        // Price
                        tvPrice.text = if (!price.isNaN()) formatBaht(price) else "-"

                        // ====== แยกหัวข้อ ======

                        // รายละเอียด (ไม่ปนเหตุผลอีกแล้ว)
                        tvDesc.text = descApi.ifBlank { "-" }

                        // สรุประดับความเหมาะสม + ความมั่นใจ
                        tvRating.text = buildString {
                            append("ระดับความเหมาะสม: ")
                            append(suitability?.first ?: "-")
                            if (confExtra in 0..100 && !levelExtra.isNullOrBlank()) {
                                append("  •  ความมั่นใจ ~ ${confExtra}% ($levelExtra)")
                            }
                        }

                        // เหตุผล/คำแนะนำ (บรรทัดแยก)
                        val extraReason = (suitability?.second?.takeIf { it.isNotBlank() } ?: reasonExtra)?.trim()
                        if (!extraReason.isNullOrEmpty()) {
                            tvSuitReason.visibility = View.VISIBLE
                            tvSuitReason.text = "• $extraReason"
                        } else {
                            tvSuitReason.visibility = View.GONE
                        }

                        // ภาพ
                        Glide.with(this@ProductDetailActivity)
                            .load(imgUrl ?: R.drawable.logo)
                            .placeholder(R.drawable.logo)
                            .error(R.drawable.logo)
                            .into(ivProduct)

                        // ลิงก์ร้าน
                        fallbackUrl = prodLink ?: fallbackUrl
                        btnShopee.visibility = if (!fallbackUrl.isNullOrBlank()) View.VISIBLE else View.GONE

                        btnBack.visibility = View.VISIBLE

                    } catch (e: Exception) {
                        toast("แปลงข้อมูลไม่ได้: ${e.message}")
                    }
                }
            }
        })
    }

    // ---------- Helpers ----------
    private fun formatBaht(price: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("th","TH"))
        return fmt.format(price) + " บาท"
    }

    private fun metaText(type: String?, shade: String?): String {
        val t = (type ?: "").trim()
        val s = (shade ?: "").trim()
        return buildString {
            if (t.isNotEmpty()) append("ประเภท: ").append(t)
            if (s.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("เฉด: ").append(s)
            }
        }.ifBlank { "-" }
    }

    private fun applyMetaChips(type: String?, shade: String?) {
        val t = (type ?: "").trim()
        val s = (shade ?: "").trim()

        if (t.isNotEmpty()) {
            chipType.text = "ประเภท: $t"
            chipType.visibility = View.VISIBLE
        } else {
            chipType.visibility = View.GONE
        }

        if (s.isNotEmpty()) {
            chipShade.text = "เฉด: $s"
            chipShade.visibility = View.VISIBLE
        } else {
            chipShade.visibility = View.GONE
        }

        chipGroupMeta.visibility =
            if (chipType.visibility == View.GONE && chipShade.visibility == View.GONE)
                View.GONE else View.VISIBLE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- แปลง ΔE00 เป็นคำอธิบายเข้าใจง่าย ----------
    private fun mapSuitability(productType: String?, deltaE00: Double?): Pair<String, String> {
        if (deltaE00 == null)
            return "ยังไม่มีข้อมูลสี" to "สินค้านี้ยังไม่มีข้อมูลเพียงพอสำหรับเทียบสีผิว"

        val d = deltaE00
        val t = (productType ?: "").lowercase()

        val isComplexion = listOf("foundation","concealer","bb","cc","tinted","powder","cushion","contour","bronzer","base")
            .any { t.contains(it) }
        val isLipOrBlush = listOf("lip","lipstick","gloss","oil","tint","stain","kit","liner","blush","cheek")
            .any { t.contains(it) }
        val isEye = listOf("eyeshadow","eye shadow","eyeliner","mascara")
            .any { t.contains(it) }
        val isBrow = listOf("brow","eyebrow")
            .any { t.contains(it) }

        return when {
            isComplexion -> when {
                d <= 2  -> "ตรงกับผิวมาก" to "สีนี้ใกล้เคียงผิวจริงมาก ทาแล้วดูกลืนกับหน้า"
                d <= 4  -> "ใกล้สีผิว"    to "เฉดใกล้ผิว แนะนำลองทาบริเวณกรามเพื่อเช็กความพอดี"
                d <= 6  -> "พอใช้ได้"     to "สีอาจอ่อนหรือเข้มกว่าผิวเล็กน้อย ปรับได้ด้วยแป้งหรือคอนซีลเลอร์"
                else    -> "ไม่เข้ากับผิว" to "สีต่างจากผิวชัด อาจทำให้หน้าดูหมองหรือวอก"
            }
            isLipOrBlush -> when {
                d in 15.0..25.0 -> "เข้ากับผิว" to "ช่วยให้หน้าดูสดใส สุขภาพดี"
                d in 25.0..40.0 -> "ดูโดดเด่น"  to "สีจัดขึ้น เหมาะกับลุคแต่งหน้าเต็ม"
                else            -> "ดูสุภาพ"    to "สีอ่อนกำลังดี เหมาะกับลุคธรรมชาติ"
            }
            isEye -> when {
                d in 20.0..45.0 -> "ขับดวงตา"   to "สีช่วยให้ตาดูเด่นขึ้นอย่างพอดี"
                d in 45.0..60.0 -> "ดูจัดชัด"   to "สีเข้ม เหมาะกับลุคแต่งเต็มหรือออกงาน"
                else            -> "ดูเบา ๆ"    to "สีอ่อน เหมาะกับลุคธรรมชาติทุกวัน"
            }
            isBrow -> when {
                d <= 5   -> "ธรรมชาติ"    to "สีคิ้วกลืนกับผิว ดูเป็นธรรมชาติ"
                d <= 12  -> "ใกล้เคียง"  to "สีใกล้เคียงกับผิวและสีผม"
                d <= 20  -> "พอใช้ได้"    to "อาจต้องเกลี่ยเพิ่มให้เนียนกับผิว"
                else     -> "ต่างจากผิว"  to "สีคิ้วอาจเข้มหรืออ่อนเกินไป"
            }
            else -> when {
                d <= 10 -> "ใกล้เคียง" to "สีใกล้โทนผิว ดูกลมกลืนดี"
                else    -> "ดูตัดกันสวย" to "สีต่างจากผิวเล็กน้อย ดูเด่นขึ้น"
            }
        }
    }
}

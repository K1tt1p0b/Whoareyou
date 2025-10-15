package com.kittipob.whoareyou

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PrizeActivity : AppCompatActivity() {

    private lateinit var BASE_URL: String

    private lateinit var skinColorTextView: TextView
    private lateinit var previewPaletteButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var backButton: MaterialButton
    private lateinit var recommendedColorImageView: ImageView

    // ส่งต่อ / ใช้เรียกพาเล็ต
    private var brightnessForProducts: String? = null       // Fair/Medium/Brown/Deep
    private var brightnessForPalette: String? = null        // ใช้เรียกตารางสี
    private var paletteVisible = false

    // OkHttp + JWT
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

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_prize)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.prize_layout_root)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }

        BASE_URL = getString(R.string.root_url).trim().removeSuffix("/")

        skinColorTextView = findViewById(R.id.skinColorTextView)
        previewPaletteButton = findViewById(R.id.previewPaletteButton)
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)
        recommendedColorImageView = findViewById(R.id.recommendedColorImageView)

        // รับรูป → predict
        intent.getStringExtra("imageUri")?.let {
            uploadImageForSkinTonePrediction(Uri.parse(it))
        } ?: run {
            Toast.makeText(this, "Image URI is missing for skin tone prediction", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "ไม่สามารถทำนายสีผิวได้"
        }

        // ปุ่มสลับดู/ซ่อนตารางสี (โหลดจาก API ตามโทน)
        previewPaletteButton.setOnClickListener {
            val b = brightnessForPalette
            if (b.isNullOrBlank()) {
                Toast.makeText(this, "ยังไม่ได้ผลทำนายสีผิว", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            paletteVisible = !paletteVisible
            if (paletteVisible) {
                previewPaletteButton.text = "ซ่อนตารางสี"
                fetchColorPaletteByBrightness(b)
                recommendedColorImageView.visibility = View.VISIBLE
            } else {
                previewPaletteButton.text = "ดูตารางสี (ไม่บังคับ)"
                recommendedColorImageView.setImageResource(R.drawable.logo)
                recommendedColorImageView.visibility = View.VISIBLE
            }
        }

        // ไปหน้า Product
        nextButton.setOnClickListener {
            val b = brightnessForProducts
            if (b == null) {
                Toast.makeText(this, "กำลังรอผลทำนายสีผิว... กรุณาลองอีกครั้ง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ProductActivity::class.java).apply {
                putExtra("EXTRA_SKIN_TONE", b)
                putExtra("EXTRA_STYLE_NAME", "")
                putExtra("EXTRA_STYLE_ID", -1)
                putExtra("EXTRA_BUDGET", "")
            })
        }

        backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    /** ---------------- predict skin tone ---------------- */
    private fun uploadImageForSkinTonePrediction(imageUri: Uri) {
        val file = getFileFromUri(imageUri)
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found or unable to create file", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "ไม่สามารถประมวลผลรูปภาพได้"
            return
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val url = "$BASE_URL/ai/predict_skin_tone"
        val request = Request.Builder().url(url).post(body).build()

        authClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PrizeActivity, "Failed to predict skin tone: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    skinColorTextView.text = "เกิดข้อผิดพลาดในการเชื่อมต่อ"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                Log.d("PrizeActivity", "predict_skin_tone ${response.code}: ${bodyStr.take(500)}")
                runOnUiThread {
                    if (!response.isSuccessful) {
                        skinColorTextView.text = "API เกิดข้อผิดพลาด: ${response.code}"
                        return@runOnUiThread
                    }
                    try {
                        val json = JSONObject(bodyStr)

                        // รองรับหลาย schema จากแบ็กเอนด์
                        val brightnessEn = when {
                            json.has("final_brightness") -> json.getString("final_brightness")
                            json.has("brightness_class") -> json.getString("brightness_class")
                            json.has("brightness")       -> json.getString("brightness")
                            else -> ""
                        }
                        val brightnessTh = when {
                            json.has("brightness_label_th") -> json.getString("brightness_label_th")
                            json.has("brightness_label")    -> json.getString("brightness_label")
                            else -> ""
                        }
                        val confidence = json.optDouble(
                            "confidence",
                            json.optJSONObject("ai")?.optDouble("confidence", 0.0) ?: 0.0
                        )

                        if (brightnessEn.isNotBlank()) {
                            skinColorTextView.text = if (brightnessTh.isNotBlank())
                                "$brightnessTh ($brightnessEn) • ${"%.1f".format(confidence)}%"
                            else
                                "$brightnessEn • ${"%.1f".format(confidence)}%"
                            brightnessForProducts = brightnessEn
                            brightnessForPalette  = brightnessEn
                        } else {
                            skinColorTextView.text = "ไม่พบผลทำนายใน response"
                            Toast.makeText(this@PrizeActivity, "อ่านผลทำนายไม่ได้", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: JSONException) {
                        skinColorTextView.text = "ไม่สามารถอ่านผลลัพธ์สีผิวได้"
                        Toast.makeText(this@PrizeActivity, "Failed to parse: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    /** ---------------- palette by brightness (API แบบเดิม) ---------------- */
    private fun fetchColorPaletteByBrightness(brightness: String) {
        val url = Uri.parse("$BASE_URL/ai/cosmetics/recommendations").buildUpon()
            // ไม่จำเป็นต้องส่ง skinTone แล้ว แต่ส่งได้ ไม่เป็นไร
            .appendQueryParameter("skinTone", brightness)
            .build().toString()

        val request = Request.Builder().url(url).get().build()

        authClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PrizeActivity, "ไม่สามารถโหลดตารางสีได้", Toast.LENGTH_SHORT).show()
                    recommendedColorImageView.setImageResource(R.drawable.logo)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                Log.d("PrizeActivity", "palettes ${response.code}: ${bodyStr.take(300)}")
                runOnUiThread {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@PrizeActivity, "API ตารางสีผิดพลาด: ${response.code}", Toast.LENGTH_SHORT).show()
                        recommendedColorImageView.setImageResource(R.drawable.error_image)
                        return@runOnUiThread
                    }
                    try {
                        val json = JSONObject(bodyStr)
                        val arr = json.optJSONArray("recommendedColorPalettes")
                        if (arr != null && arr.length() > 0) {
                            val first = arr.getJSONObject(0)

                            // ใช้ URL ตามที่แบ็กเอนด์ส่งมา
                            val imageUrl = first.getString("ImageURL")

                            // สร้าง full URL ให้ถูกทุกกรณี
                            val fullUrl = when {
                                imageUrl.startsWith("http", true) -> imageUrl
                                imageUrl.startsWith("/") -> BASE_URL + imageUrl
                                else -> "$BASE_URL/palettes/$imageUrl"
                            }

                            Glide.with(this@PrizeActivity)
                                .load(fullUrl) // อย่า encode ทั้ง path เด็ดขาด
                                .placeholder(R.drawable.logo)
                                .error(R.drawable.error_image)
                                .into(recommendedColorImageView)
                        } else {
                            Toast.makeText(this@PrizeActivity, "ไม่มีตารางสีสำหรับโทนนี้", Toast.LENGTH_SHORT).show()
                            recommendedColorImageView.setImageResource(R.drawable.logo)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@PrizeActivity, "อ่านข้อมูลตารางสีไม่ได้", Toast.LENGTH_SHORT).show()
                        recommendedColorImageView.setImageResource(R.drawable.error_image)
                    }
                }
            }
        })
    }

    /** utils */
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val file = File(cacheDir, "uploaded_skin_image.jpg")
                file.outputStream().use { output -> input.copyTo(output) }
                file
            }
        } catch (e: Exception) {
            Log.e("PrizeActivity", "getFileFromUri: ${e.message}", e)
            null
        }
    }
}

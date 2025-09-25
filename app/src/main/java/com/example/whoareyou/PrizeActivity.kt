package com.kittipob.whoareyou

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
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
    private lateinit var recommendedColorImageView: ImageView

    // เก็บแยก 2 ค่า
    private var undertoneForPalette: String? = null       // Warm/Cool/Neutral
    private var brightnessForProducts: String? = null     // Fair/Medium/Deep

    private var previewPaletteButton: Button? = null
    private lateinit var nextButton: Button
    private lateinit var backButton: Button

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
        recommendedColorImageView = findViewById(R.id.recommendedColorImageView)
        previewPaletteButton = findViewById(R.id.previewPaletteButton)
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)

        // รับรูปและทำนายสีผิว
        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString != null) {
            uploadImageForSkinTonePrediction(Uri.parse(imageUriString))
        } else {
            Toast.makeText(this, "Image URI is missing for skin tone prediction", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "ไม่สามารถทำนายสีผิวได้"
        }

        // ดูตารางสี = ใช้ Undertone
        previewPaletteButton?.setOnClickListener {
            val tone = undertoneForPalette
            if (tone.isNullOrBlank()) {
                Toast.makeText(this, "ยังไม่ได้ผลทำนายสีผิว", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchColorPalette(tone)
        }

        // ไปหน้า Product = ส่ง Brightness (Fair/Medium/Deep)
        nextButton.setOnClickListener {
            if (brightnessForProducts == null) {
                Toast.makeText(this, "กำลังรอผลทำนายสีผิว... กรุณาลองอีกครั้ง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val itn = Intent(this, ProductActivity::class.java).apply {
                putExtra("EXTRA_SKIN_TONE", brightnessForProducts) // สำคัญ!
                putExtra("EXTRA_STYLE_NAME", "")
                putExtra("EXTRA_STYLE_ID", -1)
                putExtra("EXTRA_BUDGET", "")
            }
            startActivity(itn)
        }

        backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // ---------- ทำนายสีผิว ----------
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
                val bodyStr = if (response.isSuccessful) response.body?.string() else null
                val errStr = if (!response.isSuccessful) response.body?.string() else null
                runOnUiThread {
                    if (response.isSuccessful && bodyStr != null) {
                        try {
                            val json = JSONObject(bodyStr)

                            val overall = json.getString("overall_undertone")       // Warm/Cool/Neutral
                            val brightTh = json.optString("brightness_tone", "")     // โทนสว่าง/โทนกลาง/โทนเข้ม
                            val brightDb = thBrightnessToDb(brightTh)                // Fair/Medium/Deep

                            // โชว์ให้ผู้ใช้เข้าใจง่าย
                            skinColorTextView.text = if (brightTh.isNotBlank())
                                "$overall ($brightTh)"
                            else
                                overall

                            // เก็บไว้ใช้
                            undertoneForPalette = overall
                            brightnessForProducts = brightDb

                            Toast.makeText(
                                this@PrizeActivity,
                                "ทำนายสีผิวสำเร็จ: $overall / $brightDb",
                                Toast.LENGTH_SHORT
                            ).show()

                        } catch (e: JSONException) {
                            skinColorTextView.text = "ไม่สามารถอ่านผลลัพธ์สีผิวได้"
                            Toast.makeText(this@PrizeActivity, "Failed to parse skin tone: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        skinColorTextView.text = "API เกิดข้อผิดพลาด: ${response.code}"
                        Log.e("PrizeActivity", "Skin Tone API Error: ${response.code} - $errStr")
                    }
                }
            }
        })
    }

    // แปลงไทย -> ค่าใน DB
    private fun thBrightnessToDb(valTh: String?): String = when (valTh?.trim()) {
        "โทนสว่าง" -> "Fair"
        "โทนกลาง"  -> "Medium"
        "โทนเข้ม"   -> "Deep"
        else        -> "All"
    }

    // ---------- ตารางสี (ใช้ Undertone) ----------
    private fun fetchColorPalette(skinTone: String) {
        val url = Uri.parse("$BASE_URL/ai/cosmetics/recommendations").buildUpon()
            .appendQueryParameter("skinTone", skinTone)
            .build().toString()

        val request = Request.Builder().url(url).get().build()

        authClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PrizeActivity, "ไม่สามารถโหลดตารางสีได้", Toast.LENGTH_SHORT).show()
                    recommendedColorImageView.setImageResource(R.drawable.error_image)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = if (response.isSuccessful) response.body?.string() else null
                runOnUiThread {
                    if (response.isSuccessful && bodyStr != null) {
                        try {
                            val json = JSONObject(bodyStr)
                            val arr = json.getJSONArray("recommendedColorPalettes")
                            if (arr.length() > 0) {
                                val first = arr.getJSONObject(0)
                                val filename = first.getString("ImageURL")
                                val fullUrl = "$BASE_URL/palettes/${Uri.encode(filename)}"
                                Glide.with(this@PrizeActivity)
                                    .load(fullUrl)
                                    .placeholder(R.drawable.logo)
                                    .error(R.drawable.error_image)
                                    .into(recommendedColorImageView)
                            } else {
                                recommendedColorImageView.setImageResource(R.drawable.logo)
                            }
                        } catch (e: JSONException) {
                            Toast.makeText(this@PrizeActivity, "อ่านข้อมูลตารางสีไม่ได้", Toast.LENGTH_SHORT).show()
                            recommendedColorImageView.setImageResource(R.drawable.error_image)
                        }
                    } else {
                        Toast.makeText(this@PrizeActivity, "API ตารางสีผิดพลาด: ${response.code}", Toast.LENGTH_SHORT).show()
                        recommendedColorImageView.setImageResource(R.drawable.error_image)
                    }
                }
            }
        })
    }

    // ---------- Utils ----------
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

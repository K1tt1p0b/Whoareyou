package com.kittipob.whoareyou

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kittipob.whoareyou.databinding.ActivityFeedbackBinding
import com.google.gson.Gson
import com.google.gson.GsonBuilder // ✅ เพิ่ม import นี้
import com.google.gson.annotations.SerializedName // ✅ เพิ่ม import นี้
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class FeedBackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private val client = OkHttpClient()
    // private val gson = Gson() // ❌ ลบบรรทัดนี้ออก
    private lateinit var gson: Gson // ✅ เปลี่ยนเป็น lateinit var

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // ✅ สร้าง Gson instance ใน onCreate() เพื่อให้แน่ใจว่าถูก initialize
        gson = GsonBuilder().create()

        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ratingBar.setOnRatingBarChangeListener { ratingBar, rating, fromUser ->
            binding.tvRatingDescription.text = when (rating.toInt()) {
                1 -> "แย่มาก :( "
                2 -> "ไม่ค่อยดี"
                3 -> "พอใช้ได้"
                4 -> "ดีเยี่ยม!"
                5 -> "ดีที่สุด! ขอบคุณมาก"
                else -> "กรุณาเลือกจำนวนดาว"
            }
        }

        binding.btnSubmitFeedback.setOnClickListener {
            submitFeedback()
        }

        binding.btnBackFeedback.setOnClickListener {
            finish()
        }
    }

    private fun submitFeedback() {
        val rating = binding.ratingBar.rating
        val feedbackText = binding.etFeedback.text?.toString()?.trim() ?: ""

        Log.d("FeedbackAPI_Debug", "Rating value before API call: $rating")
        Log.d("FeedbackAPI_Debug", "Feedback Text value before API call: '$feedbackText'")


        if (rating == 0f) {
            Toast.makeText(this, "กรุณาให้คะแนนความพึงพอใจด้วยค่ะ", Toast.LENGTH_SHORT).show()
            return
        }

        if (feedbackText.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกความคิดเห็นด้วยค่ะ", Toast.LENGTH_SHORT).show()
            return
        }

        sendFeedbackToFlaskApi(rating.toInt(), feedbackText)
    }

    // ✅ แก้ไข Data Class ตรงนี้
    // ย้าย FeedbackPayload ออกมานอก FeedBackActivity เพื่อให้ Gson เห็นได้ชัดเจนขึ้น
    // และใช้ @SerializedName เพื่อระบุชื่อ key ที่ Flask คาดหวัง
    // รวมถึงเปลี่ยนเป็น 'var' (บางครั้งช่วยแก้ปัญหา serialization แปลกๆ ได้)
    data class FeedbackPayload(
        @SerializedName("rating") // ✅ ระบุชื่อ key ให้ตรงกับที่ Flask คาดหวัง
        var rating: Int,
        @SerializedName("feedback_text") // ✅ ระบุชื่อ key ให้ตรงกับที่ Flask คาดหวัง
        var feedback_text: String
    )


    private fun sendFeedbackToFlaskApi(rating: Int, feedback: String) {
        val rootUrl = getString(R.string.root_url)
        val url = "$rootUrl/ai/submit_feedback"

        // ✅ ใช้ FeedbackPayload ที่เราแก้ไขไปแล้ว
        val payload = FeedbackPayload(rating, feedback)

        // ✅ ตอนนี้ gson.toJson(payload) จะไม่ null แล้ว
        val jsonString = gson.toJson(payload)

        Log.d("FeedbackAPI_Debug", "JSON Payload being sent (after gson.toJson): $jsonString")

        // ❌ ลบบล็อกนี้ออกไปได้เลย เพราะ jsonString จะไม่เป็น null แล้ว
        /*
        if (jsonString == null) {
            Log.e("FeedbackAPI_Debug", "jsonString is null, cannot send request.")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FeedBackActivity, "เกิดข้อผิดพลาดในการสร้างข้อมูลส่ง", Toast.LENGTH_LONG).show()
            }
            return
        }
        */

        val mediaType = "application/json; charset=utf-8".toMediaType()
        // ✅ ใช้ toRequestBody(mediaType) โดยตรง
        val body: RequestBody = jsonString.toRequestBody(mediaType)


        val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)

        Log.d("FeedbackAPI_Debug", "Retrieved JWT Token: $token")
        Log.d("FeedbackAPI_Debug", "Request URL: $url")
        Log.d("FeedbackAPI_Debug", "Request Content-Type: ${mediaType.toString()}")


        if (token == null) {
            Toast.makeText(this, "ไม่ได้เข้าสู่ระบบ กรุณาเข้าสู่ระบบก่อน", Toast.LENGTH_LONG).show()
            return
        }

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer $token")
            // .header("Content-Type", "application/json; charset=utf-8") // ❌ ไม่จำเป็นต้องใส่ซ้ำ
            .build()

        Log.d("FeedbackAPI_Debug", "Final Request Headers: ${request.headers}")
        // Log.d("FeedbackAPI_Debug", "Request Body Length (bytes): ${body.contentLength()}") // ❌ อาจไม่จำเป็นต้อง log ทุกครั้ง


        lifecycleScope.launch(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    Log.d("FeedbackAPI", "Response Code: ${response.code}")
                    Log.d("FeedbackAPI", "Response Body: $responseBody")

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@FeedBackActivity, "ขอบคุณสำหรับความคิดเห็นของคุณ!", Toast.LENGTH_LONG).show()
                            finish()
                        } else {
                            val errorMessage = responseBody ?: "เกิดข้อผิดพลาดที่ไม่รู้จัก"
                            Toast.makeText(this@FeedBackActivity, "ส่งความคิดเห็นไม่สำเร็จ: $errorMessage", Toast.LENGTH_LONG).show()
                            Log.e("FeedbackAPI", "Failed to submit feedback: ${response.code} - $responseBody")
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FeedBackActivity, "เกิดข้อผิดพลาดในการเชื่อมต่อเครือข่าย: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("FeedbackAPI", "Network error: ${e.message}", e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FeedBackActivity, "เกิดข้อผิดพลาดที่ไม่คาดคิด: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
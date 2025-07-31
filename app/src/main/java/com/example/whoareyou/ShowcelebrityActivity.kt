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
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

class ShowcelebrityActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_showcelebrity)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.celebrityyou)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val imageView = findViewById<ImageView>(R.id.imageViewmy)
        val backButton = findViewById<Button>(R.id.button_back)
        val allowButton = findViewById<Button>(R.id.allow_button)

        // ✅ ปุ่มย้อนกลับ (Arrow)
        backButton.setOnClickListener {
            finish() // กลับไปหน้าก่อนหน้านี้
        }

        // ✅ ปุ่มอัปโหลดรูปใหม่ (กลับไปหน้า AddphotoActivity)
        allowButton.setOnClickListener {
            clearAppCache()
            val intent = Intent(this, AddphotoActivity::class.java)
            startActivity(intent)
            finish()
        }

        // ✅ รับค่า URI ของภาพจาก Intent
        val imageUriString = intent.getStringExtra("imageUri")
        Log.d("ShowcelebrityActivity", "Image URI: $imageUriString")

        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            imageView.setImageURI(imageUri)
            uploadImageToServer(imageUri)
        } else {
            Toast.makeText(this, "Image URI is invalid", Toast.LENGTH_SHORT).show()
        }


    }

    // ✅ ฟังก์ชันอัปโหลดรูปไปยังเซิร์ฟเวอร์
    private fun uploadImageToServer(imageUri: Uri) {
        val file = getFileFromUri(imageUri)
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found or unable to create file", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "กรุณาลงชื่อเข้าใช้งานก่อน", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val url = resources.getString(R.string.root_url) + "/ai/predict"
        if (url.isEmpty()) {
            Toast.makeText(this, "API URL is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")  // ส่ง JWT Token ใน Authorization header
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ShowcelebrityActivity, "Failed to upload image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        Toast.makeText(this@ShowcelebrityActivity, "Image uploaded successfully", Toast.LENGTH_SHORT).show()

                        if (responseBody != null) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val predictedClass = jsonObject.getString("predicted_match")
                                val confidenceScore = jsonObject.getDouble("confidence_score")
                                val celebrityName = findViewById<TextView>(R.id.celebrity_name)
                                val similarityPercentage = findViewById<TextView>(R.id.similarity_percentage)

                                celebrityName.text = predictedClass
                                similarityPercentage.text = "$confidenceScore %"

                            } catch (e: JSONException) {
                                Toast.makeText(this@ShowcelebrityActivity, "Failed to parse response: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@ShowcelebrityActivity, "Empty response", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        Toast.makeText(this@ShowcelebrityActivity, "Error: $errorBody", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // ✅ แปลง URI เป็นไฟล์เพื่ออัปโหลด
    private fun getFileFromUri(uri: Uri): File? {
        try {
            val contentResolver = contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = File(cacheDir, "uploaded_image.jpg")
                inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return file
            } else {
                Log.e("ShowcelebrityActivity", "InputStream is null")
            }
        } catch (e: Exception) {
            Log.e("ShowcelebrityActivity", "Failed to get file from URI: ${e.message}", e)
        }
        return null
    }

    // ✅ ล้าง Cache รูปภาพ
    private fun clearAppCache() {
        try {
            val cacheDir = cacheDir
            if (cacheDir.isDirectory) {
                val children = cacheDir.list()
                if (children != null) {
                    for (child in children) {
                        val success = File(cacheDir, child).delete()
                        if (!success) {
                            Log.d("ShowcelebrityActivity", "Failed to delete cache file: $child")
                        }
                    }
                }
            }
            Log.d("ShowcelebrityActivity", "Cache cleared successfully")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

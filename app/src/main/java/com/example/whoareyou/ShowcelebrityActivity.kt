package com.kittipob.whoareyou

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable // เพิ่ม import นี้
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // เพิ่ม import นี้
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*
import java.io.File
import java.io.FileOutputStream // เพิ่ม import นี้
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ShowcelebrityActivity : AppCompatActivity() {

    private lateinit var resultsContainer: LinearLayout
    private lateinit var imageViewMy: ImageView // เปลี่ยนชื่อตัวแปรให้ตรงกับ id ใน XML (imageViewmy)
    private lateinit var youLookLikeTextView: TextView // เพิ่มอ้างอิงสำหรับ TextView "ผลลัพธ์การทำนาย"

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

        // กำหนด View elements
        imageViewMy = findViewById(R.id.imageViewmy) // อ้างอิง ImageView ที่แสดงรูปภาพ
        val backButton = findViewById<Button>(R.id.button_back)
        val allowButton = findViewById<Button>(R.id.allow_button)
        val shareButton = findViewById<Button>(R.id.share_button) // ✅ อ้างอิงปุ่ม Share ที่เพิ่มเข้ามา
        resultsContainer = findViewById(R.id.results_container)
        youLookLikeTextView = findViewById(R.id.you_look_like) // ✅ อ้างอิง TextView "ผลลัพธ์การทำนาย"

        // ✅ ปุ่มย้อนกลับ (Arrow)
        backButton.setOnClickListener {
            finish()
        }

        // ✅ ปุ่มอัปโหลดรูปใหม่ (กลับไปหน้า AddphotoActivity)
        allowButton.setOnClickListener {
            clearAppCache()
            val intent = Intent(this, AddphotoActivity::class.java)
            startActivity(intent)
            finish()
        }

        // ✅ ตั้งค่า OnClickListener สำหรับปุ่ม Share
        shareButton.setOnClickListener {
            shareImageAndText()
        }

        // ✅ รับค่า URI ของภาพจาก Intent
        val imageUriString = intent.getStringExtra("imageUri")
        Log.d("ShowcelebrityActivity", "Image URI: $imageUriString")

        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            imageViewMy.setImageURI(imageUri) // ใช้ imageViewMy ที่ประกาศไว้
            uploadImageToServer(imageUri)
        } else {
            Toast.makeText(this, "Image URI is invalid", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ ฟังก์ชันอัปโหลดรูปไปยังเซิร์ฟเวอร์ (โค้ดเดิมของคุณ)
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
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ShowcelebrityActivity, "Failed to upload image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    Log.e("ShowcelebrityActivity", "Upload failed: ${e.localizedMessage}", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        Toast.makeText(this@ShowcelebrityActivity, "Image uploaded successfully", Toast.LENGTH_SHORT).show()
                        Log.d("ShowcelebrityActivity", "API Response: $responseBody")

                        if (responseBody != null) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val topMatchesArray = jsonObject.getJSONArray("top_matches")
                                resultsContainer.removeAllViews()

                                // ดึงชื่อคนดังอันดับ 1 มาใช้ในข้อความแชร์
                                var topCelebrityName = "คนดัง"
                                if (topMatchesArray.length() > 0) {
                                    topCelebrityName = topMatchesArray.getJSONObject(0).getString("name")
                                }
                                // อัปเดต TextView you_look_like (ถ้าต้องการให้แสดงชื่อคนดังอันดับแรก)
                                youLookLikeTextView.text = "คุณดูเหมือน ${topCelebrityName}!"


                                if (topMatchesArray.length() > 0) {
                                    for (i in 0 until topMatchesArray.length()) {
                                        val match = topMatchesArray.getJSONObject(i)
                                        val name = match.getString("name")
                                        val confidence = match.getDouble("confidence")

                                        val nameTextView = TextView(this@ShowcelebrityActivity).apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            text = "${i + 1}. $name"
                                            textSize = 18f
                                            gravity = Gravity.CENTER_HORIZONTAL
                                            setPadding(0, 8, 0, 0)
                                        }
                                        resultsContainer.addView(nameTextView)

                                        val confidenceTextView = TextView(this@ShowcelebrityActivity).apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            text = "ความคล้ายคลึง: ${"%.2f".format(confidence)} %" // จัดฟอร์แมตทศนิยม 2 ตำแหน่ง
                                            textSize = 16f
                                            gravity = Gravity.CENTER_HORIZONTAL
                                            setPadding(0, 0, 0, 8)
                                        }
                                        resultsContainer.addView(confidenceTextView)
                                    }
                                } else {
                                    Toast.makeText(this@ShowcelebrityActivity, "ไม่พบคนดังที่คล้ายคลึงในผลลัพธ์", Toast.LENGTH_SHORT).show()
                                    val noResultsTextView = TextView(this@ShowcelebrityActivity).apply {
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                        text = "ไม่พบคนดังที่คล้ายคลึง"
                                        textSize = 18f
                                        gravity = Gravity.CENTER_HORIZONTAL
                                        setPadding(0, 16, 0, 0)
                                    }
                                    resultsContainer.addView(noResultsTextView)
                                }

                            } catch (e: JSONException) {
                                Toast.makeText(this@ShowcelebrityActivity, "Failed to parse response: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("ShowcelebrityActivity", "JSON Parsing Error: ${e.message}", e)
                            }
                        } else {
                            Toast.makeText(this@ShowcelebrityActivity, "Empty response", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        Toast.makeText(this@ShowcelebrityActivity, "Error: ${response.code} - $errorBody", Toast.LENGTH_SHORT).show()
                        Log.e("ShowcelebrityActivity", "API Error: ${response.code} - $errorBody")
                    }
                }
            }
        })
    }

    // --- ✅ ฟังก์ชันสำหรับการแชร์รูปภาพและข้อความที่เพิ่มเข้ามา ---
    private fun shareImageAndText() {
        // 1. ดึงรูปภาพจาก ImageView
        val drawable = imageViewMy.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap

            // ดึงข้อความจาก TextView "ผลลัพธ์การทำนาย" และผลลัพธ์ 5 อันดับแรก
            val shareTextBuilder = StringBuilder()
            shareTextBuilder.append(youLookLikeTextView.text).append("\n\n")

            // เพิ่มผลลัพธ์ 5 อันดับแรก (ดึงจาก resultsContainer)
            for (i in 0 until resultsContainer.childCount) {
                val childView = resultsContainer.getChildAt(i)
                if (childView is TextView) {
                    shareTextBuilder.append(childView.text).append("\n")
                }
            }
            shareTextBuilder.append("\nลองมาเล่นดูว่าคุณเหมือนใครในแอป 'WhoAreYou'!")

            val shareText = shareTextBuilder.toString()

            // 2. บันทึก Bitmap ลงในไฟล์ชั่วคราว
            var uri: Uri? = null
            try {
                // สร้าง cache directory
                val cachePath = File(externalCacheDir, "shared_images")
                cachePath.mkdirs() // สร้างโฟลเดอร์ถ้ายังไม่มี

                // สร้างไฟล์สำหรับรูปภาพ
                val file = File(cachePath, "my_celebrity_match.png")
                val fOut = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut) // บีบอัดรูปเป็น PNG
                fOut.flush()
                fOut.close()

                // รับ Uri จาก FileProvider (สำคัญสำหรับ Android N และสูงกว่า)
                uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "ไม่สามารถเตรียมรูปภาพเพื่อแชร์ได้: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                return
            }

            // 3. สร้าง Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/png" // กำหนดประเภทของข้อมูลที่จะแชร์
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri) // แนบรูปภาพ
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText) // แนบข้อความ
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // อนุญาตให้แอปปลายทางอ่าน URI ได้

            // 4. แสดงตัวเลือกการแชร์
            startActivity(Intent.createChooser(shareIntent, "แชร์ผลลัพธ์ผ่าน..."))
        } else {
            // กรณีไม่มีรูปภาพใน ImageView หรือรูปภาพไม่ใช่ BitmapDrawable
            val shareText = "มาเล่นแอป 'WhoAreYou' ดูว่าคุณเหมือนใครกัน!"
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            startActivity(Intent.createChooser(shareIntent, "แชร์ข้อความผ่าน..."))
        }
    }
    // --- สิ้นสุดฟังก์ชันสำหรับการแชร์ ---

    // ✅ แปลง URI เป็นไฟล์เพื่ออัปโหลด (โค้ดเดิมของคุณ)
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

    // ✅ ล้าง Cache รูปภาพ (โค้ดเดิมของคุณ)
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
package com.kittipob.whoareyou

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PrizeActivity : AppCompatActivity() {

    // ✅ ดึงค่า ROOT_URL มาจาก strings.xml แทนการ hardcode
    private lateinit var BASE_URL: String // เปลี่ยนเป็น lateinit var เพราะจะดึงค่าตอน onCreate

    private lateinit var skinColorTextView: TextView
    private lateinit var recommendedColorImageView: ImageView
    private lateinit var styleSpinner: Spinner
    private var makeupLooksList: List<MakeupLookItem> = emptyList()

    private var currentSkinTone: String? = null // เก็บ skinTone ที่ได้จากการทำนาย

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_prize)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.prize_layout_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ✅ Initialize BASE_URL ที่นี่
        BASE_URL = getString(R.string.root_url)

        skinColorTextView = findViewById(R.id.skinColorTextView)
        recommendedColorImageView = findViewById(R.id.recommendedColorImageView)
        val budgetSpinner: Spinner = findViewById(R.id.budgetSpinner)
        styleSpinner = findViewById(R.id.styleSpinner)
        val nextButton: Button = findViewById(R.id.nextButton)
        val backButton: Button = findViewById(R.id.backButton)

        val imageUriString = intent.getStringExtra("imageUri")
        Log.d("PrizeActivity", "Image URI: $imageUriString")
        Log.d("PrizeActivity", "Using BASE_URL: $BASE_URL") // ✅ เพิ่ม log เพื่อยืนยันว่าดึงค่ามาถูกต้อง

        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            uploadImageForSkinTonePrediction(imageUri)
        } else {
            Toast.makeText(this, "Image URI is missing for skin tone prediction", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "ไม่สามารถทำนายสีผิวได้"
        }

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
                Log.d("PrizeActivity", "Selected budget: $selectedBudget")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        fetchMakeupLooksForSpinner() // ดึงข้อมูลสไตล์จาก API

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedStyleName = parent.getItemAtPosition(position).toString()
                Log.d("PrizeActivity", "Selected style: $selectedStyleName")

                val selectedLook = makeupLooksList.find { it.lookName == selectedStyleName }
                selectedLook?.let {
                    Log.d("PrizeActivity", "Selected LookID: ${it.lookID}, Category: ${it.lookCategory}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        nextButton.setOnClickListener {
            val selectedBudget = budgetSpinner.selectedItem.toString()
            val selectedStyleName = styleSpinner.selectedItem.toString()

            val selectedLook = makeupLooksList.find { it.lookName == selectedStyleName }
            val selectedLookId = selectedLook?.lookID ?: -1

            if (currentSkinTone == null) {
                Toast.makeText(this, "กำลังรอผลทำนายสีผิว... กรุณาลองอีกครั้ง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, ProductActivity::class.java)
            intent.putExtra("EXTRA_SKIN_TONE", currentSkinTone)
            intent.putExtra("EXTRA_BUDGET", selectedBudget)
            intent.putExtra("EXTRA_STYLE_NAME", selectedStyleName)
            intent.putExtra("EXTRA_STYLE_ID", selectedLookId)
            startActivity(intent)
        }

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    data class MakeupLookItem(
        val lookID: Int,
        val lookName: String,
        val lookCategory: String?,
        val description: String?
    )

    private fun fetchMakeupLooksForSpinner() {
        val url = BASE_URL + "/ai/makeup_looks" // ✅ ใช้ BASE_URL ที่ดึงมาจาก strings.xml
        if (url.isEmpty()) { // ตรวจสอบ url.isEmpty() แทน resources.getString(R.string.root_url).isEmpty()
            Toast.makeText(this, "API URL สำหรับสไตล์ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            setupStyleSpinner(emptyList())
            return
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PrizeActivity, "Failed to load makeup styles: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    Log.e("PrizeActivity", "Makeup styles API failed: ${e.localizedMessage}", e)
                    setupStyleSpinner(emptyList())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        if (responseBody != null) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val jsonArray = jsonObject.getJSONArray("data")

                                val styles = mutableListOf<MakeupLookItem>()
                                for (i in 0 until jsonArray.length()) {
                                    val itemObject = jsonArray.getJSONObject(i)
                                    val lookID = itemObject.getInt("LookID")
                                    val lookName = itemObject.getString("lookName")
                                    val lookCategory = itemObject.optString("lookCategory", null)
                                    val description = itemObject.optString("description", null)

                                    styles.add(MakeupLookItem(lookID, lookName, lookCategory, description))
                                }
                                makeupLooksList = styles
                                val styleNames = styles.map { it.lookName }
                                setupStyleSpinner(styleNames)

                            } catch (e: JSONException) {
                                Toast.makeText(this@PrizeActivity, "Failed to parse makeup styles: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("PrizeActivity", "JSON Parsing Error for styles: ${e.message}", e)
                                setupStyleSpinner(emptyList())
                            }
                        } else {
                            Toast.makeText(this@PrizeActivity, "Empty makeup styles response", Toast.LENGTH_LONG).show()
                            setupStyleSpinner(emptyList())
                        }
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        Toast.makeText(this@PrizeActivity, "Makeup styles API Error: ${response.code} - $errorBody", Toast.LENGTH_LONG).show()
                        Log.e("PrizeActivity", "Makeup styles API Error: ${response.code} - $errorBody")
                        setupStyleSpinner(emptyList())
                    }
                }
            }
        })
    }

    private fun setupStyleSpinner(styles: List<String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            styles
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        styleSpinner.adapter = adapter
    }

    private fun uploadImageForSkinTonePrediction(imageUri: Uri) {
        val file = getFileFromUri(imageUri)
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found or unable to create file", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "ไม่สามารถประมวลผลรูปภาพได้"
            return
        }

        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", "")

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "กรุณาเข้าสู่ระบบเพื่อทำนายสีผิว"
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val url = BASE_URL + "/ai/predict_skin_tone" // ✅ ใช้ BASE_URL ที่ดึงมาจาก strings.xml
        if (url.isEmpty()) { // ตรวจสอบ url.isEmpty() แทน resources.getString(R.string.root_url).isEmpty()
            Toast.makeText(this, "API URL is missing", Toast.LENGTH_SHORT).show()
            skinColorTextView.text = "URL API ไม่ถูกต้อง"
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
                    Toast.makeText(this@PrizeActivity, "Failed to predict skin tone: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    Log.e("PrizeActivity", "Skin tone prediction failed: ${e.localizedMessage}", e)
                    skinColorTextView.text = "เกิดข้อผิดพลาดในการเชื่อมต่อ"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        Log.d("PrizeActivity", "Skin Tone API Response: $responseBody")

                        if (responseBody != null) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val overallUndertone = jsonObject.getString("overall_undertone")

                                skinColorTextView.text = overallUndertone
                                Toast.makeText(this@PrizeActivity, "ทำนายสีผิวสำเร็จ: $overallUndertone", Toast.LENGTH_SHORT).show()

                                currentSkinTone = overallUndertone

                                fetchColorPalette(overallUndertone)

                            } catch (e: JSONException) {
                                Toast.makeText(this@PrizeActivity, "Failed to parse skin tone response: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("PrizeActivity", "JSON Parsing Error: ${e.message}", e)
                                skinColorTextView.text = "ไม่สามารถอ่านผลลัพธ์สีผิวได้"
                            }
                        } else {
                            Toast.makeText(this@PrizeActivity, "Empty skin tone response", Toast.LENGTH_SHORT).show()
                            skinColorTextView.text = "ไม่ได้รับผลลัพธ์สีผิว"
                        }
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        Toast.makeText(this@PrizeActivity, "Skin Tone API Error: ${response.code} - $errorBody", Toast.LENGTH_SHORT).show()
                        Log.e("PrizeActivity", "Skin Tone API Error: ${response.code} - $errorBody")
                        skinColorTextView.text = "API เกิดข้อผิดพลาด: ${response.code}"
                    }
                }
            }
        })
    }

    private fun fetchColorPalette(skinTone: String) {
        val url = BASE_URL + "/ai/cosmetics/recommendations?skinTone=$skinTone" // ✅ ใช้ BASE_URL ที่ดึงมาจาก strings.xml

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("PrizeActivity", "Failed to fetch color palette: ${e.localizedMessage}", e)
                    Toast.makeText(this@PrizeActivity, "ไม่สามารถโหลดตารางสีได้", Toast.LENGTH_SHORT).show()
                    recommendedColorImageView.setImageResource(R.drawable.error_image)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        if (responseBody != null) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val colorPalettesArray = jsonObject.getJSONArray("recommendedColorPalettes")

                                if (colorPalettesArray.length() > 0) {
                                    val firstPalette = colorPalettesArray.getJSONObject(0)
                                    // ✅ ดึงแค่ชื่อไฟล์จากฐานข้อมูล (สมมติว่า ImageURL ใน DB มีแค่ชื่อไฟล์แล้ว)
                                    val imageFilename = firstPalette.getString("ImageURL")

                                    // ✅ สร้าง URL รูปภาพเต็มรูปแบบ: BASE_URL + /palettes/ + ชื่อไฟล์
                                    // /palettes/ คือ path ที่ @app.route ใน Flask กำหนดไว้
                                    val fullImageUrl = "$BASE_URL/palettes/$imageFilename"
                                    Log.d("PrizeActivity", "Attempting to load image with URL: $fullImageUrl")

                                    Glide.with(this@PrizeActivity)
                                        .load(fullImageUrl) // ✅ ใช้ fullImageUrl ที่สร้างขึ้นมา
                                        .placeholder(R.drawable.logo)
                                        .error(R.drawable.error_image)
                                        .into(recommendedColorImageView)
                                    Log.d("PrizeActivity", "โหลดรูปตารางสีสำเร็จ: $fullImageUrl")
                                } else {
                                    Log.w("PrizeActivity", "ไม่พบตารางสีสำหรับโทน: $skinTone")
                                    recommendedColorImageView.setImageResource(R.drawable.logo)
                                }

                            } catch (e: JSONException) {
                                Log.e("PrizeActivity", "JSON Parsing Error for color palette: ${e.message}", e)
                                Toast.makeText(this@PrizeActivity, "ไม่สามารถอ่านข้อมูลตารางสีได้", Toast.LENGTH_SHORT).show()
                                recommendedColorImageView.setImageResource(R.drawable.error_image)
                            }
                        } else {
                            Log.w("PrizeActivity", "Empty response for color palette")
                            recommendedColorImageView.setImageResource(R.drawable.error_image)
                        }
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        Log.e("PrizeActivity", "Color palette API Error: ${response.code} - $errorBody")
                        Toast.makeText(this@PrizeActivity, "API ตารางสีเกิดข้อผิดพลาด: ${response.code}", Toast.LENGTH_SHORT).show()
                        recommendedColorImageView.setImageResource(R.drawable.error_image)
                    }
                }
            }
        })
    }


    private fun getFileFromUri(uri: Uri): File? {
        try {
            val contentResolver = contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = File(cacheDir, "uploaded_skin_image.jpg")
                inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return file
            } else {
                Log.e("PrizeActivity", "InputStream is null for skin tone image")
            }
        } catch (e: Exception) {
            Log.e("PrizeActivity", "Failed to get file from URI for skin tone: ${e.message}", e)
        }
        return null
    }

    private fun clearAppCache() {
        try {
            val cacheDir = cacheDir
            if (cacheDir.isDirectory) {
                val children = cacheDir.list()
                if (children != null) {
                    for (child in children) {
                        val success = File(cacheDir, child).delete()
                        if (!success) {
                            Log.d("PrizeActivity", "Failed to delete cache file: $child")
                        }
                    }
                }
            }
            Log.d("PrizeActivity", "Cache cleared successfully")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
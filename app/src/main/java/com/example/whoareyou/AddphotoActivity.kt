package com.kittipob.whoareyou

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kittipob.whoareyou.databinding.ActivityAddphotoBinding
import java.io.File
import java.io.FileOutputStream

// ==== NEW: OkHttp for calling /ai/logout ====
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class AddphotoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddphotoBinding
    private val PICK_IMAGE = 1
    private val CAMERA_REQUEST = 2

    // ==== NEW: your API base URL (เปลี่ยนให้ตรงกับ backend ของคุณ) ====
    private val BASE_URL = "http://YOUR_SERVER_HOST:5003"  // เช่น http://10.0.2.2:5003 ถ้ารันบน emulator

    // ==== NEW: SharedPreferences ใช้ซ้ำ ====
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("UserSession", MODE_PRIVATE)
    }

    // ==== NEW: OkHttp client แบบเบา ๆ ====
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ใช้ ViewBinding
        binding = ActivityAddphotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // จัดการการแสดงผลของ System Bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addphoto)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ตรวจสอบและขอ Permission
        checkPermissions()

        // คลิกเพื่อเลือกรูป
        binding.imageView.setOnClickListener {
            showImageChooser()
        }

        // คลิกเพื่อยืนยัน
        binding.confirmButton.setOnClickListener {
            Toast.makeText(this, "Image confirmed!", Toast.LENGTH_SHORT).show()
        }

        // คลิกเพื่อล้างรูปภาพ
        binding.clearButton.setOnClickListener {
            clearImage()
        }

        // ปุ่มออกจากระบบ
        binding.buttonLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun showImageChooser() {
        val options = arrayOf<CharSequence>("ถ่ายรูป", "เลือกจากแกลเลอรี", "ยกเลิก")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("เลือกตัวเลือก")
        builder.setItems(options) { dialog, item ->
            when {
                options[item] == "ถ่ายรูป" -> {
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(cameraIntent, CAMERA_REQUEST)
                }
                options[item] == "เลือกจากแกลเลอรี" -> {
                    val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    galleryIntent.type = "image/*"
                    startActivityForResult(galleryIntent, PICK_IMAGE)
                }
                options[item] == "ยกเลิก" -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun checkPermissions() {
        // Android 13+ ใช้ READ_MEDIA_IMAGES แทน READ_EXTERNAL_STORAGE
        val needsRead =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
            else
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        val needsCamera =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED

        if (needsCamera || needsRead) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES),
                    101
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                    101
                )
            }
        }
    }

    fun onNext(selectedImageUri: Uri) {
        // แสดงปุ่มยืนยันและปุ่มล้าง
        binding.confirmButton.visibility = View.VISIBLE
        binding.clearButton.visibility = View.VISIBLE
        binding.selectedImageText.visibility = View.VISIBLE // แสดงข้อความ "นี่คือรูปภาพของคุณ"

        binding.confirmButton.setOnClickListener {
            val intent = Intent(this, yourfaceActivity::class.java)
            intent.putExtra("imageUri", selectedImageUri.toString())
            startActivity(intent)
        }
    }

    @Deprecated("onActivityResult is deprecated; works for now but consider Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE -> {
                    val selectedImage: Uri? = data?.data
                    selectedImage?.let {
                        binding.imageViewshow.setImageURI(it)
                        binding.imageViewshow.visibility = View.VISIBLE
                        binding.imageView.visibility = View.GONE
                        binding.textView.visibility = View.GONE
                        onNext(it)
                    }
                }
                CAMERA_REQUEST -> {
                    val photo: Bitmap = data?.extras?.get("data") as Bitmap
                    binding.imageViewshow.setImageBitmap(photo)
                    binding.imageViewshow.visibility = View.VISIBLE
                    binding.imageView.visibility = View.GONE
                    binding.textView.visibility = View.GONE
                    val placeholderUri = saveImageToExternalStorage(photo)
                    onNext(placeholderUri)
                }
            }
        }
    }

    private fun saveImageToExternalStorage(bitmap: Bitmap): Uri {
        val imagesFolder = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "YourImages")
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs()
        }

        val file = File(imagesFolder, "${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        return Uri.fromFile(file)
    }

    private fun clearImage() {
        // ซ่อนรูปที่แสดง
        binding.imageViewshow.setImageDrawable(null)
        binding.imageViewshow.visibility = View.GONE

        // แสดงไอคอนอัพโหลดและข้อความเดิม
        binding.imageView.visibility = View.VISIBLE
        binding.textView.visibility = View.VISIBLE

        // ซ่อนปุ่มยืนยันและปุ่มล้าง
        binding.confirmButton.visibility = View.GONE
        binding.clearButton.visibility = View.GONE

        // ซ่อนข้อความ "นี่คือรูปภาพของคุณ"
        binding.selectedImageText.visibility = View.GONE
    }

    // ==== NEW: เรียก /ai/logout ที่ backend ด้วย Bearer token ====
    private fun callServerLogout(token: String?, onAlways: () -> Unit) {
        if (token.isNullOrBlank()) {
            onAlways()
            return
        }

        val url = "$BASE_URL/ai/logout"
        // POST ว่าง ๆ ก็ได้
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), "{}")
        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()

        // ทำงานบน background thread ของ OkHttp
        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // ต่อให้ล้มเหลว เราก็จะลบ token ฝั่ง client อยู่ดี
                runOnUiThread { onAlways() }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
                // 200/401/422 ก็ลบออกเหมือนกัน
                runOnUiThread { onAlways() }
            }
        })
    }

    // ==== UPDATED: logoutUser -> ยิงไป server แล้วค่อยลบ token ฝั่ง client ====
    private fun logoutUser() {
        val token = prefs.getString("auth_token", null)

        // ยิง /ai/logout ก่อน (ไม่ว่าจะสำเร็จหรือไม่ เราจะลบ token/local state ต่อ)
        callServerLogout(token) {
            val editor = prefs.edit()
            editor.putBoolean("isLoggedIn", false)
            editor.remove("auth_token")
            editor.apply()

            Toast.makeText(this, "ออกจากระบบเรียบร้อย", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, homeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

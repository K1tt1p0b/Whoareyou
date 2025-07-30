package com.kittipob.whoareyou

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class homeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val sharedPreferences: SharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)
        val isConsentGiven = sharedPreferences.getBoolean("isConsentGiven", false)

        // ✅ ถ้าผู้ใช้เคย Login และให้ Consent แล้ว → ไปที่ `activity_addphoto`
        if (isLoggedIn && isConsentGiven) {
            startActivity(Intent(this, AddphotoActivity::class.java))
            finish()
            return
        }

        // ✅ ถ้าผู้ใช้ Login แล้ว แต่ยังไม่ให้ Consent → ไปที่ `confrimUpphotoActivity`
        if (isLoggedIn && !isConsentGiven) {
            startActivity(Intent(this, confrimUpphotoActivity::class.java))
            finish()
            return
        }

        val emailInput: EditText = findViewById(R.id.usernameEditText)
        val passwordInput: EditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.loginButton)
        val registerButton: TextView = findViewById(R.id.registerTextView)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกอีเมลและรหัสผ่าน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiUrl = resources.getString(R.string.root_url) + "/ai/login"
            loginUser(apiUrl, email, password)
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser(apiUrl: String, username: String, password: String) {
        val sharedPreferences: SharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)

        Thread {
            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonParam = JSONObject()
                jsonParam.put("username", username)
                jsonParam.put("password", password)

                val outputStream: OutputStream = conn.outputStream
                outputStream.write(jsonParam.toString().toByteArray(StandardCharsets.UTF_8))
                outputStream.close()

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val bufferedReader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?

                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    val jsonResponse = JSONObject(response.toString())
                    val token = jsonResponse.optString("token", "")  // รับ Token จาก Response
                    bufferedReader.close()

                    // บันทึก Token ลงใน SharedPreferences
                    val editor = sharedPreferences.edit()
                    editor.putBoolean("isLoggedIn", true)
                    editor.putString("auth_token", token)  // เก็บ JWT Token
                    editor.apply()

                    runOnUiThread {
                        Toast.makeText(this@homeActivity, "เข้าสู่ระบบสำเร็จ!", Toast.LENGTH_SHORT).show()

                        // ✅ เมื่อ Login สำเร็จ → ไปที่ `confrimUpphotoActivity` (ขอ Consent)
                        val intent = Intent(this@homeActivity, confrimUpphotoActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@homeActivity, "เข้าสู่ระบบไม่สำเร็จ! ตรวจสอบอีเมลหรือรหัสผ่าน", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@homeActivity, "เกิดข้อผิดพลาดในการเชื่อมต่อ", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}

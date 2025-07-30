package com.kittipob.whoareyou

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailInput: EditText = findViewById(R.id.emailInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val confirmPasswordInput: EditText = findViewById(R.id.confirmPasswordInput)
        val registerButton: Button = findViewById(R.id.registerButton)
        val backToLogin: TextView = findViewById(R.id.backToLogin)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกข้อมูลให้ครบ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "รหัสผ่านไม่ตรงกัน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiUrl = resources.getString(R.string.root_url) + "/ai/register"
            registerUser(apiUrl, email, password)
        }

        backToLogin.setOnClickListener {
            val intent = Intent(this, homeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun registerUser(apiUrl: String, username: String, password: String) {
        val sharedPreferences: SharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)

        Thread {
            try {
                Log.d("RegisterActivity", "🔄 ส่ง Request ไปที่: $apiUrl")

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val jsonParam = JSONObject()
                jsonParam.put("username", username)
                jsonParam.put("password", password)

                val outputStream: OutputStream = conn.outputStream
                outputStream.write(jsonParam.toString().toByteArray(StandardCharsets.UTF_8))
                outputStream.close()

                val responseCode = conn.responseCode
                Log.d("RegisterActivity", "📩 Response Code: $responseCode")

                val bufferedReader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?

                while (bufferedReader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                bufferedReader.close()

                Log.d("RegisterActivity", "📦 Response Body: $response")

                val jsonResponse = JSONObject(response.toString())
                val status = jsonResponse.optString("status", "error") // ✅ ป้องกัน Null
                val message = jsonResponse.optString("message", "เกิดข้อผิดพลาด") // ✅ อ่าน message

                runOnUiThread {
                    if (status == "success") {
                        Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()

                        // ✅ กลับไปหน้า Login (homeActivity) หลังสมัครเสร็จ
                        val intent = Intent(this@RegisterActivity, homeActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("RegisterActivity", "❌ Connection Error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "เกิดข้อผิดพลาดในการเชื่อมต่อ", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}

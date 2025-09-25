package com.kittipob.whoareyou.net

object ApiConfig {
    // ใช้ 10.0.2.2 เมื่อทดสอบบน Emulator (ชี้ไปยัง localhost ของเครื่องคอม)
    const val BASE_URL = "http://10.0.2.2:5000"

    fun fullUrl(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val p = pathOrUrl.trim()
        return if (p.startsWith("http://", true) || p.startsWith("https://", true)) p
        else BASE_URL + (if (p.startsWith("/")) p else "/$p")
    }
}

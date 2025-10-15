package com.kittipob.whoareyou.net

object ApiConfig {
    // Emulator: 10.0.2.2 = localhost ของคอมพ์
    const val FLASK_BASE = "http://10.0.2.2:5003"  // ใช้กับ /ai/* และ /palettes/*
    const val NODE_BASE  = "http://10.0.2.2:5000"  // ใช้กับ /images/* (รูปสินค้า)

    fun fullUrl(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val p = pathOrUrl.trim()
        // absolute URL ก็ส่งกลับตรงๆ
        if (p.startsWith("http://", true) || p.startsWith("https://", true)) return p

        // รูปสินค้าใน DB เป็น /images/xxxx → ไป Node
        if (p.startsWith("/images/")) return NODE_BASE + p

        // อย่างอื่น (รวม /palettes/ ด้วย) → ไป Flask
        return if (p.startsWith("/")) FLASK_BASE + p else "$FLASK_BASE/$p"
    }
}

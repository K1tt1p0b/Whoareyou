package com.kittipob.whoareyou.ui

data class ProductItem(
    val id: Int? = null,
    val brandName: String? = null,
    val productName: String? = null,

    // ✅ ใช้ field นี้แทน category
    val type: String? = null,

    val shadeName: String? = null,

    // ราคา/รูป/ลิงก์
    val priceTHB: Double? = null,
    val imageURL: String? = null,
    val productURL: String? = null,

    // คะแนนจากแบ็กเอนด์
    val hybridConfidence: Int? = null,
    val confidenceLevel: String? = null,
    val deltaE00: Double? = null,
    val badges: List<String>? = null,
    val reasons: List<String>? = null
)

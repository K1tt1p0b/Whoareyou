package com.kittipob.whoareyou.ui

import com.google.gson.annotations.SerializedName

data class ProductItem(
    val id: Int? = null,
    val brandName: String? = null,
    val productName: String? = null,
    val category: String? = null,
    val shadeName: String? = null,
    val shadeCode: String? = null,

    // ราคา/รูป/ลิงก์ (ไม่มี best* แล้ว)
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

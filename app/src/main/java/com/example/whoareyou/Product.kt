package com.example.project101

// Product.kt
data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String, // URL ของรูปภาพ
    val category: String // "เกาหลี", "lipstick", "primer" etc.
)
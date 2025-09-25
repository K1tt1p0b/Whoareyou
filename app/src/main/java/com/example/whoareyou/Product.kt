package com.kittipob.whoareyou.ui

import com.google.gson.annotations.SerializedName

data class ProductItem(
    @SerializedName("CosmeticID") val id: Int?,
    @SerializedName("brandName")  val brandName: String?,
    @SerializedName("Name")       val productName: String?,
    @SerializedName("Type")       val category: String?,
    @SerializedName("Shade")      val shadeName: String?,
    @SerializedName("ShadeCode")  val shadeCode: String?,
    @SerializedName("Price")      val priceTHB: Double?,
    @SerializedName("ImageURL")   val imageURL: String?,
    @SerializedName("ProductLink")val productURL: String?,

    @SerializedName("bestPrice")  val bestPrice: Double? = null,
    @SerializedName("bestURL")    val shopURL: String? = null,
    @SerializedName("isOfficial") val isOfficial: Int? = null,
    @SerializedName("bestRating") val rating: Double? = null,
    @SerializedName("bestReviews")val reviewCount: Int? = null,

    @SerializedName("hybrid_confidence") val hybridConfidence: Int? = null,
    @SerializedName("confidence_level")  val confidenceLevel: String? = null,
    @SerializedName("badges")            val badges: List<String>? = null,
    @SerializedName("reasons")           val reasons: List<String>? = null,

    @SerializedName("suitableSkinTone")  val suitableSkinTone: String? = null,
    @SerializedName("Description")       val description: String? = null
)

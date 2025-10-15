package com.kittipob.whoareyou.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kittipob.whoareyou.R
import com.kittipob.whoareyou.net.ApiConfig
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class ProductAdapter : ListAdapter<ProductItem, ProductAdapter.VH>(DIFF) {

    var onItemClick: ((ProductItem) -> Unit)? = null

    companion object {
        private val TH = Locale("th", "TH")

        private val DIFF = object : DiffUtil.ItemCallback<ProductItem>() {
            override fun areItemsTheSame(old: ProductItem, new: ProductItem) =
                (old.id == new.id)
            override fun areContentsTheSame(old: ProductItem, new: ProductItem) =
                (old == new)
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView      = v.findViewById(R.id.productImage)
        val tvBrand: TextView  = v.findViewById(R.id.productBrand)
        val tvName: TextView   = v.findViewById(R.id.productName)
        val tvPrice: TextView  = v.findViewById(R.id.productPrice)

        val tvConfidence: TextView = v.findViewById(R.id.tvConfidence)
        val tvBadges: TextView     = v.findViewById(R.id.tvBadges)
        val tvDelta: TextView      = v.findViewById(R.id.tvDelta)
        val tvReasons: TextView    = v.findViewById(R.id.tvReasons)

        init {
            v.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onItemClick?.invoke(getItem(pos))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_product, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = getItem(position)

        h.tvBrand.text = it.brandName.orEmpty()
        h.tvName.text  = it.productName.orEmpty()

        val priceText = it.priceTHB?.let { p ->
            NumberFormat.getNumberInstance(TH).format(p) + " บาท"
        } ?: "-"
        h.tvPrice.text = priceText

        val conf = (it.hybridConfidence ?: 0).coerceIn(0, 100)
        val level = it.confidenceLevel ?: "-"
        h.tvConfidence.text = "ความมั่นใจ ~ ${conf}% ($level)"

        h.tvBadges.text = it.badges?.joinToString(" · ") { b ->
            when (b.lowercase()) {
                "official" -> "Official"
                "admin-approved" -> "Admin Approved"
                else -> b.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }
            }
        } ?: ""

        // ✅ แปลงค่า ΔE00 ให้เป็นหมวดที่เข้าใจง่าย + คำอธิบาย
        val (suitLabel, suitDesc) = mapSuitability(it.type, it.deltaE00)

        h.tvDelta.visibility = View.VISIBLE
        h.tvDelta.text = suitLabel              // เช่น “เหมาะมาก”, “คอนทราสต์สวย”, “โทนสุภาพ”

        if (suitDesc.isNotBlank()) {
            h.tvReasons.visibility = View.VISIBLE
            h.tvReasons.text = "• $suitDesc"
        } else {
            // ถ้าอยากรวมเหตุผลจาก backend เพิ่มเติมด้วยก็เติมต่อได้
            val reasons = it.reasons?.takeIf { r -> r.isNotEmpty() }?.joinToString("\n") { r -> "• $r" }
            if (!reasons.isNullOrBlank()) {
                h.tvReasons.visibility = View.VISIBLE
                h.tvReasons.text = reasons
            } else {
                h.tvReasons.visibility = View.GONE
            }
        }

        val img = ApiConfig.fullUrl(it.imageURL)
        Glide.with(h.itemView)
            .load(img ?: R.drawable.logo)
            .placeholder(R.drawable.logo)
            .error(R.drawable.logo)
            .into(h.iv)
    }

    // ---------- Helpers ----------

    private fun mapSuitability(
        productType: String?,
        deltaE00: Double?
    ): Pair<String, String> {
        if (deltaE00 == null) return "ไม่พบข้อมูลสี" to "สินค้านี้ยังไม่มีค่าสีสำหรับประเมิน"
        val d = deltaE00
        val t = (productType ?: "").lowercase()

        val isComplexion = listOf("foundation","concealer","bb","cc","tinted","powder","cushion","contour","bronzer","base").any { t.contains(it) }
        val isLipOrBlush = listOf("lip","lipstick","gloss","oil","tint","stain","kit","liner","blush","cheek").any { t.contains(it) }
        val isEye        = listOf("eyeshadow","eye shadow","eyeliner","mascara").any { t.contains(it) }
        val isBrow       = listOf("brow","eyebrow").any { t.contains(it) }


        return when {
            isComplexion -> when {
                d <= 2  -> "เหมาะมาก"   to "สีผิวแทบตรงกัน"
                d <= 4  -> "ใกล้เคียง"  to "เฉดใกล้ผิว แนะนำลองที่แนวกราม"
                d <= 6  -> "พอใช้"     to "อาจต้องบาลานซ์ด้วยไฮไลต์/คอนซีลเลอร์"
                else    -> "ต่างจากผิว" to "มีโอกาสเพี้ยนเมื่อทาทั่วหน้า"
            }
            isLipOrBlush -> when {
                d in 15.0..25.0 -> "คอนทราสต์สวย" to "ช่วยให้ใบหน้าดูมีชีวิตชีวา"
                d in 25.0..40.0 -> "เด่นชัด"     to "สีจัดขึ้น เหมาะกับลุคชัด"
                else            -> "โทนสุภาพ"    to "คอนทราสต์ไม่แรง ใช้ได้ทุกวัน"
            }
            isEye -> when {
                d in 20.0..45.0 -> "ดวงตาเด่น"   to "คอนทราสต์กำลังดี ช่วยขับตา"
                d in 45.0..60.0 -> "ลุคจัดชัด"   to "โทนชัด เหมาะกับแต่งเต็ม"
                else            -> "โทนอ่อน"     to "สุภาพ/ธรรมชาติ"
            }
            isBrow -> when {
                d <= 5   -> "กลืนผิว"   to "โทนคิ้วใกล้ธรรมชาติ"
                d <= 12  -> "ใกล้เคียง" to "อาจต้องปรับความเข้มเล็กน้อย"
                d <= 20  -> "พอใช้"     to "ควรเบลนด์เพื่อให้เนียน"
                else     -> "ต่างโทน"   to "อาจเข้มหรืออ่อนเกินไป"
            }
            else -> if (d <= 10) "ค่อนข้างใกล้" to "สีใกล้โทนผิว"
            else          "ต่างปานกลาง"  to "เหมาะใช้สร้างเลเยอร์/ไฮไลต์"
        }
    }
}

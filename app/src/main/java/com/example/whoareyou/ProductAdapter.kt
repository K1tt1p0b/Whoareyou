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

    private fun mapSuitability(productType: String?, deltaE00: Double?): Pair<String, String> {
        if (deltaE00 == null)
            return "ยังไม่มีข้อมูลสี" to "สินค้านี้ยังไม่มีข้อมูลเพียงพอสำหรับเทียบสีผิว"

        val d = deltaE00
        val t = (productType ?: "").lowercase()

        val isComplexion = listOf("foundation","concealer","bb","cc","tinted","powder","cushion","contour","bronzer","base")
            .any { t.contains(it) }
        val isLipOrBlush = listOf("lip","lipstick","gloss","oil","tint","stain","kit","liner","blush","cheek")
            .any { t.contains(it) }
        val isEye = listOf("eyeshadow","eye shadow","eyeliner","mascara")
            .any { t.contains(it) }
        val isBrow = listOf("brow","eyebrow")
            .any { t.contains(it) }

        return when {
            // 🎨 รองพื้น / คอนซีลเลอร์ / เบส
            isComplexion -> when {
                d <= 2  -> "ตรงกับผิวมาก" to "สีนี้ใกล้เคียงผิวจริงมาก ทาแล้วดูกลืนกับหน้า"
                d <= 4  -> "ใกล้สีผิว"    to "เฉดใกล้ผิว แนะนำลองทาบริเวณกรามเพื่อเช็กความพอดี"
                d <= 6  -> "พอใช้ได้"     to "สีอาจอ่อนหรือเข้มกว่าผิวเล็กน้อย ปรับได้ด้วยแป้งหรือคอนซีลเลอร์"
                else    -> "ไม่เข้ากับผิว" to "สีต่างจากผิวชัด อาจทำให้หน้าดูหมองหรือวอก"
            }

            // 💄 ลิป / บลัชออน
            isLipOrBlush -> when {
                d in 15.0..25.0 -> "เข้ากับผิว" to "ช่วยให้หน้าดูสดใส สุขภาพดี"
                d in 25.0..40.0 -> "ดูโดดเด่น"  to "สีจัดขึ้น เหมาะกับลุคแต่งหน้าเต็ม"
                else            -> "ดูสุภาพ"    to "สีอ่อนกำลังดี เหมาะกับลุคธรรมชาติ"
            }

            // 👁️ อายแชโดว์ / อายไลเนอร์
            isEye -> when {
                d in 20.0..45.0 -> "ขับดวงตา"   to "สีช่วยให้ตาดูเด่นขึ้นอย่างพอดี"
                d in 45.0..60.0 -> "ดูจัดชัด"   to "สีเข้ม เหมาะกับลุคแต่งเต็มหรือออกงาน"
                else            -> "ดูเบา ๆ"    to "สีอ่อน เหมาะกับลุคธรรมชาติทุกวัน"
            }

            // 🪞 คิ้ว
            isBrow -> when {
                d <= 5   -> "ธรรมชาติ"    to "สีคิ้วกลืนกับผิว ดูเป็นธรรมชาติ"
                d <= 12  -> "ใกล้เคียง"  to "สีใกล้เคียงกับผิวและสีผม"
                d <= 20  -> "พอใช้ได้"    to "อาจต้องเกลี่ยเพิ่มให้เนียนกับผิว"
                else     -> "ต่างจากผิว"  to "สีคิ้วอาจเข้มหรืออ่อนเกินไป"
            }

            // 🎨 อื่น ๆ
            else -> when {
                d <= 10 -> "ใกล้เคียง" to "สีใกล้โทนผิว ดูกลมกลืนดี"
                else    -> "ดูตัดกันสวย" to "สีต่างจากผิวเล็กน้อย ดูเด่นขึ้น"
            }
        }
    }
}

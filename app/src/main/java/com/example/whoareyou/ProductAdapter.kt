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
        val tvConfidence: TextView = v.findViewById(R.id.tvConfidence) // ต้องมีใน layout
        val tvBadges: TextView     = v.findViewById(R.id.tvBadges)     // ต้องมีใน layout

        init {
            v.setOnClickListener {
                @Suppress("DEPRECATION")
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

        h.tvPrice.text = it.bestPrice?.let { p ->
            NumberFormat.getNumberInstance(TH).format(p) + " บาท"
        } ?: it.priceTHB?.let { p ->
            NumberFormat.getNumberInstance(TH).format(p) + " บาท"
        } ?: "-"

        val conf = it.hybridConfidence ?: 0
        val level = it.confidenceLevel ?: "-"
        h.tvConfidence.text = "ความมั่นใจ ~ ${conf}% ($level)"

        h.tvBadges.text = it.badges?.joinToString(" · ") { b ->
            when (b.lowercase()) {
                "official" -> "Official"
                "admin-approved" -> "Admin Approved"
                else -> b.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }
            }
        } ?: ""

        // ✅ ต่อ BASE_URL ถ้าเป็น path
        val img = ApiConfig.fullUrl(it.imageURL)

        Glide.with(h.itemView)
            .load(img)
            .placeholder(R.drawable.logo)
            .error(R.drawable.logo)
            .into(h.iv)
    }
}

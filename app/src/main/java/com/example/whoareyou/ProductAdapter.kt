package com.example.project101

// ProductAdapter.kt
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load // import สำหรับ Coil
import com.kittipob.whoareyou.R

class ProductAdapter(
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit // Lambda สำหรับจัดการการคลิก
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    // คลาสที่เก็บ View ของสินค้าแต่ละชิ้น
    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.product_image)
        val priceTextView: TextView = itemView.findViewById(R.id.product_price)
        val nameTextView: TextView = itemView.findViewById(R.id.product_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        holder.priceTextView.text = "${product.price.toInt()} บาท"
        holder.nameTextView.text = product.name

        // ใช้ Coil โหลดรูปจาก URL
        holder.imageView.load(product.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background) // รูปขณะโหลด
            error(R.drawable.ic_launcher_foreground) // รูปเมื่อโหลดพลาด
        }

        // ตั้งค่า OnClickListener ให้กับ item ทั้งหมด
        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    // ฟังก์ชันสำหรับอัปเดตข้อมูลเมื่อมีการกรอง (Filter)
    fun updateData(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged() // บอก Adapter ให้วาดใหม่
    }
}
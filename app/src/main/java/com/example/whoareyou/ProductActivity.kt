package com.kittipob.whoareyou

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kittipob.whoareyou.ui.ProductAdapter
import com.kittipob.whoareyou.ui.ProductItem
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ProductActivity : AppCompatActivity() {

    private lateinit var BASE_URL: String

    private lateinit var budgetSpinner: Spinner
    private lateinit var styleSpinner: Spinner
    private lateinit var searchInput: EditText
    private lateinit var refreshButton: ImageButton

    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProductAdapter

    private var productsLoaded = false
    private var makeupLooks: List<MakeupLookItem> = emptyList()

    private val ALL_LABEL = "ทั้งหมด"

    private val authClient: OkHttpClient by lazy {
        val auth = Interceptor { chain ->
            val prefs = getSharedPreferences("UserSession", MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)
            val req = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(req)
        }
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class MakeupLookItem(val id: Int, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        BASE_URL = getString(R.string.root_url).trim().removeSuffix("/")

        budgetSpinner  = findViewById(R.id.budget_spinner)
        styleSpinner   = findViewById(R.id.style_spinner)
        searchInput    = findViewById(R.id.searchInput)
        refreshButton  = findViewById(R.id.refreshButton)
        progressBar    = findViewById(R.id.progressBar)
        emptyView      = findViewById(R.id.emptyView)
        recyclerView   = findViewById(R.id.product_recycler_view)

        adapter = ProductAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        adapter.onItemClick = { item ->
            val intent = Intent(this, ProductDetailActivity::class.java).apply {
                putExtra("PRODUCT_ID", item.id ?: -1)
                putExtra("FALLBACK_URL", item.productURL)
            }
            startActivity(intent)
        }

        val budgetList = resources.getStringArray(R.array.budget_options).toMutableList()
        if (budgetList.firstOrNull() != ALL_LABEL) budgetList.add(0, ALL_LABEL)
        val budgetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, budgetList)
        budgetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        budgetSpinner.adapter = budgetAdapter
        budgetSpinner.setSelection(0, false)

        setupStyleSpinner(emptyList())
        fetchMakeupLooksForSpinner()

        budgetSpinner.onItemSelectedListener = simpleListener { triggerFetch() }
        styleSpinner.onItemSelectedListener  = simpleListener { triggerFetch() }
        refreshButton.setOnClickListener { triggerFetch() }

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                triggerFetch()
                true
            } else false
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        triggerFetch()
    }

    private fun simpleListener(block: () -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block()
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

    private fun triggerFetch() {
        productsLoaded = false
        fetchRecommendedProducts()
    }

    private fun fetchMakeupLooksForSpinner() {
        val url = "$BASE_URL/ai/makeup_looks"
        val req = Request.Builder().url(url).get().build()

        authClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ProductActivity, "โหลดสไตล์ไม่ได้: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    setupStyleSpinner(emptyList())
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                val ct = response.header("Content-Type") ?: ""
                runOnUiThread {
                    try {
                        if (!response.isSuccessful || !ct.contains("application/json")) {
                            setupStyleSpinner(emptyList()); return@runOnUiThread
                        }
                        val looks = parseMakeupLooks(bodyStr)
                        makeupLooks = looks
                        setupStyleSpinner(looks.map { it.name })
                    } catch (_: Exception) {
                        setupStyleSpinner(emptyList())
                    }
                }
            }
        })
    }

    private fun setupStyleSpinner(names: List<String>) {
        val display = mutableListOf(ALL_LABEL).apply { addAll(names) }
        val ad = ArrayAdapter(this, android.R.layout.simple_spinner_item, display)
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        styleSpinner.adapter = ad
        styleSpinner.setSelection(0, false)
    }

    private fun parseMakeupLooks(body: String): List<MakeupLookItem> {
        fun mapArr(arr: JSONArray): List<MakeupLookItem> {
            val out = ArrayList<MakeupLookItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    MakeupLookItem(
                        id = when {
                            o.has("LookID") -> o.getInt("LookID")
                            o.has("id")     -> o.getInt("id")
                            else            -> o.optInt("lookID", -1)
                        },
                        name = o.optString("lookName", o.optString("name", o.optString("title", "Unnamed")))
                    )
                )
            }
            return out
        }
        val t = body.trim()
        if (t.startsWith("[")) return mapArr(JSONArray(t))
        val obj = JSONObject(t)
        val keys = listOf("looks", "data", "items", "results")
        for (k in keys) if (obj.has(k)) return mapArr(obj.getJSONArray(k))
        throw IllegalStateException("ไม่พบลิสต์สไตล์")
    }

    private fun fetchRecommendedProducts() {
        if (productsLoaded) return
        productsLoaded = true

        val skinToneRaw = intent.getStringExtra("EXTRA_SKIN_TONE")?.trim().orEmpty()
        val skinToneForApi = when (skinToneRaw) {
            "Fair", "Medium", "Deep", "All" -> skinToneRaw
            "โทนสว่าง" -> "Fair"
            "โทนกลาง"  -> "Medium"
            "โทนเข้ม"   -> "Deep"
            "Warm Tone", "Cool Tone", "Neutral Tone" -> "All"
            else -> "All"
        }

        val budget = budgetSpinner.selectedItem?.toString()
            ?.takeIf { it.isNotBlank() && it != ALL_LABEL }

        val styleName = styleSpinner.selectedItem?.toString()
            ?.takeIf { it.isNotBlank() && it != ALL_LABEL }

        val styleId  = makeupLooks.find { it.name == styleName }?.id
        val q        = searchInput.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }

        showLoading(true)

        val builder = Uri.parse("$BASE_URL/ai/cosmetics/recommendations").buildUpon()
            .appendQueryParameter("skinTone", skinToneForApi)
        if (budget != null) builder.appendQueryParameter("budget", budget)
        if (styleName != null) builder.appendQueryParameter("lookType", styleName)
        if (styleId != null && styleId >= 0) builder.appendQueryParameter("styleId", styleId.toString())
        if (q != null) builder.appendQueryParameter("q", q)

        val url = builder.build().toString()
        val req = Request.Builder().url(url).get().build()

        authClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    productsLoaded = false
                    showLoading(false)
                    Toast.makeText(this@ProductActivity, "โหลดสินค้าล้มเหลว: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    renderProducts(emptyList())
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                val ct = response.header("Content-Type") ?: ""
                runOnUiThread {
                    try {
                        showLoading(false)
                        if (!response.isSuccessful || !ct.contains("application/json")) {
                            productsLoaded = false
                            renderProducts(emptyList()); return@runOnUiThread
                        }
                        val items = parseProducts(bodyStr)
                        renderProducts(items)
                    } catch (e: Exception) {
                        productsLoaded = false
                        Toast.makeText(this@ProductActivity, "อ่านรายการสินค้าไม่ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                        renderProducts(emptyList())
                    }
                }
            }
        })
    }

    private fun parseProducts(body: String): List<ProductItem> {
        fun mapArr(arr: JSONArray): List<ProductItem> {
            val out = ArrayList<ProductItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)

                val priceRaw  = o.optDouble("Price", Double.NaN)

                val reasons = mutableListOf<String>()
                o.optJSONArray("reasons")?.let { a ->
                    for (k in 0 until a.length()) reasons.add(a.optString(k))
                }

                val badges = mutableListOf<String>()
                o.optJSONArray("badges")?.let { a ->
                    for (k in 0 until a.length()) badges.add(a.optString(k))
                }

                out.add(
                    ProductItem(
                        id            = if (o.has("CosmeticID")) o.optInt("CosmeticID") else null,
                        brandName     = o.optString("brandName", o.optString("brand", "")),
                        productName   = o.optString("Name",  o.optString("name", "")),

                        // ✅ เดิมเขียน category = o.optString("Type") ให้แก้เป็น:
                        type          = o.optString("Type", null),

                        // ใช้ Shade เดียวก็พอ
                        shadeName     = o.optString("Shade", null),

                        priceTHB      = priceRaw.takeIf { !it.isNaN() },
                        imageURL      = o.optString("ImageURL", null),
                        productURL    = o.optString("ProductLink", null),

                        hybridConfidence = if (o.has("hybrid_confidence")) o.optInt("hybrid_confidence") else null,
                        confidenceLevel  = o.optString("confidence_level", null),
                        deltaE00         = o.optDouble("deltaE00", Double.NaN).takeIf { !it.isNaN() },

                        badges        = badges,
                        reasons       = reasons
                    )
                )
            }
            return out
        }

        val t = body.trim()
        if (t.startsWith("[")) return mapArr(JSONArray(t))
        val obj = JSONObject(t)
        val keys = listOf("recommendedCosmetics", "data", "items", "results", "products")
        for (k in keys) if (obj.has(k)) return mapArr(obj.getJSONArray(k))
        throw IllegalStateException("ไม่พบลิสต์สินค้า")
    }

    private fun renderProducts(items: List<ProductItem>) {
        adapter.submitList(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}

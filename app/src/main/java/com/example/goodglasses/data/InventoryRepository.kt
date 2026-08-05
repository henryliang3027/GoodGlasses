package com.example.goodglasses.data

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class InventoryItem(val position: String, val outOfStock: List<String>)
data class ObbPoint(val x: Int, val y: Int)
data class ExpiryItem(
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val obb: List<ObbPoint>,
    val dateBbox: List<Int>?            // [x1, y1, x2, y2] 或 null
)

private const val EXPIRY_WARNING_MONTHS = 6L

/** 保存期限日期，若未偵測到日期則回傳 null */
fun ExpiryItem.expiryDate(): LocalDate? =
    if (year == 0 || month == 0 || day == 0) null
    else runCatching { LocalDate.of(year, month, day) }.getOrNull()

/** 距離現在的剩餘天數，若未偵測到日期則回傳 null */
fun ExpiryItem.remainingDays(): Long? =
    expiryDate()?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

/** 保存期限距離現在不足 6 個月，視為效期未合格 */
fun ExpiryItem.isExpiryFailing(): Boolean {
    val date = expiryDate() ?: return false
    return date.isBefore(LocalDate.now().plusMonths(EXPIRY_WARNING_MONTHS))
}

class InventoryRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private var serverHost = "192.168.0.102"
    private var serverPort = "8888"
    private val url: String
        get() = "http://$serverHost:$serverPort/check_out_of_stock"
    private val expiryUrl: String
        get() = "http://$serverHost:$serverPort/box_date_detection"

    fun setServerAddress(ip: String, port: String) {
        serverHost = ip
        serverPort = port
    }

    fun getServerAddress(): String = "$serverHost:$serverPort"

    suspend fun analyzeImage(bitmap: Bitmap): Result<List<InventoryItem>> = withContext(Dispatchers.IO) {
        try {
            val base64 = bitmapToBase64(bitmap)
            val payload = JSONObject().apply {
                put("image_base64", base64)
                put("question", "統計商品")
                put("mode", 2)
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            val status = json.get("status").toString()
            if (status != "1") return@withContext Result.failure(Exception("API returned status $status"))
            val items = parseItems(json.getJSONArray("data"))
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseItems(dataArray: JSONArray): List<InventoryItem> {
        val items = mutableListOf<InventoryItem>()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.getJSONObject(i)
            val position = obj.getString("position")
            val outOfStockArray = obj.getJSONArray("out_of_stock")
            val outOfStock = (0 until outOfStockArray.length()).map { outOfStockArray.getString(it) }
            if (outOfStock.isNotEmpty()) {
                items.add(InventoryItem(position = position, outOfStock = outOfStock))
            }
        }
        return items
    }

    suspend fun analyzeExpiry(bitmap: Bitmap): Result<List<ExpiryItem>> = withContext(Dispatchers.IO) {
        try {
            val base64 = bitmapToBase64(bitmap)
            val payload = JSONObject().apply { put("image_base64", base64) }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(expiryUrl).post(body).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            val status = json.get("status").toString()
            if (status != "1") return@withContext Result.failure(Exception("API returned status $status"))
            val items = parseExpiryItems(json.getJSONArray("data"))
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseExpiryItems(dataArray: JSONArray): List<ExpiryItem> {
        val items = mutableListOf<ExpiryItem>()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.getJSONObject(i)
            val name = obj.getString("name")
            val dateObj = obj.optJSONObject("date")
            val obbArray = obj.getJSONArray("obb")
            val obb = (0 until obbArray.length()).map { j ->
                val pt = obbArray.getJSONArray(j)
                ObbPoint(pt.getInt(0), pt.getInt(1))
            }
            val dateBboxArray = obj.optJSONArray("date_bbox")
            val dateBbox = dateBboxArray?.let { arr ->
                (0 until arr.length()).map { j -> arr.getInt(j) }
            }
            items.add(ExpiryItem(
                name = name,
                year = dateObj?.getInt("year") ?: 0,
                month = dateObj?.getInt("month") ?: 0,
                day = dateObj?.getInt("day") ?: 0,
                obb = obb,
                dateBbox = dateBbox
            ))
        }
        return items
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

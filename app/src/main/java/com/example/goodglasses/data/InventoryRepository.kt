package com.example.goodglasses.data

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
data class ExpiryItem(
    val name: String,                   // 紙箱類別名稱 (label)
    val bbox: List<Double>,             // 紙箱框 [x1, y1, x2, y2]，原圖座標
    val dateStr: String?,               // 日期字串，例如 "2027年12月22日"，若無則為 null
    val dateBbox: List<Double>?         // 日期印刷區框 [x1, y1, x2, y2]，原圖座標，若無則為 null
)

const val DEFAULT_EXPIRY_WARNING_MONTHS = 6L
private val DATE_STR_REGEX = Regex("""(\d+)年(\d+)月(\d+)日""")

/** 保存期限日期，若未偵測到日期或格式無法解析則回傳 null */
fun ExpiryItem.expiryDate(): LocalDate? {
    val match = dateStr?.let { DATE_STR_REGEX.find(it) } ?: return null
    val (year, month, day) = match.destructured
    return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/** 距離現在的剩餘天數，若未偵測到日期則回傳 null */
fun ExpiryItem.remainingDays(): Long? =
    expiryDate()?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

/** 保存期限距離現在不足 warningMonths 個月，視為效期未合格 */
fun ExpiryItem.isExpiryFailing(warningMonths: Long = DEFAULT_EXPIRY_WARNING_MONTHS): Boolean {
    val date = expiryDate() ?: return false
    return date.isBefore(LocalDate.now().plusMonths(warningMonths))
}

class InventoryRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private var serverHost = "192.168.51.80"
    private var serverPort = "5000"
    private val url: String
        get() = "http://$serverHost:$serverPort/check_out_of_stock"
    private val expiryUrl: String
        get() = "http://$serverHost:$serverPort/infer"

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
            // 高度過大時縮小再上傳，回傳的 bbox 座標再等比例放大回原圖尺寸
            val scaleFactor = if (bitmap.height >= 4000 || bitmap.width >= 4000) 2 else 1
            val uploadBitmap = if (scaleFactor > 1) {
                Bitmap.createScaledBitmap(bitmap, bitmap.width / scaleFactor, bitmap.height / scaleFactor, true)
            } else {
                bitmap
            }
            val jpegBytes = bitmapToJpegBytes(uploadBitmap)
            val imagePart = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg", imagePart)
                .build()
            val request = Request.Builder().url(expiryUrl).post(body).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            val items = parseExpiryItems(json.optJSONArray("boxes") ?: JSONArray(), scaleFactor)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseExpiryItems(boxesArray: JSONArray, scaleFactor: Int): List<ExpiryItem> {
        val items = mutableListOf<ExpiryItem>()
        for (i in 0 until boxesArray.length()) {
            val obj = boxesArray.getJSONObject(i)
            val name = obj.getString("label")
            val bbox = readBbox(obj.getJSONArray("bbox"), scaleFactor)
            val dateStr = if (obj.isNull("date_str")) null else obj.optString("date_str")
            val dateBbox = obj.optJSONArray("date_bbox")?.let { readBbox(it, scaleFactor) }
            items.add(ExpiryItem(
                name = name,
                bbox = bbox,
                dateStr = dateStr,
                dateBbox = dateBbox
            ))
        }
        return items
    }

    private fun readBbox(arr: JSONArray, scaleFactor: Int): List<Double> =
        (0 until arr.length()).map { j -> arr.getDouble(j) * scaleFactor }

    private fun bitmapToBase64(bitmap: Bitmap): String =
        Base64.encodeToString(bitmapToJpegBytes(bitmap), Base64.NO_WRAP)

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }
}

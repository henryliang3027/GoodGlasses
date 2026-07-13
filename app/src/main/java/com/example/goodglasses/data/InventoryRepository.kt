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

data class InventoryItem(val position: String, val outOfStock: List<String>)

data class ExpiryProduct(val brand: String?, val name: String?)

data class ExpiryDateInfo(val year: Int, val month: Int, val day: Int) {
    fun formatted(): String =
        "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}

data class ExpiryItem(
    val boxId: Int,
    val confidence: Double,
    val product: ExpiryProduct?,
    val expiryDate: ExpiryDateInfo?,
    val manufactureDate: ExpiryDateInfo?
)

class InventoryRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val url = "http://192.168.0.102:8888/check_out_of_stock"
    private val expiryUrl = "https://gillian-unhesitative-jestine.ngrok-free.dev/api/v1/detect"

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
            val payload = JSONObject().apply {
                put("image_base64", base64)
                put("include_image", false)
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(expiryUrl).post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API returned HTTP ${response.code}"))
            }
            val json = JSONObject(responseBody)
            val items = parseExpiryItems(json.optJSONArray("boxes") ?: JSONArray())
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseExpiryItems(boxesArray: JSONArray): List<ExpiryItem> {
        val items = mutableListOf<ExpiryItem>()
        for (i in 0 until boxesArray.length()) {
            val obj = boxesArray.getJSONObject(i)
            val boxId = obj.optInt("box_id", i + 1)
            val confidence = obj.optDouble("confidence", 0.0)
            val product = obj.optJSONObject("product")?.let { p ->
                ExpiryProduct(
                    brand = p.optString("brand").takeIf { it.isNotBlank() && it != "null" },
                    name = p.optString("name").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            items.add(
                ExpiryItem(
                    boxId = boxId,
                    confidence = confidence,
                    product = product,
                    expiryDate = obj.optJSONObject("expiry_date")?.toExpiryDateInfo(),
                    manufactureDate = obj.optJSONObject("manufacture_date")?.toExpiryDateInfo()
                )
            )
        }
        return items
    }

    private fun JSONObject.toExpiryDateInfo(): ExpiryDateInfo? {
        val year = optString("year").toIntOrNull() ?: return null
        val month = optString("month").toIntOrNull() ?: 0
        val day = optString("day").toIntOrNull() ?: 0
        return ExpiryDateInfo(year, month, day)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

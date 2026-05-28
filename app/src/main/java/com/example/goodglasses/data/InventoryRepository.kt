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
data class ExpiryItem(val name: String, val year: Int, val month: Int, val day: Int)

class InventoryRepository {

    private val client = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val url = "http://192.168.0.102:8888/check_out_of_stock"
    private val expiryUrl = "http://192.168.0.102:8888/box_date_detection"

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
            items.add(
                if (dateObj != null) ExpiryItem(
                    name = name,
                    year = dateObj.getInt("year"),
                    month = dateObj.getInt("month"),
                    day = dateObj.getInt("day")
                ) else ExpiryItem(name = name, year = 0, month = 0, day = 0)
            )
        }
        return items
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

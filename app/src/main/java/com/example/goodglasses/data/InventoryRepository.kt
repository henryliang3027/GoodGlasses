package com.example.goodglasses.data

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class InventoryItem(val name: String, val count: Int)

class InventoryRepository {

    private val client = OkHttpClient()
    private val url = "http://192.168.0.102:8888/inventory_base64"

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
            val status = json.getInt("status")
            if (status != 1) return@withContext Result.failure(Exception("API returned status $status"))
            val items = parseItems(json.getString("data"))
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseItems(data: String): List<InventoryItem> =
        data.split("\n").mapNotNull { line ->
            val parts = line.split(" 有 ")
            if (parts.size != 2) return@mapNotNull null
            val count = parts[1].replace(" 瓶", "").trim().toIntOrNull() ?: return@mapNotNull null
            InventoryItem(name = parts[0].trim(), count = count)
        }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

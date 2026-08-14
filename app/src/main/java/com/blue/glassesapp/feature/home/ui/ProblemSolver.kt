package com.blue.glassesapp.feature.home.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import com.blue.armobile.BuildConfig
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 拍照解题管理器 - 使用智谱 GLM-4V-Flash 多模态 API
 */
class ProblemSolver(
    private val contentResolver: ContentResolver
) {

    companion object {
        private const val TAG = "ProblemSolver"
        // ✅ API Key 由 local.properties 通过 BuildConfig 注入
        private val API_KEY = BuildConfig.GLM_API_KEY
        // ✅ 改这里：替换成智谱的 API 地址
        private const val API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        // ✅ 改这里：替换成智谱的模型名
        private const val MODEL = "glm-4v-plus"
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    interface Callback {
        fun onSuccess(result: String)
        fun onError(message: String)
    }

    fun solveFromUri(photoUri: Uri, callback: Callback) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
            solveFromBitmap(bitmap, callback)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取图片失败: ${e.message}")
            callback.onError("读取图片失败: ${e.message}")
        }
    }

    fun solveFromBitmap(bitmap: Bitmap, callback: Callback) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            callVisionAPI(base64Image, callback)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 图片处理失败: ${e.message}")
            callback.onError("图片处理失败: ${e.message}")
        }
    }

    private fun callVisionAPI(base64Image: String, callback: Callback) {
        Thread {
            try {
                val json = buildRequestJson(base64Image)

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.e(TAG, "✅ 智谱 API 响应成功")
                    val result = parseResponse(responseBody)
                    if (result != null && result.isNotEmpty()) {
                        callback.onSuccess(result)
                    } else {
                        callback.onError("未识别到题目，请重新拍照")
                    }
                } else {
                    Log.e(TAG, "❌ API 请求失败: $responseBody")
                    callback.onError("API 请求失败: $responseBody")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 智谱 API 调用异常: ${e.message}")
                callback.onError("解题失败: ${e.message}")
            }
        }.start()
    }

    private fun buildRequestJson(base64Image: String): String {
        Log.e(TAG, "📤 使用模型: $MODEL")
        return """
            {
                "model": "$MODEL",
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": "data:image/jpeg;base64,$base64Image"
                                }
                            },
                            {
                                "type": "text",
                                "text": "请识别图片中的题目并给出详细解答过程。如果是数学题，请分步骤讲解，最后给出答案。如果是语文题，请给出答案和解析。如果是英语题，请翻译并作答。如果图片中没有题目，请告诉用户没有识别到题目。"
                            }
                        ]
                    }
                ],
                "stream": false,
                "max_tokens": 1024
            }
        """.trimIndent()
    }

    private fun parseResponse(json: String): String? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                message?.get("content")?.asString
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析响应失败: ${e.message}")
            null
        }
    }
}
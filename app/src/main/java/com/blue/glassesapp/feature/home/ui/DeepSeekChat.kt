package com.blue.glassesapp.feature.home.ui

import android.util.Log
import com.blue.armobile.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DeepSeek AI 对话客户端
 * 专门处理与 DeepSeek API 的通信
 */
object DeepSeekChat {
    private const val TAG = "DeepSeekChat"

    // ============ 配置 ============
    // API Key 由 local.properties 通过 BuildConfig 注入
    private val API_KEY = BuildConfig.DEEPSEEK_API_KEY
    private const val BASE_URL = "https://api.deepseek.com/chat/completions"

    // ============ 网络客户端 ============
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 发送消息给 DeepSeek（完整对话）
     * @param userMessage 用户说的话
     * @param systemPrompt 系统提示词（可选）
     * @return AI 回复内容，失败返回 null
     */
    suspend fun sendMessage(
        userMessage: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): String? {
        Log.e(TAG, "🔥🔥🔥 sendMessage 被调用，消息: $userMessage")

        return try {
            // 1. 构建消息
            val messages = listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userMessage)
            )

            // 2. 构建请求体
            val body = mapOf(
                "model" to "deepseek-chat",
                "messages" to messages,
                "stream" to false,
                "temperature" to 0.7,
                "max_tokens" to 100
            )
            val jsonBody = gson.toJson(body)
            Log.e(TAG, "📤 请求体: $jsonBody")

            // 3. 创建请求
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            // 4. 发送请求（IO 线程）
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: ""
            Log.e(TAG, "📥 状态码: ${response.code}")
            Log.e(TAG, "📥 响应内容: $responseBody")

            // 5. 检查响应
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ API 错误: ${response.code}")
                return null
            }

            // 6. 解析回复
            val reply = parseResponse(responseBody)
            Log.e(TAG, "🤖 AI 回复: $reply")
            reply

        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络异常: ${e.message}", e)
            null
        }
    }

    /**
     * 发送简单消息给 DeepSeek（不带系统提示，用于提取目的地等简单任务）
     * @param userMessage 用户说的话
     * @return AI 回复内容，失败返回 null
     */
    suspend fun sendSimpleMessage(userMessage: String): String? {
        Log.e(TAG, "🔥🔥🔥 sendSimpleMessage 被调用，消息: $userMessage")
        return try {
            // 1. 构建消息（不带系统提示，让 AI 自由理解）
            val messages = listOf(
                mapOf("role" to "user", "content" to userMessage)
            )

            // 2. 构建请求体（温度更低，更确定性的回答）
            val body = mapOf(
                "model" to "deepseek-chat",
                "messages" to messages,
                "stream" to false,
                "temperature" to 0.3,
                "max_tokens" to 50
            )
            val jsonBody = gson.toJson(body)
            Log.e(TAG, "📤 请求体: $jsonBody")

            // 3. 创建请求
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            // 4. 发送请求（IO 线程）
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: ""
            Log.e(TAG, "📥 状态码: ${response.code}")
            Log.e(TAG, "📥 响应内容: $responseBody")

            // 5. 检查响应
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ API 错误: ${response.code}")
                return null
            }

            // 6. 解析回复
            val reply = parseResponse(responseBody)
            Log.e(TAG, "🤖 AI 回复: $reply")
            reply

        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络异常: ${e.message}", e)
            null
        }
    }

    /**
     * 解析 API 响应
     */
    private fun parseResponse(json: String): String {
        return try {
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                message?.get("content")?.asString ?: "回复内容为空"
            } else {
                "没有收到回复"
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 JSON 失败: ${e.message}")
            Log.e(TAG, "原始 JSON: $json")
            "解析失败，请重试"
        }
    }

    /**
     * 默认系统提示词
     */
    private const val DEFAULT_SYSTEM_PROMPT =
        "你是地平线智能眼镜的AI助手，名字叫地平线。" +
                "你亲切、友好，用中文回答。" +
                "你的回答要简洁、有趣，每次回答不超过50个字。"
}
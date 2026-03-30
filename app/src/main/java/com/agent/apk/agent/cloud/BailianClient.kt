// File: app/src/main/java/com/agent/apk/agent/cloud/BailianClient.kt
package com.agent.apk.agent.cloud

import android.util.Log
import com.agent.apk.model.VendorConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 阿里云百炼 LLM 客户端（支持原生 API 和 OpenAI 兼容模式）
 */
class BailianClient(
    private val config: VendorConfig,
    private val client: OkHttpClient = createOptimizedClient(),
    private val gson: Gson = Gson()
) : LlmClient {

    companion object {
        private const val TAG = "BailianClient"

        // 阿里云百炼原生 API endpoint
        private const val NATIVE_BASE_URL = "https://dashscope.aliyuncs.com/api/v1"
        // OpenAI 兼容模式 endpoint
        private const val OPENAI_COMPATIBLE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

        @Volatile
        private var sharedClient: OkHttpClient? = null

        private fun createOptimizedClient(): OkHttpClient {
            return sharedClient ?: synchronized(this) {
                sharedClient ?: OkHttpClient.Builder().apply {
                    connectionPool(okhttp3.ConnectionPool(
                        maxIdleConnections = 10,
                        keepAliveDuration = 5L,
                        TimeUnit.MINUTES
                    ))
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }.build().also { sharedClient = it }
            }
        }
    }

    private val baseUrl = if (config.baseUrl.contains("compatible-mode")) {
        config.baseUrl.removeSuffix("/")
    } else {
        OPENAI_COMPATIBLE_BASE_URL
    }

    /**
     * 测试 API 连通性
     */
    suspend fun testConnection(): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Testing API connection to: $baseUrl")
                val url = "$baseUrl/models"
                val httpRequest = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                Log.d(TAG, "Sending request to: $url")
                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "Response code: ${response.code}, body length: ${responseBody.length}")

                if (response.isSuccessful) {
                    val models = parseAvailableModels(responseBody)
                    Log.d(TAG, "Available models: ${models.joinToString(", ")}")
                    ConnectionTestResult(
                        success = true,
                        message = "API 连接成功",
                        availableModels = models
                    )
                } else {
                    Log.e(TAG, "API error: ${response.code} - $responseBody")
                    ConnectionTestResult(
                        success = false,
                        message = "API 返回错误：${response.code} - $responseBody",
                        errorCode = response.code.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed: ${e.message}", e)
                ConnectionTestResult(
                    success = false,
                    message = "连接失败：${e.message}",
                    errorCode = "NETWORK_ERROR"
                )
            }
        }
    }

    /**
     * 获取可用模型列表
     */
    suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching available models from: $baseUrl")
                val url = "$baseUrl/models"
                val httpRequest = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val models = parseAvailableModels(responseBody)
                    Log.d(TAG, "Fetched ${models.size} models: ${models.joinToString(", ")}")
                    models
                } else {
                    Log.w(TAG, "Failed to fetch models, using defaults")
                    config.models
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available models: ${e.message}", e)
                config.models
            }
        }
    }

    /**
     * 检查指定模型是否可用
     */
    suspend fun isModelAvailable(modelName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking model availability: $modelName")
                val testRequest = ChatRequest(
                    model = modelName,
                    messages = listOf(Message("user", "Hello")),
                    maxTokens = 10
                )
                val response = chatCompletion(testRequest)
                val available = response.choices.isNotEmpty()
                Log.d(TAG, "Model $modelName available: $available")
                available
            } catch (e: Exception) {
                Log.e(TAG, "Model $modelName check failed: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun chatCompletion(request: ChatRequest): ChatResponse {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/chat/completions"
            Log.d(TAG, "Sending chat completion request to: $url, model: ${request.model}")

            val requestBody = gson.toJson(
                ChatRequestSerializer().serialize(request)
            )

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
                ?: throw ApiException("Empty response body")

            Log.d(TAG, "Chat completion response: ${response.code}, body length: ${responseBody.length}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat completion failed: ${response.code} - $responseBody")
                throw ApiException("Request failed: ${response.code} - $responseBody")
            }

            gson.fromJson(responseBody, ChatResponse::class.java)
        }
    }

    override suspend fun streamChatCompletion(request: ChatRequest, onToken: (String) -> Unit): String {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/chat/completions"
            Log.d(TAG, "Sending streaming chat completion request to: $url, model: ${request.model}")

            val requestBody = gson.toJson(
                ChatRequestSerializer().serialize(request).toMutableMap().apply {
                    this["stream"] = true
                }
            )

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val stringBuilder = StringBuilder()

            try {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Streaming chat failed: ${response.code} - $errorBody")
                    throw ApiException("Request failed: ${response.code} - $errorBody")
                }

                val inputStream = response.body?.byteStream()
                    ?: throw ApiException("Empty response body")

                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") return@forEach

                            try {
                                val json = gson.fromJson(data, com.google.gson.JsonObject::class.java)
                                val choices = json.getAsJsonArray("choices")
                                if (choices != null && choices.size() > 0) {
                                    val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                    val content = delta.get("content")?.asString
                                    if (content != null) {
                                        stringBuilder.append(content)
                                        withContext(Dispatchers.Main) {
                                            onToken(content)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse streaming chunk", e)
                            }
                        }
                    }
                }

                val fullContent = stringBuilder.toString()
                Log.d(TAG, "Streaming completed, total content: ${fullContent.length} chars")
                fullContent
            } finally {
                response.close()
            }
        }
    }

    override suspend fun chatWithVision(request: VisionChatRequest): VisionChatResponse {
        if (!config.supportsVision) {
            Log.e(TAG, "Vision not supported for vendor: ${config.name}")
            throw ApiException("Vendor ${config.name} does not support vision")
        }

        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/chat/completions"
            Log.d(TAG, "Sending vision chat request to: $url, model: ${request.model}")

            val requestBody = gson.toJson(
                VisionChatRequestSerializer().serialize(request)
            )

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
                ?: throw ApiException("Empty response body")

            Log.d(TAG, "Vision chat response: ${response.code}, body length: ${responseBody.length}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Vision chat failed: ${response.code} - $responseBody")
                throw ApiException("Request failed: ${response.code} - $responseBody")
            }

            gson.fromJson(responseBody, VisionChatResponse::class.java)
        }
    }

    private fun parseAvailableModels(responseBody: String): List<String> {
        return try {
            val json = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
            val data = json.getAsJsonArray("data")
            data?.map { model ->
                model.asJsonObject.get("id")?.asString ?: ""
            }?.filter { it.isNotEmpty() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse available models: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * API 连接测试结果
 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val availableModels: List<String> = emptyList(),
    val errorCode: String? = null
)

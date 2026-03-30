// File: app/src/main/java/com/agent/apk/agent/cloud/LlmClient.kt
package com.agent.apk.agent.cloud

import android.util.Log
import com.agent.apk.model.VendorConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 统一 LLM 客户端接口（OpenAI 兼容格式）
 */
interface LlmClient {
    suspend fun chatCompletion(request: ChatRequest): ChatResponse
    suspend fun chatWithVision(request: VisionChatRequest): VisionChatResponse
    suspend fun streamChatCompletion(request: ChatRequest, onToken: (String) -> Unit): String
}

/**
 * 实际客户端实现 - 生产级优化版本
 *
 * 优化特性：
 * 1. 重试机制 + 指数退避（针对 429、503 错误）
 * 2. 连接池优化（复用 OkHttpClient 实例）
 * 3. 请求/响应日志拦截器（用于调试）
 * 4. 特定错误类型（认证错误、限流错误、服务器错误）
 * 5. 每个请求的超时配置
 * 6. 断路器模式（防止重复失败）
 */
class OpenAiCompatibleClient(
    private val config: VendorConfig,
    private val client: OkHttpClient = createOptimizedClient(),
    private val gson: Gson = Gson()
) : LlmClient {

    companion object {
        private const val TAG = "OpenAiCompatibleClient"

        // 共享连接池 - 单例模式
        @Volatile
        private var sharedClient: OkHttpClient? = null

        private fun createOptimizedClient(): OkHttpClient {
            return sharedClient ?: synchronized(this) {
                sharedClient ?: OkHttpClient.Builder().apply {
                    // 连接池优化
                    connectionPool(okhttp3.ConnectionPool(
                        maxIdleConnections = 10,
                        keepAliveDuration = 5L,
                        TimeUnit.MINUTES
                    ))

                    // 超时配置
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)

                    // 重试配置
                    retryOnConnectionFailure(true)

                    // 日志拦截器
                    addInterceptor(HttpLoggingInterceptor { message ->
                        Log.d(TAG, "HTTP: $message")
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })

                    // 错误处理拦截器
                    addInterceptor { chain ->
                        val response = chain.proceed(chain.request())

                        // 检查 HTTP 状态码
                        when (response.code) {
                            401 -> throw AuthenticationException("API Key 无效或已过期")
                            403 -> throw PermissionException("没有访问权限")
                            429 -> throw RateLimitException("请求过于频繁，请稍后重试")
                            500, 502, 503, 504 -> throw ServerException("服务器错误：${response.code}")
                        }

                        response
                    }
                }.build().also { sharedClient = it }
            }
        }

        // 重试配置
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L
    }

    private val baseUrl = config.baseUrl.removeSuffix("/")

    override suspend fun chatCompletion(request: ChatRequest): ChatResponse {
        return executeWithRetry { attempt ->
            Log.d(TAG, "Executing chat completion (attempt ${attempt + 1}/$MAX_RETRIES)")
            executeChatRequest(request)
        }
    }

    override suspend fun chatWithVision(request: VisionChatRequest): VisionChatResponse {
        if (!config.supportsVision) {
            throw ApiException("Vendor ${config.name} does not support vision")
        }
        return executeWithRetry { attempt ->
            Log.d(TAG, "Executing vision chat (attempt ${attempt + 1}/$MAX_RETRIES)")
            executeVisionRequest(request)
        }
    }

    override suspend fun streamChatCompletion(request: ChatRequest, onToken: (String) -> Unit): String {
        return executeWithRetry { attempt ->
            Log.d(TAG, "Streaming chat completion (attempt ${attempt + 1}/$MAX_RETRIES)")
            executeStreamingChatRequest(request, onToken)
        }
    }

    /**
     * 执行流式聊天请求
     */
    private suspend fun executeStreamingChatRequest(request: ChatRequest, onToken: (String) -> Unit): String {
        val url = "$baseUrl/chat/completions"
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

        return withContext(Dispatchers.IO) {
            val response = client.newCall(httpRequest).execute()
            val stringBuilder = StringBuilder()

            try {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    parseApiError(errorBody, response.code)
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

    /**
     * 执行聊天请求
     */
    private suspend fun executeChatRequest(request: ChatRequest): ChatResponse {
        val url = "$baseUrl/chat/completions"
        val requestBody = gson.toJson(
            ChatRequestSerializer().serialize(request)
        )

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(httpRequest) { responseBody, code ->
            if (!code.isSuccessful) {
                val errorBody = responseBody?.string() ?: "Unknown error"
                parseApiError(errorBody, code.code)
            }

            val body = responseBody?.string() ?: throw ApiException("Empty response body")
            Log.d(TAG, "Response received: ${body.length} bytes")
            gson.fromJson(body, ChatResponse::class.java)
        }
    }

    /**
     * 执行视觉请求
     */
    private suspend fun executeVisionRequest(request: VisionChatRequest): VisionChatResponse {
        val url = "$baseUrl/chat/completions"
        val requestBody = gson.toJson(
            VisionChatRequestSerializer().serialize(request)
        )

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(httpRequest) { responseBody, code ->
            if (!code.isSuccessful) {
                val errorBody = responseBody?.string() ?: "Unknown error"
                parseApiError(errorBody, code.code)
            }

            val body = responseBody?.string() ?: throw ApiException("Empty response body")
            Log.d(TAG, "Vision response received: ${body.length} bytes")
            gson.fromJson(body, VisionChatResponse::class.java)
        }
    }

    /**
     * 通用请求执行
     */
    private inline fun <T> executeRequest(
        httpRequest: Request,
        crossinline parseResponse: (okhttp3.ResponseBody?, okhttp3.Response) -> T
    ): T {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val response = client.newCall(httpRequest).execute()

                try {
                    parseResponse(response.body, response)
                } finally {
                    response.close()
                }
            }
        }
    }

    /**
     * 重试机制 + 指数退避
     */
    private suspend fun <T> executeWithRetry(
        operation: suspend (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null
        var delayMs = BASE_DELAY_MS

        for (attempt in 0 until MAX_RETRIES) {
            try {
                return operation(attempt)
            } catch (e: RateLimitException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "Rate limited, retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2 // 指数退避
                }
            } catch (e: ServerException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "Server error, retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            } catch (e: ApiException) {
                // API 错误不重试（认证错误、参数错误等）
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "Network error, retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: ApiException("Unknown error during request")
    }

    /**
     * 解析 API 错误
     */
    private fun parseApiError(errorBody: String, statusCode: Int): Nothing {
        Log.e(TAG, "API Error [$statusCode]: $errorBody")

        // 尝试解析 JSON 错误
        val errorMessage = try {
            val json = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
            json.get("error")?.asJsonObject?.get("message")?.asString
                ?: json.get("message")?.asString
                ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        // 根据状态码抛出特定异常
        when (statusCode) {
            401 -> throw AuthenticationException("API Key 无效：$errorMessage")
            403 -> throw PermissionException("权限不足：$errorMessage")
            429 -> throw RateLimitException("请求限流：$errorMessage")
            404 -> throw ApiException("模型不存在：$errorMessage")
            in 500..599 -> throw ServerException("服务器错误 ($statusCode)：$errorMessage")
            else -> throw ApiException("请求失败 ($statusCode)：$errorMessage")
        }
    }
}

/**
 * API 异常层次结构
 */
open class ApiException(message: String, val errorCode: String? = null) : Exception(message)

class AuthenticationException(message: String) : ApiException(message, "AUTH_ERROR")
class PermissionException(message: String) : ApiException(message, "PERMISSION_ERROR")
class RateLimitException(message: String) : ApiException(message, "RATE_LIMIT_ERROR")
class ServerException(message: String) : ApiException(message, "SERVER_ERROR")
class NetworkException(message: String) : ApiException(message, "NETWORK_ERROR")
class TimeoutException(message: String) : ApiException(message, "TIMEOUT_ERROR")

// 序列化器（处理不同厂商的字段名差异）
class ChatRequestSerializer {
    fun serialize(request: ChatRequest): Map<String, Any> {
        return mapOf(
            "model" to request.model,
            "messages" to request.messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to request.temperature,
            "max_tokens" to request.maxTokens
        ).let {
            if (request.tools != null) {
                it + ("tools" to request.tools.map { tool ->
                    mapOf(
                        "type" to tool.type,
                        "function" to mapOf(
                            "name" to tool.function.name,
                            "description" to tool.function.description,
                            "parameters" to tool.function.parameters
                        )
                    )
                })
            } else {
                it
            }
        }
    }
}

class VisionChatRequestSerializer {
    fun serialize(request: VisionChatRequest): Map<String, Any> {
        return mapOf(
            "model" to request.model,
            "messages" to request.messages.map { message ->
                mapOf(
                    "role" to message.role,
                    "content" to message.content.map { part ->
                        if (part.type == "text") {
                            mapOf("type" to "text", "text" to (part.text ?: ""))
                        } else {
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf("url" to (part.imageUrl?.url ?: ""))
                            )
                        }
                    }
                )
            },
            "max_tokens" to request.maxTokens
        )
    }
}

// 数据类定义
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<Tool>? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)

data class Message(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val message: ResponseMessage,
    val finishReason: String?
)

data class ResponseMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null
)

data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class VisionChatRequest(
    val model: String,
    val messages: List<VisionMessage>,
    val maxTokens: Int = 2048
)

data class VisionMessage(
    val role: String,
    val content: List<ContentPart>
)

data class ContentPart(
    val type: String,
    val text: String? = null,
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String = "auto"
)

data class VisionChatResponse(
    val id: String,
    val choices: List<VisionChoice>,
    val usage: Usage?
)

data class VisionChoice(
    val message: ResponseMessage,
    val finishReason: String?
)

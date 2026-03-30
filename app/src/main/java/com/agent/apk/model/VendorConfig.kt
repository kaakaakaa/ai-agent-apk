// File: app/src/main/java/com/agent/apk/model/VendorConfig.kt
package com.agent.apk.model

import kotlinx.serialization.Serializable

/**
 * 大模型厂商配置
 */
@Serializable
data class VendorConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,  // 加密存储
    val models: List<String>,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val isActive: Boolean = false,
    val selectedModel: String? = null
) {
    companion object {
        /**
         * 预定义的厂商配置模板
         */
        fun aliyunBailian(apiKey: String): VendorConfig = VendorConfig(
            id = "aliyun-bailian",
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = apiKey,
            models = listOf("qwen-max", "qwen-plus", "qwen-turbo"),
            supportsVision = true,
            supportsTools = true,
            selectedModel = "qwen-max"
        )

        fun deepseek(apiKey: String): VendorConfig = VendorConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = apiKey,
            models = listOf("deepseek-chat", "deepseek-coder"),
            supportsVision = false,
            supportsTools = true,
            selectedModel = "deepseek-chat"
        )

        fun kimi(apiKey: String): VendorConfig = VendorConfig(
            id = "kimi",
            name = "Kimi 月之暗面",
            baseUrl = "https://api.moonshot.cn/v1",
            apiKey = apiKey,
            models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
            supportsVision = true,
            supportsTools = true,
            selectedModel = "moonshot-v1-128k"
        )

        fun dashscope(apiKey: String): VendorConfig = VendorConfig(
            id = "dashscope",
            name = "通义千问 DashScope",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = apiKey,
            models = listOf("qwen-max", "qwen-plus"),
            supportsVision = true,
            supportsTools = true,
            selectedModel = "qwen-max"
        )

        fun azureOpenAI(apiKey: String, endpoint: String): VendorConfig = VendorConfig(
            id = "azure-openai",
            name = "Azure OpenAI",
            baseUrl = endpoint,
            apiKey = apiKey,
            models = listOf("gpt-4o", "gpt-4-turbo"),
            supportsVision = true,
            supportsTools = true,
            selectedModel = "gpt-4o"
        )

        /**
         * 根据 ID 获取厂商配置
         */
        fun getVendorConfigById(id: String, apiKey: String): VendorConfig {
            return when (id) {
                "aliyun-bailian" -> aliyunBailian(apiKey)
                "deepseek" -> deepseek(apiKey)
                "kimi" -> kimi(apiKey)
                "dashscope" -> dashscope(apiKey)
                "azure-openai" -> azureOpenAI(apiKey, "")  // 需要单独设置 endpoint
                else -> aliyunBailian(apiKey)  // 默认使用阿里云百炼
            }
        }
    }
}

/**
 * 完整的 API 配置
 */
@Serializable
data class ApiConfig(
    val llmVendor: String,
    val sttVendor: String,
    val ttsVendor: String,
    val vendorConfigs: Map<String, VendorConfig>
)

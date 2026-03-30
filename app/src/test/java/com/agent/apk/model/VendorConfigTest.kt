// File: app/src/test/java/com/agent/apk/model/VendorConfigTest.kt
package com.agent.apk.model

import org.junit.Test
import org.junit.Assert.*

class VendorConfigTest {

    @Test
    fun `aliyun bailian template creates correct config`() {
        val config = VendorConfig.aliyunBailian("test-api-key")

        assertEquals("aliyun-bailian", config.id)
        assertEquals("阿里云百炼", config.name)
        assertTrue(config.baseUrl.contains("dashscope.aliyuncs.com"))
        assertEquals("test-api-key", config.apiKey)
        assertTrue(config.supportsVision)
        assertTrue(config.supportsTools)
        assertEquals("qwen-max", config.selectedModel)
    }

    @Test
    fun `deepseek template creates correct config`() {
        val config = VendorConfig.deepseek("test-api-key")

        assertEquals("deepseek", config.id)
        assertEquals("DeepSeek", config.name)
        assertTrue(config.baseUrl.contains("deepseek.com"))
        assertFalse(config.supportsVision)
        assertTrue(config.supportsTools)
    }

    @Test
    fun `kimi template creates correct config`() {
        val config = VendorConfig.kimi("test-api-key")

        assertEquals("kimi", config.id)
        assertEquals("Kimi 月之暗面", config.name)
        assertTrue(config.baseUrl.contains("moonshot.cn"))
        assertTrue(config.supportsVision)
        assertTrue(config.models.contains("moonshot-v1-128k"))
    }
}

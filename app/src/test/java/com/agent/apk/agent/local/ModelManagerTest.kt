// File: app/src/test/java/com/agent/apk/agent/local/ModelManagerTest.kt
package com.agent.apk.agent.local

import org.junit.Test
import org.junit.Assert.*

class ModelManagerTest {

    @Test
    fun `selectModelForDevice returns best available model for high-end device`() {
        // 高配设备：8GB RAM, 128GB 存储
        val models = listOf(
            LocalModel(
                id = "gemma-2b",
                name = "Gemma 2B",
                assetPath = "llm/models/gemma-2b-int4.bin",
                minRam = 6L * 1024 * 1024 * 1024,
                minStorage = 2L * 1024 * 1024 * 1024,
                description = "Google Gemma 2B"
            ),
            LocalModel(
                id = "qwen-0.5b",
                name = "Qwen 0.5B",
                assetPath = "llm/models/qwen-0.5b-int4.bin",
                minRam = 3L * 1024 * 1024 * 1024,
                minStorage = 1L * 1024 * 1024 * 1024,
                description = "Qwen 0.5B"
            )
        )

        val totalRam = 8L * 1024 * 1024 * 1024
        val storage = 128L * 1024 * 1024 * 1024

        val selected = models
            .filter { totalRam >= it.minRam && storage >= it.minStorage }
            .maxByOrNull { it.minRam }

        assertEquals("gemma-2b", selected?.id)
    }

    @Test
    fun `selectModelForDevice returns low-end model for low-end device`() {
        // 低配设备：4GB RAM, 32GB 存储
        val models = listOf(
            LocalModel(
                id = "gemma-2b",
                name = "Gemma 2B",
                assetPath = "llm/models/gemma-2b-int4.bin",
                minRam = 6L * 1024 * 1024 * 1024,
                minStorage = 2L * 1024 * 1024 * 1024,
                description = "Google Gemma 2B"
            ),
            LocalModel(
                id = "qwen-0.5b",
                name = "Qwen 0.5B",
                assetPath = "llm/models/qwen-0.5b-int4.bin",
                minRam = 3L * 1024 * 1024 * 1024,
                minStorage = 1L * 1024 * 1024 * 1024,
                description = "Qwen 0.5B"
            )
        )

        val totalRam = 4L * 1024 * 1024 * 1024
        val storage = 32L * 1024 * 1024 * 1024

        val selected = models
            .filter { totalRam >= it.minRam && storage >= it.minStorage }
            .maxByOrNull { it.minRam }

        assertEquals("qwen-0.5b", selected?.id)
    }

    @Test
    fun `selectModelForDevice returns null when no model fits`() {
        // 超低配设备：2GB RAM
        val models = listOf(
            LocalModel(
                id = "qwen-0.5b",
                name = "Qwen 0.5B",
                assetPath = "llm/models/qwen-0.5b-int4.bin",
                minRam = 3L * 1024 * 1024 * 1024,
                minStorage = 1L * 1024 * 1024 * 1024,
                description = "Qwen 0.5B"
            )
        )

        val totalRam = 2L * 1024 * 1024 * 1024
        val storage = 16L * 1024 * 1024 * 1024

        val selected = models
            .filter { totalRam >= it.minRam && storage >= it.minStorage }
            .maxByOrNull { it.minRam }

        assertNull(selected)
    }
}

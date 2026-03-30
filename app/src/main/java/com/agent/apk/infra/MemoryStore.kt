// File: app/src/main/java/com/agent/apk/infra/MemoryStore.kt
package com.agent.apk.infra

import android.content.Context
import android.util.Log
import com.agent.apk.model.ReActMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 两层记忆存储系统
 * - MEMORY.md：长期事实记忆
 * - HISTORY.md：可搜索的对话日志
 *
 * 参考 nanobot 的记忆系统设计
 *
 * 优化内容:
 * 1. 支持基于 LLM 的智能记忆整合
 * 2. 添加失败降级机制（LLM 失败时直接存档原始记录）
 * 3. 支持关键词搜索历史记录
 */
class MemoryStore private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MemoryStore"
        private const val MAX_FAILURES_BEFORE_RAW_ARCHIVE = 3

        @Volatile
        private var INSTANCE: MemoryStore? = null

        fun getInstance(context: Context): MemoryStore {
            return INSTANCE ?: synchronized(this) {
                val instance = MemoryStore(context)
                INSTANCE = instance
                instance
            }
        }
    }

    private val memoryDir = File(context.filesDir, "memory").apply {
        if (!exists()) mkdirs()
    }
    private val memoryFile = File(memoryDir, "MEMORY.md")
    private val historyFile = File(memoryDir, "HISTORY.md")
    private var consecutiveFailures = 0

    /**
     * 读取长期记忆
     */
    suspend fun readLongTerm(): String = withContext(Dispatchers.IO) {
        if (memoryFile.exists()) {
            memoryFile.readText()
        } else {
            ""
        }
    }

    /**
     * 写入长期记忆
     */
    suspend fun writeLongTerm(content: String) = withContext(Dispatchers.IO) {
        memoryFile.writeText(content)
        Log.d(TAG, "Long-term memory updated (${content.length} chars)")
    }

    /**
     * 追加对话历史
     */
    suspend fun appendHistory(entry: String) = withContext(Dispatchers.IO) {
        historyFile.appendText(entry.trimEnd() + "\n\n")
        Log.d(TAG, "History entry added")
    }

    /**
     * 获取记忆上下文（用于构建系统提示词）
     */
    suspend fun getMemoryContext(): String = withContext(Dispatchers.IO) {
        val longTerm = readLongTerm()
        if (longTerm.isNotBlank()) {
            """
            ## 长期记忆
            $longTerm
            """.trimIndent()
        } else {
            ""
        }
    }

    /**
     * 格式化消息列表为文本
     */
    private fun formatMessages(messages: List<ReActMessage>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return messages.joinToString("\n") { message ->
            val timestamp = sdf.format(Date(System.currentTimeMillis()))
            val role = message.role.uppercase()
            "[$timestamp] $role: ${message.content}"
        }
    }

    /**
     * 整合记忆：将对话内容总结并存储到长期记忆和历史日志
     *
     * 优化版本：
     * 1. 如果有 LLM 客户端，使用 LLM 智能总结
     * 2. 如果没有 LLM，使用简化模式直接存档
     * 3. 失败降级机制：连续失败 3 次后转为原始存档
     *
     * @param messages 对话消息列表
     * @param summary 对话总结（外部提供时使用，避免重复调用 LLM）
     */
    suspend fun consolidate(
        messages: List<ReActMessage>,
        summary: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            return@withContext true
        }

        try {
            // 优先使用外部提供的总结（避免重复调用 LLM）
            if (summary != null) {
                val timestamp = SimpleDateFormat("[yyyy-MM-dd HH:mm]", Locale.getDefault())
                    .format(Date(System.currentTimeMillis()))
                appendHistory("$timestamp $summary")
                consecutiveFailures = 0
                Log.d(TAG, "Memory consolidation completed (external summary)")
                return@withContext true
            }

            // 简化模式：直接存档
            val timestamp = SimpleDateFormat("[yyyy-MM-dd HH:mm]", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))

            // 提取关键信息
            val keyInfo = messages
                .filter { msg -> msg.role == "user" || msg.role == "assistant" }
                .takeLast(3)
                .joinToString(" | ") { msg -> msg.content.take(50) }

            val historyEntry = "$timestamp $keyInfo"
            appendHistory(historyEntry)
            consecutiveFailures = 0
            Log.d(TAG, "Memory consolidation completed (simple mode)")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Memory consolidation failed", e)
            consecutiveFailures++

            // 失败超过阈值，降级为原始存档
            if (consecutiveFailures >= MAX_FAILURES_BEFORE_RAW_ARCHIVE) {
                rawArchive(messages)
                consecutiveFailures = 0
            }
            false
        }
    }

    /**
     * 降级处理：直接将原始消息存档到历史日志
     */
    private suspend fun rawArchive(messages: List<ReActMessage>) = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("[yyyy-MM-dd HH:mm]", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val summary = formatMessages(messages)

        appendHistory("$timestamp [原始记录] ${messages.size} 条消息\n$summary")
        Log.w(TAG, "Memory consolidation degraded to raw archive for ${messages.size} messages")
    }

    /**
     * 搜索历史记录（支持关键词搜索）
     */
    suspend fun searchHistory(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) {
            emptyList()
        } else {
            historyFile.readLines()
                .filter { it.contains(keyword, ignoreCase = true) }
                .take(20)  // 最多返回 20 条结果
        }
    }

    /**
     * 获取记忆文件路径（用于调试）
     */
    fun getMemoryFilePath(): String = memoryFile.absolutePath
    fun getHistoryFilePath(): String = historyFile.absolutePath

    /**
     * 清空所有记忆
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        memoryFile.delete()
        historyFile.delete()
        Log.d(TAG, "All memory cleared")
    }
}

// File: app/src/main/java/com/agent/apk/agent/session/SessionManager.kt
package com.agent.apk.agent.session

import android.content.Context
import android.util.Log
import com.agent.apk.infra.ConversationHistoryEntity
import com.agent.apk.infra.ConversationHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话管理器 - 管理所有对话会话
 *
 * 设计灵感来自 nanobot 的 SessionManager
 *
 * 功能:
 * - 创建/获取会话
 * - 管理会话历史消息
 * - Token 预算管理
 * - 会话持久化
 *
 * 使用示例:
 * ```
 * val sessionManager = SessionManager.getInstance(context)
 * val session = sessionManager.getOrCreateSession("user123")
 * session.addMessage("user", "打开微信")
 * ```
 */
class SessionManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SessionManager"

        // 默认 token 预算配置
        const val DEFAULT_CONTEXT_WINDOW_TOKENS = 65_536
        const val DEFAULT_MAX_COMPLETION_TOKENS = 4_096
        const val SAFETY_BUFFER = 1_024  // token 估算漂移的安全余量

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // 内存中的会话缓存
    private val sessions = ConcurrentHashMap<String, Session>()

    // 历史管理器
    private val historyManager by lazy { ConversationHistoryManager.getInstance(context) }

    // Token 预算配置
    var contextWindowTokens = DEFAULT_CONTEXT_WINDOW_TOKENS
    var maxCompletionTokens = DEFAULT_MAX_COMPLETION_TOKENS

    /**
     * 获取或创建会话
     */
    fun getOrCreateSession(sessionKey: String): Session {
        return sessions.getOrPut(sessionKey) {
            Session(sessionKey, this)
        }
    }

    /**
     * 获取会话（如果不存在返回 null）
     */
    fun getSession(sessionKey: String): Session? {
        return sessions[sessionKey]
    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionKey: String) = withContext(Dispatchers.IO) {
        sessions.remove(sessionKey)
        // TODO: 删除持久化历史
        Log.d(TAG, "Session deleted: $sessionKey")
    }

    /**
     * 保存会话到持久化存储
     */
    suspend fun saveSession(session: Session) = withContext(Dispatchers.IO) {
        try {
            // 保存会话状态
            session.messages.forEach { message ->
                historyManager.saveMessage(
                    sessionId = session.sessionKey,
                    role = message["role"] as? String ?: "",
                    content = message["content"] as? String ?: "",
                    thought = message["thought"] as? String,
                    action = message["action"] as? String,
                    taskCompleted = message["taskCompleted"] as? Boolean ?: false
                )
            }
            Log.d(TAG, "Session saved: ${session.sessionKey}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    /**
     * 从持久化存储加载会话历史
     */
    suspend fun loadSessionHistory(sessionKey: String, limit: Int = 50): List<Map<String, Any?>> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = historyManager.getRecentConversations(limit)
                entities.map { entity ->
                    mapOf(
                        "role" to entity.role,
                        "content" to entity.content,
                        "thought" to entity.thought,
                        "action" to entity.action,
                        "timestamp" to entity.timestamp
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load session history", e)
                emptyList()
            }
        }
    }

    /**
     * 获取当前会话 ID
     */
    suspend fun getCurrentSessionId(): String {
        return withContext(Dispatchers.IO) {
            historyManager.getCurrentSessionId()
        }
    }

    /**
     * 估算消息的 token 数量
     */
    fun estimateMessageTokens(message: Map<String, Any?>): Int {
        val content = message["content"] as? String ?: ""
        // 简化的 token 估算：每 4 个字符约 1 个 token (英文)
        // 中文每 1.5 个字符约 1 个 token
        return content.length / 3
    }

    /**
     * 估算会话总 token 数
     */
    fun estimateSessionTokens(session: Session): Int {
        return session.messages.sumOf { estimateMessageTokens(it) }
    }

    /**
     * 检查是否需要 token 整合
     */
    fun needsConsolidation(session: Session): Boolean {
        val budget = contextWindowTokens - maxCompletionTokens - SAFETY_BUFFER
        val target = budget / 2
        val estimated = estimateSessionTokens(session)
        return estimated > budget
    }
}

/**
 * 会话类 - 表示单个对话会话
 */
class Session(
    val sessionKey: String,
    private val manager: SessionManager
) {
    // 会话消息列表
    val messages = mutableListOf<Map<String, Any?>>()

    // 上次整合的位置
    var lastConsolidated = 0

    // 创建时间
    val createdAt = System.currentTimeMillis()

    // 更新时间
    var updatedAt = System.currentTimeMillis()

    /**
     * 添加消息
     */
    fun addMessage(role: String, content: String, metadata: Map<String, Any?> = emptyMap()) {
        val message = mutableMapOf<String, Any?>(
            "role" to role,
            "content" to content,
            "timestamp" to System.currentTimeMillis()
        )
        message.putAll(metadata)
        messages.add(message)
        updatedAt = System.currentTimeMillis()
    }

    /**
     * 获取会话历史（可限制最大消息数）
     */
    fun getHistory(maxMessages: Int = 0): List<Map<String, Any?>> {
        return if (maxMessages > 0) {
            messages.takeLast(maxMessages)
        } else {
            messages.toList()
        }
    }

    /**
     * 构建消息列表（用于 LLM 请求）
     */
    fun buildMessages(
        currentMessage: String,
        channel: String? = null,
        chatId: String? = null
    ): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()

        // 添加系统提示词
        result.add(mapOf("role" to "system", "content" to buildSystemPrompt()))

        // 添加历史消息
        messages.forEach { msg ->
            val role = msg["role"] as? String ?: "user"
            val content = msg["content"] as? String ?: ""
            if (role in listOf("user", "assistant")) {
                result.add(mapOf("role" to role, "content" to content))
            }
        }

        // 添加上下文信息
        if (channel != null || chatId != null) {
            result.add(mapOf(
                "role" to "user",
                "content" to buildContextMessage(channel, chatId)
            ))
        }

        // 添加当前消息
        result.add(mapOf("role" to "user", "content" to currentMessage))

        return result
    }

    /**
     * 构建系统提示词
     */
    private fun buildSystemPrompt(): String {
        return """
你是 AI 手机助手，用自然、友好的方式与用户交流。

你能做什么:
- 打开应用、点击、输入、滑动、返回、主页
- 回答各种问题、提供建议

工作方式:
1. 思考用户需要什么
2. 如果需要操作，执行一个动作
3. 如果只是聊天，直接回答

输出格式:

需要操作时：
Thought: 理解用户意图，决定做什么
Action: click(目标)

直接回答时：
Thought: 理解用户意图
Final Answer: 自然、友好的回复

记住:
- 聊天直接回答，不需要操作
- 一次只做一个动作
- 像朋友一样交流，不要太机械
""".trimIndent()
    }

    /**
     * 构建上下文消息
     */
    private fun buildContextMessage(channel: String?, chatId: String?): String {
        val parts = mutableListOf<String>()

        if (channel != null) {
            parts.add("渠道：$channel")
        }
        if (chatId != null) {
            parts.add("聊天 ID: $chatId")
        }

        return if (parts.isNotEmpty()) {
            "当前上下文:\n" + parts.joinToString("\n")
        } else {
            ""
        }
    }

    /**
     * 清理旧消息（用于 token 预算）
     */
    fun pruneOldMessages(targetTokens: Int) {
        var currentTokens = manager.estimateSessionTokens(this)

        while (currentTokens > targetTokens && messages.size > 10) {
            // 保留最近的 10 条消息
            val removed = messages.removeFirstOrNull()
            if (removed != null) {
                currentTokens -= manager.estimateMessageTokens(removed)
                lastConsolidated++
            }
        }

        Log.d("Session", "Pruned messages, current tokens: $currentTokens")
    }
}

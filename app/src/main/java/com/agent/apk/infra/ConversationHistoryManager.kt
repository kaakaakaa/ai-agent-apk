// File: app/src/main/java/com/agent/apk/infra/ConversationHistoryManager.kt
package com.agent.apk.infra

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 全局对话历史管理器 - 持久化存储和恢复对话历史
 *
 * 功能：
 * 1. 保存每次对话到本地数据库
 * 2. 应用启动时自动恢复最近的对话历史
 * 3. 支持按会话查询历史
 * 4. 支持清理过期数据
 */
class ConversationHistoryManager private constructor(
    context: Context
) {
    private val database = ConversationHistoryDatabase.getInstance(context)
    private val dao = database.conversationHistoryDao()

    // 当前会话 ID
    private var currentSessionId: String = generateSessionId()

    // 内存缓存，减少数据库查询
    private val conversationCache = mutableListOf<ConversationHistoryEntity>()
    private val cacheMutex = Mutex()

    companion object {
        private const val TAG = "ConversationHistoryMgr"
        private const val MAX_CACHE_SIZE = 100

        @Volatile
        private var INSTANCE: ConversationHistoryManager? = null

        fun getInstance(context: Context): ConversationHistoryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ConversationHistoryManager(context)
                INSTANCE = instance
                instance
            }
        }

        private fun generateSessionId(): String {
            return "session_${System.currentTimeMillis()}"
        }
    }

    /**
     * 开始新的会话
     */
    fun startNewSession(): String {
        currentSessionId = generateSessionId()
        Log.d(TAG, "New session started: $currentSessionId")
        return currentSessionId
    }

    /**
     * 获取当前会话 ID
     */
    fun getCurrentSessionId(): String = currentSessionId

    /**
     * 保存用户消息
     */
    suspend fun saveUserMessage(content: String) {
        saveMessage(
            sessionId = currentSessionId,
            role = "user",
            content = content,
            thought = null,
            action = null,
            taskCompleted = false
        )
    }

    /**
     * 保存 AI 回复（包含思考过程和动作）
     */
    suspend fun saveAssistantMessage(
        content: String,
        thought: String? = null,
        action: String? = null,
        taskCompleted: Boolean = false
    ) {
        saveMessage(
            sessionId = currentSessionId,
            role = "assistant",
            content = content,
            thought = thought,
            action = action,
            taskCompleted = taskCompleted
        )
    }

    /**
     * 保存系统消息
     */
    suspend fun saveSystemMessage(content: String) {
        saveMessage(
            sessionId = currentSessionId,
            role = "system",
            content = content,
            thought = null,
            action = null,
            taskCompleted = false
        )
    }

    /**
     * 保存消息（通用方法）
     */
    suspend fun saveMessage(
        sessionId: String,
        role: String,
        content: String,
        thought: String?,
        action: String?,
        taskCompleted: Boolean
    ) {
        val entity = ConversationHistoryEntity(
            sessionId = sessionId,
            role = role,
            content = content,
            thought = thought,
            action = action,
            taskCompleted = taskCompleted
        )

        try {
            dao.insert(entity)
            Log.d(TAG, "Message saved: role=$role, completed=$taskCompleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
        }
    }

    /**
     * 保存消息到缓存
     */
    private suspend fun saveMessageToCache(
        sessionId: String,
        role: String,
        content: String,
        thought: String?,
        action: String?,
        taskCompleted: Boolean
    ) {
        saveMessage(
            sessionId = sessionId,
            role = role,
            content = content,
            thought = thought,
            action = action,
            taskCompleted = taskCompleted
        )

        // 更新缓存
        val entity = ConversationHistoryEntity(
            sessionId = sessionId,
            role = role,
            content = content,
            thought = thought,
            action = action,
            taskCompleted = taskCompleted,
            timestamp = System.currentTimeMillis()
        )
        cacheMutex.withLock {
            conversationCache.add(entity)
            if (conversationCache.size > MAX_CACHE_SIZE) {
                conversationCache.removeAt(0)
            }
        }
    }

    /**
     * 获取当前会话的所有历史
     */
    suspend fun getCurrentSessionHistory(): List<ConversationHistoryEntity> {
        return getConversationBySession(currentSessionId)
    }

    /**
     * 按会话 ID 获取历史
     */
    suspend fun getConversationBySession(sessionId: String): List<ConversationHistoryEntity> {
        return try {
            dao.getConversationBySession(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversation", e)
            emptyList()
        }
    }

    /**
     * 获取最近的对话（用于恢复）
     */
    suspend fun getRecentConversations(limit: Int = 50): List<ConversationHistoryEntity> {
        return try {
            dao.getRecentConversations(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent conversations", e)
            emptyList()
        }
    }

    /**
     * 获取所有会话 ID 列表
     */
    suspend fun getAllSessionIds(): List<String> {
        return try {
            dao.getAllSessionIds()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session ids", e)
            emptyList()
        }
    }

    /**
     * 删除指定会话
     */
    suspend fun deleteSession(sessionId: String) {
        try {
            dao.deleteBySession(sessionId)
            cacheMutex.withLock {
                conversationCache.removeAll { it.sessionId == sessionId }
            }
            Log.d(TAG, "Session deleted: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session", e)
        }
    }

    /**
     * 清空所有历史
     */
    suspend fun deleteAllHistory() {
        try {
            dao.deleteAll()
            cacheMutex.withLock {
                conversationCache.clear()
            }
            Log.d(TAG, "All history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all history", e)
        }
    }

    /**
     * 以 Flow 形式监听会话历史变化
     */
    fun watchSession(sessionId: String): Flow<List<ConversationHistoryEntity>> {
        return dao.getConversationBySessionFlow(sessionId)
    }
}

// File: app/src/main/java/com/agent/apk/model/ReActSession.kt
package com.agent.apk.model

import kotlinx.serialization.Serializable

/**
 * ReAct 模式的会话状态
 */
data class ReActSession(
    val taskId: String,
    val userGoal: String,
    val conversationHistory: MutableList<ReActMessage> = mutableListOf(),
    val executedActions: MutableList<ActionRecord> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    var status: SessionStatus = SessionStatus.ACTIVE
) {
    /**
     * 检测是否无限循环（同一操作重复执行且都失败）
     */
    fun isInfiniteLoop(threshold: Int = 5): Boolean {
        if (executedActions.size < threshold) return false

        // 检查最近的动作是否都失败且相同
        val recentActions = executedActions.takeLast(threshold)
        val allSameAction = recentActions.all { it.action == recentActions.first().action }
        val allFailed = recentActions.all { !it.result.success }

        // 只有当动作相同且都失败时才认为是无限循环
        return allSameAction && allFailed
    }

    /**
     * 获取当前会话的总步数
     */
    fun stepCount(): Int = conversationHistory.size
}

@Serializable
data class ReActMessage(
    val role: String,  // "user", "assistant", "observation"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActionRecord(
    val action: Action,
    var result: ActionResult,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ActionResult(
    val success: Boolean,
    val message: String,
    val newUiTree: UiTree? = null
)

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
    TIMEOUT
}

/**
 * ReAct 单步执行结果
 */
data class ReActResult(
    val thought: String,
    val action: Action?,         // null 表示任务完成
    val finalAnswer: String?,    // 任务完成时返回
    val isComplete: Boolean
)

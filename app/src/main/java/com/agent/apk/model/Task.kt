// File: app/src/main/java/com/agent/apk/model/Task.kt
package com.agent.apk.model

import com.agent.apk.agent.AgentTarget

data class Task(
    val id: String,
    val userInstruction: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: TaskStatus = TaskStatus.PENDING,
    val assignedModel: AgentTarget = AgentTarget.AUTO
) {
    /**
     * 判断是否是简单指令
     * 简单指令：打开应用、基础导航、简单点击、系统操作
     */
    fun isSimpleCommand(): Boolean {
        val simplePatterns = listOf(
            // 打开应用
            Regex("打开.*"),
            Regex("启动.*"),
            Regex("进入.*"),
            // 基础导航
            Regex("^返回$"),
            Regex("^主页$"),
            Regex("^首页$"),
            Regex("^最近任务$"),
            // 简单点击
            Regex("点击.*按钮"),
            Regex("点.*一下"),
            // 系统操作
            Regex("调高.*音量"),
            Regex("调低.*音量"),
            Regex("打开.*蓝牙"),
            Regex("关闭.*蓝牙"),
            Regex("打开.*WiFi"),
            Regex("关闭.*WiFi")
        )
        return simplePatterns.any { it.matches(userInstruction) }
    }
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}


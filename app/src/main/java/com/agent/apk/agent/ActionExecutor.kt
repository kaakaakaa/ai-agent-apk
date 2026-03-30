// File: app/src/main/java/com/agent/apk/agent/ActionExecutor.kt
package com.agent.apk.agent

/**
 * 动作执行器接口
 */
interface ActionExecutor {
    suspend fun click(x: Int, y: Int): Boolean
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Boolean
    suspend fun type(text: String): Boolean
    suspend fun openApp(packageName: String): Boolean
    suspend fun navigateBack(): Boolean
    suspend fun goHome(): Boolean
    suspend fun openRecentApps(): Boolean
    suspend fun scroll(nodeId: String, direction: String): Boolean
    suspend fun takeScreenshot(): ByteArray?
}

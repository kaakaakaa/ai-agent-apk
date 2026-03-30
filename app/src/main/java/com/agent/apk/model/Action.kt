// File: app/src/main/java/com/agent/apk/model/Action.kt
package com.agent.apk.model

import kotlinx.serialization.Serializable

/**
 * Agent 可以执行的操作
 */
@Serializable
sealed class Action {
    abstract val target: String?
    abstract val reason: String
}

@Serializable
data class ClickAction(
    override val target: String,
    override val reason: String,
    val nodeId: String? = null,      // AccessibilityNodeInfo 的 ID
    val bounds: Bounds? = null       // 点击坐标（可选）
) : Action()

@Serializable
data class SwipeAction(
    override val target: String,
    override val reason: String,
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int,
    val durationMs: Long = 300
) : Action()

@Serializable
data class TypeAction(
    override val target: String,
    override val reason: String,
    val text: String,
    val nodeId: String? = null
) : Action()

@Serializable
data class OpenAppAction(
    override val target: String,
    override val reason: String,
    val packageName: String
) : Action()

@Serializable
data class NavigateAction(
    override val target: String,
    override val reason: String
) : Action() {
    companion object {
        fun back(): NavigateAction = NavigateAction("back", "用户请求返回")
        fun home(): NavigateAction = NavigateAction("home", "用户请求回到主页")
        fun recentApps(): NavigateAction = NavigateAction("recent", "用户请求查看最近任务")
    }
}

@Serializable
data class ScreenshotAction(
    override val target: String = "screen",
    override val reason: String
) : Action()

@Serializable
data class ScrollAction(
    override val target: String,
    override val reason: String,
    val direction: ScrollDirection,
    val nodeId: String? = null
) : Action()

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

@Serializable
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2
}

/**
 * 未知动作（用于反序列化失败时的降级）
 */
@Serializable
data class UnknownAction(
    override val target: String = "unknown",
    override val reason: String = "Unknown action type"
) : Action()

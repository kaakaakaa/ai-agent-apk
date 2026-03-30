// File: app/src/main/java/com/agent/apk/model/UiNode.kt
package com.agent.apk.model

import kotlinx.serialization.Serializable

/**
 * 屏幕 UI 树节点
 */
@Serializable
data class UiNode(
    val id: String,
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val bounds: Bounds,
    val actions: List<String>,
    val parent: String?,
    val children: List<String>,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val isChecked: Boolean? = null,
    val isSelected: Boolean = false
) {
    /**
     * 获取节点的简洁描述，用于发送给 LLM
     */
    fun toDescription(): String {
        val attrs = mutableListOf(className)
        text?.takeIf { it.isNotBlank() }?.let { attrs.add("text=\"$it\"") }
        contentDescription?.takeIf { it.isNotBlank() }?.let {
            attrs.add("contentDesc=\"$it\"")
        }
        if (!isEnabled) attrs.add("disabled")
        if (isChecked == true) attrs.add("checked")
        if (isSelected) attrs.add("selected")
        return attrs.joinToString(" ")
    }
}

/**
 * 完整的屏幕 UI 树
 */
@Serializable
data class UiTree(
    val screenWidth: Int,
    val screenHeight: Int,
    val packageName: String,
    val nodes: List<UiNode>,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 压缩为 JSON 字符串，用于发送给 LLM
     * 优化：添加更清晰的元素描述和可点击状态
     */
    fun toJson(): String {
        val visibleNodes = nodes.filter { it.isVisible && it.isEnabled }
        return buildString {
            appendLine("当前屏幕信息：")
            appendLine("- 设备尺寸：${screenWidth}x${screenHeight}")
            appendLine("- 应用包名：$packageName")
            appendLine("- 可见元素数量：${visibleNodes.size}")
            appendLine()
            appendLine("可交互元素列表（按从上到下排序）：")
            visibleNodes.sortedBy { it.bounds.top }.forEachIndexed { index, node ->
                val isClickable = node.actions.contains("ACTION_CLICK") ||
                                  node.actions.contains("32") // ACTION_CLICK 的整数值
                if (isClickable) {
                    appendLine("${index + 1}. [可点击] ${node.className}")
                    node.text?.takeIf { it.isNotBlank() }?.let { appendLine("   文字：\"$it\"") }
                    node.contentDescription?.takeIf { it.isNotBlank() }?.let { appendLine("   描述：\"$it\"") }
                    appendLine("   位置：[${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}]")
                    appendLine("   元素 ID: ${node.id}")
                }
            }
        }
    }

    private fun String.escapeJson(): String {
        return this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .take(100) // 限制长度
    }
}

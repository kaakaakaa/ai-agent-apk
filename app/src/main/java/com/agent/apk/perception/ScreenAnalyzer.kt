// File: app/src/main/java/com/agent/apk/perception/ScreenAnalyzer.kt
package com.agent.apk.perception

import android.graphics.Bitmap
import com.agent.apk.model.UiTree

/**
 * 屏幕分析器：分析屏幕内容，提取关键信息
 *
 * 用于：
 * - 压缩 UI 树，过滤无关节点
 * - 识别屏幕类型（列表、表单、对话框等）
 * - 提取关键操作元素
 */
class ScreenAnalyzer {

    /**
     * 压缩 UI 树，过滤不可见和不重要的节点
     */
    fun compressUiTree(uiTree: UiTree): UiTree {
        val visibleNodes = uiTree.nodes.filter { node ->
            node.isVisible && node.isEnabled
        }

        return UiTree(
            screenWidth = uiTree.screenWidth,
            screenHeight = uiTree.screenHeight,
            packageName = uiTree.packageName,
            nodes = visibleNodes
        )
    }

    /**
     * 识别屏幕类型
     */
    fun identifyScreenType(uiTree: UiTree): ScreenType {
        val classNames = uiTree.nodes.mapNotNull { it.className }

        return when {
            classNames.any { it.contains("AdapterView", ignoreCase = true) } ->
                ScreenType.LIST

            classNames.any { it.contains("EditText", ignoreCase = true) } ->
                ScreenType.FORM

            classNames.any { it.contains("AlertDialog", ignoreCase = true) } ->
                ScreenType.DIALOG

            classNames.any { it.contains("ViewPager", ignoreCase = true) } ->
                ScreenType.PAGER

            else -> ScreenType.NORMAL
        }
    }

    /**
     * 查找可点击的元素
     */
    fun findClickableNodes(uiTree: UiTree): List<ClickableNode> {
        return uiTree.nodes
            .filter { node ->
                node.isVisible && node.isEnabled
            }
            .mapNotNull { node ->
                val bounds = node.bounds
                val centerX = (bounds.left + bounds.right) / 2
                val centerY = (bounds.top + bounds.bottom) / 2

                ClickableNode(
                    id = node.id,
                    text = node.text ?: node.contentDescription,
                    className = node.className,
                    centerX = centerX,
                    centerY = centerY,
                    width = bounds.right - bounds.left,
                    height = bounds.bottom - bounds.top
                )
            }
    }

    /**
     * 查找输入框
     */
    fun findEditableNodes(uiTree: UiTree): List<EditableNode> {
        return uiTree.nodes
            .filter { node ->
                node.isVisible && node.isEnabled &&
                        (node.className?.contains("EditText", ignoreCase = true) == true ||
                         node.className?.contains("Edit", ignoreCase = true) == true)
            }
            .map { node ->
                val bounds = node.bounds
                EditableNode(
                    id = node.id,
                    text = node.text,
                    hint = node.contentDescription,
                    centerX = (bounds.left + bounds.right) / 2,
                    centerY = (bounds.top + bounds.bottom) / 2
                )
            }
    }

    /**
     * 生成屏幕内容摘要（用于发送给 LLM）
     */
    fun generateSummary(uiTree: UiTree): String {
        val screenType = identifyScreenType(uiTree)
        val clickableNodes = findClickableNodes(uiTree)
        val editableNodes = findEditableNodes(uiTree)

        return buildString {
            appendLine("屏幕类型：${screenType.name}")
            appendLine("应用包名：${uiTree.packageName}")
            appendLine("屏幕尺寸：${uiTree.screenWidth}x${uiTree.screenHeight}")
            appendLine()
            appendLine("可点击元素 (${clickableNodes.size}):")
            clickableNodes.forEachIndexed { index, node ->
                appendLine("  ${index + 1}. [${node.className}] ${node.text ?: "无文本"} @ (${node.centerX}, ${node.centerY})")
            }
            if (editableNodes.isNotEmpty()) {
                appendLine()
                appendLine("输入框 (${editableNodes.size}):")
                editableNodes.forEachIndexed { index, node ->
                    appendLine("  ${index + 1}. [${node.hint ?: "无提示"}] ${node.text ?: "空"}")
                }
            }
        }
    }
}

/**
 * 屏幕类型
 */
enum class ScreenType {
    NORMAL,     // 普通屏幕
    LIST,       // 列表
    FORM,       // 表单
    DIALOG,     // 对话框
    PAGER       // 分页器
}

/**
 * 可点击节点
 */
data class ClickableNode(
    val id: String,
    val text: String?,
    val className: String,
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int
)

/**
 * 可编辑节点
 */
data class EditableNode(
    val id: String,
    val text: String?,
    val hint: String?,
    val centerX: Int,
    val centerY: Int
)

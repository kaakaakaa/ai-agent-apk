// File: app/src/main/java/com/agent/apk/agent/tools/ActionTools.kt
package com.agent.apk.agent.tools

import android.view.accessibility.AccessibilityNodeInfo
import com.agent.apk.model.*
import com.agent.apk.perception.AccessibilityScanner

/**
 * 判断节点是否可编辑
 */
private fun AccessibilityNodeInfo.isEditable(): Boolean {
    return className?.toString()?.let {
        it.contains("EditText", ignoreCase = true) ||
                it.contains("Edit", ignoreCase = true)
    } ?: false
}

/**
 * 判断 UiNode 是否可编辑
 */
private fun UiNode.isEditable(): Boolean {
    return className.let {
        it.contains("EditText", ignoreCase = true) ||
                it.contains("Edit", ignoreCase = true)
    }
}

/**
 * 点击工具
 */
class ClickTool(
    private val scanner: AccessibilityScanner
) : AgentTool<ClickParams>() {

    override val name = "click"
    override val description = "点击屏幕上的元素"

    override fun getParameters(): List<ToolParameter> {
        return listOf(
            ToolParameter("target", "string", "要点击的元素文本或描述", required = true),
            ToolParameter("nodeId", "string", "元素的 accessibility node ID（可选，优先级更高）", required = false)
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val target = args["target"] as? String ?: return ToolResult.failure("缺少参数：target")
        val nodeId = args["nodeId"] as? String

        // 优先使用 nodeId 点击
        if (nodeId != null) {
            val node = scanner.findNodeById(nodeId)
            if (node != null) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return if (success) {
                    ToolResult.success(mapOf("target" to target, "method" to "nodeId"))
                } else {
                    ToolResult.failure("点击失败")
                }
            }
        }

        // 降级：根据 target 查找元素
        val uiTree = AccessibilityScanner.getUiTreeSync()
        if (uiTree != null) {
            val node = findNodeByTarget(uiTree, target)
            if (node != null) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return if (success) {
                    ToolResult.success(mapOf("target" to target, "method" to "textMatch"))
                } else {
                    ToolResult.failure("点击失败")
                }
            }
        }

        return ToolResult.failure("未找到元素：$target")
    }

    private fun findNodeByTarget(uiTree: UiTree, target: String): AccessibilityNodeInfo? {
        val visibleNodes = uiTree.nodes.filter { it.isVisible && it.isEnabled }

        // 精确匹配 text
        val exactTextMatch = visibleNodes.find { it.text?.equals(target, ignoreCase = true) == true }
        if (exactTextMatch != null) {
            return scanner.findNodeById(exactTextMatch.id)
        }

        // 精确匹配 desc
        val exactDescMatch = visibleNodes.find { it.contentDescription?.equals(target, ignoreCase = true) == true }
        if (exactDescMatch != null) {
            return scanner.findNodeById(exactDescMatch.id)
        }

        // 模糊匹配
        val containsMatch = visibleNodes.find {
            (it.text?.contains(target, ignoreCase = true) == true) ||
            (it.contentDescription?.contains(target, ignoreCase = true) == true)
        }
        if (containsMatch != null) {
            return scanner.findNodeById(containsMatch.id)
        }

        return null
    }
}

/**
 * 输入文本工具
 */
class TypeTool(
    private val scanner: AccessibilityScanner
) : AgentTool<TypeParams>() {

    override val name = "type"
    override val description = "在输入框中输入文本"

    override fun getParameters(): List<ToolParameter> {
        return listOf(
            ToolParameter("text", "string", "要输入的文本", required = true),
            ToolParameter("nodeId", "string", "输入框的 accessibility node ID（可选）", required = false)
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String ?: return ToolResult.failure("缺少参数：text")
        val nodeId = args["nodeId"] as? String

        // 优先使用 nodeId 定位
        if (nodeId != null) {
            val node = scanner.findNodeById(nodeId)
            if (node != null && node.isEditable) {
                val arguments = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                return ToolResult.success(mapOf("text" to text, "method" to "nodeId"))
            }
        }

        // 降级：查找最后一个可编辑的输入框
        val uiTree = AccessibilityScanner.getUiTreeSync()
        if (uiTree != null) {
            val editableNode = uiTree.nodes.find { node ->
                // 使用 isEditable 函数检查是否为可编辑输入框
                node.isEditable() && node.isEnabled
            }
            if (editableNode != null) {
                val node = scanner.findNodeById(editableNode.id)
                if (node != null) {
                    val arguments = android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text
                        )
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    return ToolResult.success(mapOf("text" to text, "method" to "autoFind"))
                }
            }
        }

        return ToolResult.failure("未找到输入框")
    }
}

/**
 * 滑动工具
 */
class SwipeTool : AgentTool<SwipeParams>() {

    override val name = "swipe"
    override val description = "在屏幕上滑动手势"

    override fun getParameters(): List<ToolParameter> {
        return listOf(
            ToolParameter("fromX", "number", "起始 X 坐标", required = true),
            ToolParameter("fromY", "number", "起始 Y 坐标", required = true),
            ToolParameter("toX", "number", "结束 X 坐标", required = true),
            ToolParameter("toY", "number", "结束 Y 坐标", required = true),
            ToolParameter("durationMs", "number", "滑动持续时间（毫秒）", required = false, defaultValue = 300)
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val fromX = (args["fromX"] as? Number)?.toInt() ?: return ToolResult.failure("缺少参数：fromX")
        val fromY = (args["fromY"] as? Number)?.toInt() ?: return ToolResult.failure("缺少参数：fromY")
        val toX = (args["toX"] as? Number)?.toInt() ?: return ToolResult.failure("缺少参数：toX")
        val toY = (args["toY"] as? Number)?.toInt() ?: return ToolResult.failure("缺少参数：toY")
        val durationMs = (args["durationMs"] as? Number)?.toLong() ?: 300L

        // 使用 AccessibilityScanner 中的 GesturePerformer 执行滑动手势
        val scanner = com.agent.apk.perception.AccessibilityScanner.instance
        if (scanner == null) {
            return ToolResult.failure("AccessibilityService 未启用")
        }

        // 创建滑动手势路径
        val path = android.graphics.Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }

        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val success = scanner.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {}
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {}
        }, null)
        return if (success) {
            ToolResult.success(mapOf(
                "from" to "$fromX,$fromY",
                "to" to "$toX,$toY"
            ))
        } else {
            ToolResult.failure("滑动手势执行失败")
        }
    }
}

/**
 * 打开应用工具
 */
class OpenAppTool : AgentTool<OpenAppParams>() {

    override val name = "openApp"
    override val description = "打开指定的应用程序"

    override fun getParameters(): List<ToolParameter> {
        return listOf(
            ToolParameter("packageName", "string", "应用包名", required = true)
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val packageName = args["packageName"] as? String ?: return ToolResult.failure("缺少参数：packageName")

        try {
            val context = com.agent.apk.AgentApplication.instance
                ?: return ToolResult.failure("无法获取应用上下文")

            val intent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_MAIN
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.success(mapOf("packageName" to packageName))
        } catch (e: Exception) {
            return ToolResult.failure("打开应用失败：${e.message}")
        }
    }
}

/**
 * 导航工具
 */
class NavigateTool(
    private val scanner: AccessibilityScanner
) : AgentTool<NavigateParams>() {

    override val name = "navigate"
    override val description = "执行系统导航操作（返回、主页、最近任务）"

    override fun getParameters(): List<ToolParameter> {
        return listOf(
            ToolParameter("action", "string", "导航动作：back, home, recent", required = true)
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String ?: return ToolResult.failure("缺少参数：action")

        val success = when (action.lowercase()) {
            "back" -> scanner.performGlobalAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            "home" -> scanner.performGlobalAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            "recent" -> scanner.performGlobalAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            else -> return ToolResult.failure("未知的导航动作：$action")
        }

        // 注意：上述全局动作实际不生效，需要实际的 GlobalActionExecutor
        // 这里保留接口，实际实现需要依赖 AccessibilityService 的全局动作支持
        return ToolResult.failure("导航功能需要额外的全局动作支持")
    }
}

// 参数类定义
class ClickParams : ToolParams()
class TypeParams : ToolParams()
class SwipeParams : ToolParams()
class OpenAppParams : ToolParams()
class NavigateParams : ToolParams()

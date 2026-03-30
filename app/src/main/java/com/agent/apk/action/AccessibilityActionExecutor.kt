// File: app/src/main/java/com/agent/apk/action/AccessibilityActionExecutor.kt
package com.agent.apk.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.apk.agent.ActionExecutor
import com.agent.apk.perception.AccessibilityScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 无障碍动作执行器
 */
class AccessibilityActionExecutor(
    private val service: AccessibilityScanner
) : ActionExecutor {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    /**
     * 点击坐标
     */
    override suspend fun click(x: Int, y: Int): Boolean {
        Log.d(TAG, "[click] Executing click at ($x, $y)")
        val result = performGesture {
            val clickGesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        Path().apply { moveTo(x.toFloat(), y.toFloat()) },
                        0,
                        ViewConfiguration.getTapTimeout().toLong()
                    )
                )
                .build()
            clickGesture
        }
        Log.d(TAG, "[click] Click result: $result")
        return result
    }

    /**
     * 滑动手势
     */
    override suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long
    ): Boolean {
        Log.d(TAG, "[swipe] Executing swipe from ($fromX, $fromY) to ($toX, $toY) in ${durationMs}ms")
        val result = performGesture {
            val swipePath = Path().apply {
                moveTo(fromX.toFloat(), fromY.toFloat())
                lineTo(toX.toFloat(), toY.toFloat())
            }
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        swipePath,
                        0,
                        durationMs
                    )
                )
                .build()
        }
        Log.d(TAG, "[swipe] Swipe result: $result")
        return result
    }

    /**
     * 输入文本（需要先聚焦到输入框）
     */
    override suspend fun type(text: String): Boolean {
        Log.d(TAG, "[type] Executing type: \"$text\"")
        val currentNode = service.rootInActiveWindow ?: run {
            Log.e(TAG, "[type] Failed: rootInActiveWindow is null")
            return false
        }

        // 查找可编辑的节点
        val editableNode = findEditableNode(currentNode)
        if (editableNode == null) {
            Log.e(TAG, "[type] Failed: no editable node found")
            return false
        }

        Log.d(TAG, "[type] Found editable node: ${editableNode.className}")

        // 使用 Accessibility Action 设置文本
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }

        val result = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "[type] Type result: $result")
        return result
    }

    /**
     * 打开应用
     */
    override suspend fun openApp(packageName: String): Boolean {
        Log.d(TAG, "[openApp] Executing open app: $packageName")
        return try {
            val intent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_MAIN
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            Log.d(TAG, "[openApp] Started activity for: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[openApp] Failed to open app: ${e.message}", e)
            false
        }
    }

    /**
     * 返回
     */
    override suspend fun navigateBack(): Boolean {
        Log.d(TAG, "[navigateBack] Executing back navigation")
        val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "[navigateBack] Result: $result")
        return result
    }

    /**
     * 回到主页
     */
    override suspend fun goHome(): Boolean {
        Log.d(TAG, "[goHome] Executing home navigation")
        val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Log.d(TAG, "[goHome] Result: $result")
        return result
    }

    /**
     * 打开最近任务
     */
    override suspend fun openRecentApps(): Boolean {
        Log.d(TAG, "[openRecentApps] Executing recent apps navigation")
        val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        Log.d(TAG, "[openRecentApps] Result: $result")
        return result
    }

    /**
     * 滚动
     */
    override suspend fun scroll(nodeId: String, direction: String): Boolean {
        return try {
            // 如果提供了 nodeId，查找并滚动该节点
            val node = if (nodeId.isNotEmpty()) {
                AccessibilityScanner.instance?.findNodeById(nodeId)
            } else {
                // 否则使用当前焦点节点
                AccessibilityScanner.instance?.rootInActiveWindow
            }

            node?.let { targetNode ->
                val scrollAction = when (direction) {
                    "forward", "up" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "backward", "down" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                targetNode.performAction(scrollAction)
            } ?: run {
                // 如果找不到节点，尝试使用全局滚动（Android 13+）
                // 注意：旧版本 Android 可能不支持全局滚动
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 截图
     */
    override suspend fun takeScreenshot(): ByteArray? {
        // Android 13+ 可以使用 AccessibilityService 截图
        // 旧版本需要 MediaProjection
        return null
    }

    /**
     * 点击节点
     */
    suspend fun clickNode(nodeInfo: AccessibilityNodeInfo): Boolean {
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 长按节点
     */
    suspend fun longClickNode(nodeInfo: AccessibilityNodeInfo): Boolean {
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /**
     * 查找可编辑的节点
     */
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // BFS 查找可编辑节点
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isEditable) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    /**
     * 执行手势
     */
    private suspend fun performGesture(gestureBuilder: () -> GestureDescription): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val gesture = gestureBuilder()
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(false)
                    }
                }
            }, null)
        }
    }
}

/**
 * 判断节点是否可编辑
 */
private fun AccessibilityNodeInfo.isEditable(): Boolean {
    return className?.toString()?.let {
        it.contains("EditText", ignoreCase = true) ||
                it.contains("Edit", ignoreCase = true)
    } ?: false
}

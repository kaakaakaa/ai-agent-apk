// File: app/src/main/java/com/agent/apk/perception/AccessibilityScanner.kt
package com.agent.apk.perception

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.apk.model.Bounds
import com.agent.apk.model.UiNode
import com.agent.apk.model.UiTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 无障碍服务：获取屏幕 UI 树
 */
class AccessibilityScanner : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityScanner"

        /**
         * 获取全局实例（用于从其他组件调用）
         */
        var instance: AccessibilityScanner? = null
            private set

        /**
         * 当前屏幕 UI 树的 Flow
         */
        private val _uiTreeFlow = MutableStateFlow<UiTree?>(null)
        val uiTreeFlow: StateFlow<UiTree?> = _uiTreeFlow

        /**
         * 从当前屏幕获取 UI 树
         */
        fun getUiTreeSync(): UiTree? = _uiTreeFlow.value

        /**
         * 根据 UiNode ID 查找 AccessibilityNodeInfo
         */
        fun findNodeInfoByUiNodeId(uiNodeId: String): AccessibilityNodeInfo? {
            return instance?.nodeIdMapping?.get(uiNodeId)
        }

        /**
         * 清除节点映射（窗口变化时调用）
         */
        fun clearNodeIdMapping() {
            instance?.nodeIdMapping?.clear()
        }

        /**
         * 主动触发 UI 树扫描（用于在需要时立即获取）
         */
        fun forceScanUiTree(): UiTree? {
            return instance?.scanCurrentWindow()
        }
    }

    // 节点 ID 映射表：UiNode.id -> AccessibilityNodeInfo
    private val nodeIdMapping = mutableMapOf<String, AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // 初始化 ScreenshotManager 单例
        ScreenshotManager.initialize(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            )
        }
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 窗口状态变化时更新 UI 树
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            // 过滤掉来自本应用的事件，避免不必要的扫描
            if (event.packageName != packageName) {
                val uiTree = scanCurrentWindow()
                _uiTreeFlow.value = uiTree
            }
        }

        // 对于 TYPE_VIEW_SCROLLED 等频繁事件，可以选择性忽略以减少开销
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * 扫描当前窗口的 UI 树（public 方法，供外部调用）
     */
    fun scanCurrentWindow(): UiTree? {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "rootInActiveWindow is null, cannot scan UI tree")
            return null
        }

        val packageName = packageName?.toString() ?: ""
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val nodes = mutableListOf<UiNode>()
        val nodeInfoMap = mutableMapOf<String, AccessibilityNodeInfo>()

        try {
            traverseNode(rootNode, null, nodes, nodeInfoMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error during UI tree traversal: ${e.message}", e)
            // 发生错误时返回部分结果
            if (nodes.isEmpty()) {
                return null
            }
        }

        val uiTree = UiTree(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            packageName = packageName,
            nodes = nodes
        )

        // 更新节点 ID 映射表
        nodeIdMapping.clear()
        nodeIdMapping.putAll(nodeInfoMap)

        Log.d(TAG, "Scanned UI tree: ${nodes.size} nodes from $packageName")
        return uiTree
    }

    /**
     * 递归遍历节点树
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        parentId: String?,
        result: MutableList<UiNode>,
        nodeInfoMap: MutableMap<String, AccessibilityNodeInfo>
    ) {
        val nodeId = UUID.randomUUID().toString().take(8)

        // 建立 ID 映射
        nodeInfoMap[nodeId] = node

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val bounds = Bounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )

        val actions = node.actionList.map { it.id.toString() }

        val uiNode = UiNode(
            id = nodeId,
            className = node.className?.toString() ?: "Unknown",
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            bounds = bounds,
            actions = actions,
            parent = parentId,
            children = emptyList(),
            isVisible = node.isVisibleToUser,
            isEnabled = node.isEnabled,
            isChecked = if (node.isCheckable) node.isChecked else null,
            isSelected = node.isSelected
        )

        result.add(uiNode)

        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, nodeId, result, nodeInfoMap)
            }
        }
    }

    /**
     * 根据 ID 查找节点
     * @param nodeId UiNode 中的 id 字段
     */
    fun findNodeById(nodeId: String): AccessibilityNodeInfo? {
        // 优先从映射表查找
        val mappedNode = findNodeInfoByUiNodeId(nodeId)
        if (mappedNode != null) {
            return mappedNode
        }

        // 映射表未命中时，返回 null
        // 注意：不会从根节点递归查找，因为节点 ID 是随机生成的
        Log.w(TAG, "Node $nodeId not found in mapping table")
        return null
    }
}

// File: app/src/main/java/com/agent/apk/agent/cloud/ReActEngine.kt
package com.agent.apk.agent.cloud

import android.util.Log
import com.agent.apk.agent.ActionExecutor
import com.agent.apk.model.*
import com.agent.apk.perception.AccessibilityScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ReAct 引擎：协调 CloudAgent、ActionExecutor 和 UI 更新
 */
class ReActEngine(
    private val cloudAgent: CloudAgent,
    private val actionExecutor: ActionExecutor
) {
    companion object {
        private const val TAG = "ReActEngine"
        private const val MAX_STEPS = 20
    }

    // 流式输出回调
    var onStreamUpdate: ((text: String, type: StreamUpdateType) -> Unit)? = null

    enum class StreamUpdateType {
        THOUGHT_START,    // 开始思考
        THOUGHT_TOKEN,    // 思考中的 token
        ACTION_START,     // 开始执行动作
        ACTION_TOKEN,     // 动作执行中的 token
        ANSWER_TOKEN,     // 最终回答的 token
        COMPLETE          // 完成
    }

    init {
        // 设置 CloudAgent 的流式回调
        cloudAgent.onStreamToken = { token, type ->
            val streamType = when (type) {
                CloudAgent.StreamType.THOUGHT -> StreamUpdateType.THOUGHT_TOKEN
                CloudAgent.StreamType.ACTION -> StreamUpdateType.ACTION_TOKEN
                CloudAgent.StreamType.FINAL_ANSWER -> StreamUpdateType.ANSWER_TOKEN
            }
            onStreamUpdate?.invoke(token, streamType)
        }
    }

    private val _sessionState = MutableStateFlow<ReActSession?>(null)
    val sessionState: StateFlow<ReActSession?> = _sessionState.asStateFlow()

    private val _currentStep = MutableStateFlow<ReActStep?>(null)
    val currentStep: StateFlow<ReActStep?> = _currentStep.asStateFlow()

    /**
     * 开始执行任务
     * @return 任务执行结果（最终回答）
     */
    suspend fun executeTask(userGoal: String): String? {
        Log.d(TAG, "Starting task execution for: $userGoal")
        val session = cloudAgent.startTask(userGoal)
        _sessionState.value = session

        var stepsExecuted = 0
        var finalAnswer: String? = null
        var lastObservation: String? = null
        var currentThought = StringBuilder()
        var currentAction = StringBuilder()

        while (stepsExecuted < MAX_STEPS && session.status == SessionStatus.ACTIVE) {
            stepsExecuted++
            Log.d(TAG, "Executing step $stepsExecuted/$MAX_STEPS")

            // 重置 StringBuilder 为新步骤做准备
            currentThought.clear()
            currentAction.clear()

            try {
                // 通知 UI 开始思考
                withContext(Dispatchers.Main) {
                    onStreamUpdate?.invoke("", StreamUpdateType.THOUGHT_START)
                }

                // 获取当前 UI 树，增加重试机制
                val uiTree = getUiTreeWithRetry()

                // 执行 ReAct 单步 - 传入上一轮的观察结果
                val result = cloudAgent.step(session, lastObservation ?: uiTree?.toJson())

                // 更新思考内容（完整）
                if (result.thought.isNotEmpty()) {
                    currentThought.append(result.thought)
                    withContext(Dispatchers.Main) {
                        onStreamUpdate?.invoke(result.thought, StreamUpdateType.THOUGHT_TOKEN)
                    }
                }

                _currentStep.value = ReActStep(
                    stepNumber = stepsExecuted,
                    thought = result.thought,
                    action = result.action,
                    isComplete = result.isComplete
                )

                // 如果任务完成，返回结果
                if (result.isComplete) {
                    Log.d(TAG, "Task completed at step $stepsExecuted")
                    finalAnswer = result.finalAnswer
                    // 通知 UI 流式输出结束
                    withContext(Dispatchers.Main) {
                        onStreamUpdate?.invoke(result.finalAnswer ?: "", StreamUpdateType.COMPLETE)
                    }
                    // 结束任务并整合记忆
                    cloudAgent.endTask(session, result.finalAnswer)
                    return finalAnswer
                }

                // 执行动作 - 添加详细日志
                result.action?.let { action ->
                    // 通知 UI 开始执行动作
                    withContext(Dispatchers.Main) {
                        onStreamUpdate?.invoke(
                            when (action) {
                                is com.agent.apk.model.ClickAction -> "点击：${action.target}"
                                is com.agent.apk.model.TypeAction -> "输入：${action.text}"
                                is com.agent.apk.model.SwipeAction -> "滑动：${action.target}"
                                is com.agent.apk.model.OpenAppAction -> "打开：${action.packageName}"
                                is com.agent.apk.model.NavigateAction -> "导航：${action.target}"
                                else -> action.toString()
                            },
                            StreamUpdateType.ACTION_START
                        )
                    }

                    Log.d(TAG, ">>> Executing action: ${action::class.java.simpleName}")
                    Log.d(TAG, ">>> Action details: $action")

                    val startTime = System.currentTimeMillis()
                    val success = executeAction(action)
                    val endTime = System.currentTimeMillis()

                    Log.d(TAG, "<<< Action completed in ${endTime - startTime}ms, success: $success")

                    // 获取动作执行后的 UI 树作为观察结果
                    val newUiTree = getUiTreeWithRetry()

                    val lastRecord = session.executedActions.lastOrNull()
                    if (lastRecord != null) {
                        lastRecord.result = ActionResult(
                            success = success,
                            message = if (success) "执行成功" else "执行失败：${action::class.java.simpleName}",
                            newUiTree = newUiTree
                        )
                        Log.d(TAG, "<<< ActionResult recorded: success=$success")
                    } else {
                        Log.w(TAG, "<<< WARNING: No action record found to update!")
                    }

                    // 准备下一轮的观察结果
                    lastObservation = if (success) {
                        "Action executed successfully: ${action::class.java.simpleName}\n当前屏幕内容：${newUiTree?.toJson() ?: "不可用"}"
                    } else {
                        "Action failed: ${action::class.java.simpleName}"
                    }
                    Log.d(TAG, "<<< Observation for next step: $lastObservation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error at step $stepsExecuted: ${e.message}", e)
                session.status = SessionStatus.FAILED
                return "执行失败：${e.message}"
            }
        }

        // 达到最大步数，强制结束
        if (stepsExecuted >= MAX_STEPS) {
            Log.w(TAG, "Task failed: reached max steps ($MAX_STEPS)")
            session.status = SessionStatus.FAILED
            return "任务执行失败：已达到最大步骤数"
        }

        return finalAnswer ?: "任务完成"
    }

    /**
     * 获取 UI 树，带重试机制
     */
    private suspend fun getUiTreeWithRetry(maxRetries: Int = 3, retryDelayMs: Long = 200): UiTree? {
        for (i in 0 until maxRetries) {
            val uiTree = AccessibilityScanner.getUiTreeSync()
            if (uiTree != null && uiTree.nodes.isNotEmpty()) {
                Log.d(TAG, "Successfully got UI tree with ${uiTree.nodes.size} nodes on attempt ${i + 1}")
                return uiTree
            }
            Log.w(TAG, "UI tree empty on attempt ${i + 1}, retrying in ${retryDelayMs}ms...")
            if (i < maxRetries - 1) {
                kotlinx.coroutines.delay(retryDelayMs)
            }
        }
        Log.w(TAG, "Failed to get UI tree after $maxRetries attempts")
        return null
    }

    /**
     * 执行单个动作
     */
    private suspend fun executeAction(action: Action): Boolean {
        return when (action) {
            is ClickAction -> executeClick(action)
            is SwipeAction -> executeSwipe(action)
            is TypeAction -> executeType(action)
            is OpenAppAction -> executeOpenApp(action)
            is NavigateAction -> executeNavigate(action)
            is ScrollAction -> executeScroll(action)
            is ScreenshotAction -> executeScreenshot(action)
            is UnknownAction -> false
        }
    }

    /**
     * 执行点击动作
     */
    private suspend fun executeClick(action: ClickAction): Boolean {
        Log.d(TAG, "[Click] Starting click execution")
        return try {
            // 1. 优先使用 nodeId 点击节点
            if (action.nodeId != null) {
                Log.d(TAG, "[Click] Attempting node click with nodeId: ${action.nodeId}")
                val node = AccessibilityScanner.instance?.findNodeById(action.nodeId)
                if (node != null) {
                    Log.d(TAG, "[Click] Node found, performing ACTION_CLICK")
                    val result = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "[Click] Node click result: $result")
                    return result
                }
                Log.w(TAG, "[Click] Node ${action.nodeId} not found, trying alternative methods")
            }

            // 2. 使用坐标点击
            action.bounds?.let { bounds ->
                Log.d(TAG, "[Click] Executing coordinate click at (${bounds.centerX()}, ${bounds.centerY()})")
                val result = actionExecutor.click(bounds.centerX(), bounds.centerY())
                Log.d(TAG, "[Click] Coordinate click result: $result")
                return result
            }

            // 3. 根据 target 文字在 UI 树中查找并点击
            action.target?.let { target ->
                Log.d(TAG, "[Click] Trying to find element by target: $target")
                val uiTree = AccessibilityScanner.getUiTreeSync()
                if (uiTree != null) {
                    val node = findNodeByTarget(uiTree, target)
                    if (node != null) {
                        Log.d(TAG, "[Click] Found node by target, performing click")
                        val result = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "[Click] Click by target result: $result")
                        return result
                    }
                }
                Log.w(TAG, "[Click] Could not find node by target: $target")
            }

            Log.e(TAG, "[Click] All click methods failed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "[Click] Exception during click: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 根据 target 在 UI 树中查找节点
     */
    private fun findNodeByTarget(uiTree: UiTree, target: String): android.view.accessibility.AccessibilityNodeInfo? {
        val scanner = AccessibilityScanner.instance ?: return null

        // 尝试匹配 node
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

    /**
     * 执行滑动手势
     */
    private suspend fun executeSwipe(action: SwipeAction): Boolean {
        return try {
            actionExecutor.swipe(
                action.fromX, action.fromY,
                action.toX, action.toY,
                action.durationMs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行输入文本动作
     */
    private suspend fun executeType(action: TypeAction): Boolean {
        return try {
            // 优先使用 nodeId 定位输入框
            if (action.nodeId != null) {
                val node = AccessibilityScanner.instance?.findNodeById(action.nodeId)
                if (node != null) {
                    val arguments = android.os.Bundle().apply {
                        putCharSequence(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            action.text
                        )
                    }
                    node.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    )
                    return true
                }
                // nodeId 找不到，降级使用 type 方法
                Log.w(TAG, "Node ${action.nodeId} not found, falling back to generic type")
            }

            // 使用通用 type 方法
            actionExecutor.type(action.text)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行打开应用动作
     */
    private suspend fun executeOpenApp(action: OpenAppAction): Boolean {
        Log.d(TAG, "[OpenApp] Starting open app execution for: ${action.packageName}")
        return try {
            Log.d(TAG, "[OpenApp] Calling actionExecutor.openApp(${action.packageName})")
            val result = actionExecutor.openApp(action.packageName)
            Log.d(TAG, "[OpenApp] Open app result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "[OpenApp] Exception during open app: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行导航动作
     */
    private suspend fun executeNavigate(action: NavigateAction): Boolean {
        Log.d(TAG, "[Navigate] Starting navigation to: ${action.target}")
        return try {
            val result = when (action.target) {
                "back" -> {
                    Log.d(TAG, "[Navigate] Executing back navigation")
                    actionExecutor.navigateBack()
                }
                "home" -> {
                    Log.d(TAG, "[Navigate] Executing home navigation")
                    actionExecutor.goHome()
                }
                "recent" -> {
                    Log.d(TAG, "[Navigate] Executing recent apps navigation")
                    actionExecutor.openRecentApps()
                }
                else -> {
                    Log.w(TAG, "[Navigate] Unknown navigation target: ${action.target}")
                    false
                }
            }
            Log.d(TAG, "[Navigate] Navigation result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "[Navigate] Exception during navigation: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行滚动动作
     */
    private suspend fun executeScroll(action: ScrollAction): Boolean {
        return try {
            val direction = when (action.direction) {
                com.agent.apk.model.ScrollDirection.UP -> "forward"
                com.agent.apk.model.ScrollDirection.DOWN -> "backward"
                com.agent.apk.model.ScrollDirection.LEFT -> "left"
                com.agent.apk.model.ScrollDirection.RIGHT -> "right"
            }

            // 优先使用 nodeId 定位滚动容器
            if (action.nodeId != null) {
                val node = AccessibilityScanner.instance?.findNodeById(action.nodeId)
                if (node != null) {
                    actionExecutor.scroll(action.nodeId, direction)
                    return true
                }
                Log.w(TAG, "Node ${action.nodeId} not found, falling back to generic scroll")
            }

            actionExecutor.scroll("", direction)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行截图动作
     */
    private suspend fun executeScreenshot(action: ScreenshotAction): Boolean {
        return try {
            // Android 13+ 可以直接截图，旧版本需要 MediaProjection
            val screenshotManager = com.agent.apk.perception.ScreenshotManager.getInstance()
            screenshotManager?.takeScreenshot() != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 取消当前任务
     */
    fun cancelTask() {
        _sessionState.value?.status = SessionStatus.FAILED
        _sessionState.value = null
        _currentStep.value = null
    }

    /**
     * 获取任务执行历史
     */
    fun getExecutionHistory(): List<ReActStep> {
        return _sessionState.value?.executedActions?.map { record ->
            ReActStep(
                stepNumber = 0,
                thought = "",
                action = record.action,
                isComplete = false
            )
        } ?: emptyList()
    }
}

data class ReActStep(
    val stepNumber: Int,
    val thought: String,
    val action: Action?,
    val isComplete: Boolean
)

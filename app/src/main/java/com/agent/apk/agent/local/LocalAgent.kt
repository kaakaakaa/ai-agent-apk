// File: app/src/main/java/com/agent/apk/agent/local/LocalAgent.kt
package com.agent.apk.agent.local

import com.agent.apk.agent.ActionExecutor
import com.agent.apk.model.*
// import com.google.mediapipe.tasks.llm.inference.InferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 本地 Agent：基于 MediaPipe LLM 的轻量级任务执行器
 *
 * 特点：
 * - 离线运行，无需网络
 * - 低延迟，快速响应
 * - 适合简单任务（打开应用、基础导航、简单问答）
 *
 * 限制：
 * - 模型参数量小，复杂推理能力有限
 * - 不支持多模态视觉输入
 */
class LocalAgent(
    private val modelManager: ModelManager,
    private val actionExecutor: ActionExecutor
) {

    // 暂时使用 String 作为 conversation 占位符（MediaPipe 不可用）
    private var conversation: String? = null

    /**
     * 系统提示词：定义 Agent 角色和能力边界
     */
    private val systemPrompt = """
        你是一个 Android 手机智能助手。你的任务是理解用户的指令，然后通过手机界面执行操作。

        你可以执行以下操作：
        1. 打开应用 (open_app: 包名)
        2. 点击坐标 (click: x,y)
        3. 滑动 (swipe: fromX,fromY,toX,toY,durationMs)
        4. 输入文本 (type: 文本内容)
        5. 返回 (navigate: back)
        6. 回到主页 (navigate: home)
        7. 打开最近任务 (navigate: recent)

        请用简洁的中文回答。如果需要执行操作，先说明你的思考过程，然后给出操作指令。

        示例：
        用户：打开微信
        思考：用户想要打开微信应用。微信的包名是 com.tencent.mm
        操作：open_app: com.tencent.mm

        用户：返回
        思考：用户想要执行返回操作
        操作：navigate: back
    """.trimIndent()

    /**
     * 开始任务
     */
    suspend fun startTask(userGoal: String): ReActSession = withContext(Dispatchers.IO) {
        val session = ReActSession(
            taskId = generateTaskId(),
            userGoal = userGoal,
            startTime = System.currentTimeMillis(),
            status = SessionStatus.ACTIVE
        )

        // 初始化对话
        initConversation()

        session
    }

    /**
     * 执行单步 ReAct 循环
     */
    suspend fun step(session: ReActSession, uiTreeJson: String?): ReActResult = withContext(Dispatchers.IO) {
        val thought = think(uiTreeJson)
        val action = parseAction(thought)
        // 如果没有动作或动作为空，视为任务完成
        val isComplete = action == null

        ReActResult(
            thought = thought,
            action = action,
            finalAnswer = if (isComplete) thought else null,
            isComplete = isComplete
        )
    }

    /**
     * 结束任务
     */
    fun endTask(session: ReActSession) {
        session.status = SessionStatus.COMPLETED
        conversation = null
    }

    /**
     * 初始化对话
     */
    private fun initConversation() {
        // MediaPipe 暂时不可用，使用本地存根
        conversation = systemPrompt
    }

    /**
     * 思考过程：根据 UI 树生成推理
     */
    private suspend fun think(uiTreeJson: String?): String = withContext(Dispatchers.Default) {
        // 暂时返回存根响应（MediaPipe 不可用）
        return@withContext "思考：本地模型暂不可用，建议使用云端模型。"
    }

    /**
     * 构建推理提示词
     */
    private fun buildPrompt(uiTreeJson: String?): String {
        val context = buildString {
            appendLine("当前屏幕内容：")
            if (uiTreeJson != null) {
                appendLine(uiTreeJson)
            } else {
                appendLine("无法获取屏幕内容")
            }
            appendLine()
            appendLine("请分析屏幕内容，然后决定下一步操作。")
        }
        return context
    }

    /**
     * 解析 LLM 输出为 Action
     */
    private fun parseAction(response: String): Action? {
        val lines = response.lines()

        for (line in lines) {
            if (line.startsWith("操作：") || line.startsWith("操作:")) {
                val actionPart = line.substringAfter("操作：").substringAfter("操作:").trim()
                return parseActionLine(actionPart)
            }
        }

        return null
    }

    /**
     * 解析动作行
     */
    private fun parseActionLine(actionLine: String): Action? {
        val actionPart = actionLine.substringAfter("操作：").substringAfter("操作:").trim()
        return when {
            actionPart.startsWith("open_app:") -> {
                val packageName = actionPart.substringAfter("open_app:").trim()
                OpenAppAction(packageName = packageName, reason = "打开应用", target = packageName)
            }
            actionPart.startsWith("click:") -> {
                val coords = actionPart.substringAfter("click:").trim().split(",")
                if (coords.size == 2) {
                    ClickAction(
                        target = "坐标点击",
                        reason = "点击坐标",
                        bounds = Bounds(
                            left = coords[0].trim().toIntOrNull() ?: 0,
                            top = coords[1].trim().toIntOrNull() ?: 0,
                            right = (coords[0].trim().toIntOrNull() ?: 0) + 50,
                            bottom = (coords[1].trim().toIntOrNull() ?: 0) + 50
                        )
                    )
                } else null
            }
            actionPart.startsWith("swipe:") -> {
                val params = actionPart.substringAfter("swipe:").trim().split(",")
                if (params.size == 5) {
                    SwipeAction(
                        target = "滑动手势",
                        reason = "滑动手势",
                        fromX = params[0].trim().toIntOrNull() ?: 0,
                        fromY = params[1].trim().toIntOrNull() ?: 0,
                        toX = params[2].trim().toIntOrNull() ?: 0,
                        toY = params[3].trim().toIntOrNull() ?: 0,
                        durationMs = params[4].trim().toLongOrNull() ?: 300
                    )
                } else null
            }
            actionPart.startsWith("type:") -> {
                val text = actionPart.substringAfter("type:").trim()
                TypeAction(text = text, reason = "输入文本", target = "输入框")
            }
            actionPart.startsWith("navigate:") -> {
                val target = actionPart.substringAfter("navigate:").trim()
                NavigateAction(target = target, reason = "导航操作")
            }
            else -> null
        }
    }

    /**
     * 生成任务 ID
     */
    private fun generateTaskId(): String {
        return "local_${System.currentTimeMillis()}"
    }

    /**
     * 判断当前任务是否适合本地模型执行
     */
    fun canHandleTask(task: com.agent.apk.model.Task): Boolean {
        return task.isSimpleCommand()
    }

    /**
     * 执行任务（便捷方法）
     */
    suspend fun execute(task: com.agent.apk.model.Task) {
        val session = startTask(task.userInstruction)
        val uiTreeJson = com.agent.apk.perception.AccessibilityScanner.getUiTreeSync()?.toJson()
        val result = step(session, uiTreeJson)

        // 执行动作
        result.action?.let { action ->
            executeAction(action)
        }

        if (result.isComplete) {
            endTask(session)
        }
    }

    /**
     * 执行单个动作
     */
    private suspend fun executeAction(action: com.agent.apk.model.Action): Boolean {
        return when (action) {
            is com.agent.apk.model.ClickAction -> {
                action.bounds?.let { bounds ->
                    actionExecutor.click(bounds.centerX(), bounds.centerY())
                } ?: false
            }
            is com.agent.apk.model.SwipeAction -> {
                actionExecutor.swipe(
                    action.fromX, action.fromY,
                    action.toX, action.toY,
                    action.durationMs
                )
            }
            is com.agent.apk.model.TypeAction -> {
                actionExecutor.type(action.text)
            }
            is com.agent.apk.model.OpenAppAction -> {
                actionExecutor.openApp(action.packageName)
            }
            is com.agent.apk.model.NavigateAction -> {
                when (action.target) {
                    "back" -> actionExecutor.navigateBack()
                    "home" -> actionExecutor.goHome()
                    "recent" -> actionExecutor.openRecentApps()
                    else -> false
                }
            }
            else -> false
        }
    }
}

// File: app/src/main/java/com/agent/apk/agent/cloud/CloudAgent.kt
package com.agent.apk.agent.cloud

import android.content.Context
import android.util.Log
import com.agent.apk.infra.ConversationHistoryManager
import com.agent.apk.infra.MemoryStore
import com.agent.apk.model.*
import com.agent.apk.perception.AccessibilityScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 云端 Agent：处理复杂任务，支持 ReAct 模式
 */
class CloudAgent(
    private val context: Context,
    private val llmClient: LlmClient,
    private val model: String,
    private val supportsVision: Boolean
) {
    companion object {
        private const val TAG = "CloudAgent"
        private const val MAX_HISTORY_SIZE = 6  // 只保留最近 6 条，避免上下文负担过重
    }

    // 流式输出回调
    var onStreamToken: ((token: String, type: StreamType) -> Unit)? = null

    enum class StreamType {
        THOUGHT,      // 思考中
        ACTION,       // 动作执行中
        FINAL_ANSWER  // 最终回答
    }

    private val historyManager = ConversationHistoryManager.getInstance(context)
    private val memoryStore = MemoryStore.getInstance(context)

    // 持久化对话历史 - 从数据库加载
    private val conversationHistory = mutableListOf<ReActMessage>()

    // 标记是否已加载历史（避免重复加载）
    private var isHistoryLoaded = false
    private var isMemoryLoaded = false

    // 初始化系统提示词（只添加一次）
    init {
        conversationHistory.add(
            ReActMessage(
                role = "system",
                content = buildSystemPrompt()
            )
        )
    }

    /**
     * 开始一个新任务
     */
    suspend fun startTask(goal: String): ReActSession {
        Log.d(TAG, "Starting new task: $goal")

        // 第一次启动时加载历史（只加载一次）
        if (!isHistoryLoaded) {
            loadConversationHistory()
            isHistoryLoaded = true
        }

        // 加载长期记忆
        if (!isMemoryLoaded) {
            loadLongTermMemory()
            isMemoryLoaded = true
        }

        // 添加用户目标到共享历史
        conversationHistory.add(
            ReActMessage(
                role = "user",
                content = goal
            )
        )

        val session = ReActSession(
            taskId = java.util.UUID.randomUUID().toString(),
            userGoal = goal
        )
        // 同步共享历史到 session
        session.conversationHistory.addAll(conversationHistory.map { it.copy() })

        return session
    }

    /**
     * 加载长期记忆到对话历史
     */
    private suspend fun loadLongTermMemory() {
        try {
            val memoryContext = memoryStore.getMemoryContext()
            if (memoryContext.isNotBlank()) {
                Log.d(TAG, "Loaded long-term memory")
                // 作为系统提示添加，让 AI 知道用户的偏好和历史
                conversationHistory.add(
                    ReActMessage(
                        role = "system",
                        content = memoryContext
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load long-term memory", e)
        }
    }

    /**
     * 加载之前的对话历史（最近 6 条，仅作为上下文参考）
     * 注意：只提供给 AI 参考之前的对话内容，不会重新执行历史动作
     */
    private fun loadConversationHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recentHistory = historyManager.getRecentConversations(6)
                if (recentHistory.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${recentHistory.size} conversation history entries for context reference")

                    // 构建简洁的历史对话摘要
                    val historySummary = recentHistory
                        .filter { it.role == "user" || (it.role == "assistant" && it.taskCompleted) }
                        .take(6)
                        .joinToString("\n") { entity ->
                            when (entity.role) {
                                "user" -> "用户：${entity.content}"
                                "assistant" -> "助手：${entity.content}"
                                else -> ""
                            }
                        }

                    if (historySummary.isNotEmpty()) {
                        // 作为系统提示添加，让 AI 知道之前的对话上下文
                        conversationHistory.add(
                            ReActMessage(
                                role = "system",
                                content = """
|=== 之前的对话 ===
|$historySummary
|=================
|重要：
|1. 这些是之前的对话，供你参考
|2. 不要重新执行历史操作
|3. 如果用户继续之前的话题，基于历史记录回答
|4. 如果用户没有提出新请求，只需回答，不要操作
""".trimIndent()
                            )
                        )
                        Log.d(TAG, "Added history summary as system context")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation history", e)
            }
        }
    }

    /**
     * 保存用户消息到持久化存储
     */
    private fun saveUserMessage(content: String) {
        CoroutineScope(Dispatchers.IO).launch {
            historyManager.saveUserMessage(content)
        }
    }

    /**
     * 保存 AI 回复到持久化存储
     */
    private fun saveAssistantMessage(content: String, thought: String?, action: String?, completed: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            historyManager.saveAssistantMessage(content, thought, action, completed)
        }
    }

    /**
     * 执行 ReAct 单步
     */
    suspend fun step(session: ReActSession, observation: String? = null): ReActResult {
        Log.d(TAG, "Executing step for task: ${session.taskId}")

        // 如果有观察结果，添加到共享对话历史（使用 'user' role，因为 observation 不被 API 接受）
        observation?.let {
            Log.d(TAG, "Adding observation: $it")
            conversationHistory.add(
                ReActMessage(role = "user", content = "Observation: $it")
            )
        }

        // 检查是否无限循环
        if (session.isInfiniteLoop()) {
            Log.w(TAG, "Detected infinite loop")
            return ReActResult(
                thought = "检测到无限循环，需要调整策略",
                action = null,
                finalAnswer = "抱歉，我无法完成此任务，请尝试更具体的指令",
                isComplete = true
            )
        }

        // 检查是否超时（10 分钟）
        if (System.currentTimeMillis() - session.startTime > 10 * 60 * 1000) {
            Log.w(TAG, "Task timeout")
            session.status = SessionStatus.TIMEOUT
            return ReActResult(
                thought = "任务执行超时",
                action = null,
                finalAnswer = "任务执行超时，请稍后重试",
                isComplete = true
            )
        }

        // 构建当前上下文
        val currentUiTree = AccessibilityScanner.getUiTreeSync()
        val contextMessage = buildContextMessage(currentUiTree)

        // 调用 LLM - 使用共享的对话历史
        Log.d(TAG, "Calling LLM with model: $model, history size: ${conversationHistory.size}")
        val response = callLlm(conversationHistory, contextMessage)
        Log.d(TAG, "LLM response: $response")

        // 解析响应
        val result = parseReActResponse(response, currentUiTree)
        Log.d(TAG, "Parsed result - thought: ${result.thought}, action: ${result.action}, isComplete: ${result.isComplete}")

        // 记录执行的操作
        result.action?.let { action ->
            Log.d(TAG, "Recording action: ${action::class.java.simpleName}")
            session.executedActions.add(
                ActionRecord(
                    action = action,
                    result = ActionResult(success = true, message = "执行中...")
                )
            )
        }

        if (result.isComplete) {
            session.status = SessionStatus.COMPLETED
            // 保存最终回答到持久化历史
            saveAssistantMessage(
                content = result.finalAnswer ?: "",
                thought = result.thought,
                action = result.action?.toString(),
                completed = true
            )
        }

        return result
    }

    /**
     * 结束任务并整合记忆
     */
    suspend fun endTask(session: ReActSession, summary: String? = null) {
        session.status = SessionStatus.COMPLETED

        // 整合记忆：将本次对话保存到长期记忆和历史日志
        if (summary != null || session.executedActions.isNotEmpty()) {
            val messages = session.conversationHistory
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(10)  // 只保存最近 10 条

            memoryStore.consolidate(messages, summary)
            Log.d(TAG, "Memory consolidated for task: ${session.taskId}")
        }
    }

    /**
     * 获取记忆文件路径（用于调试）
     */
    fun getMemoryFilePath(): String = memoryStore.getMemoryFilePath()
    fun getHistoryFilePath(): String = memoryStore.getHistoryFilePath()

    /**
     * 搜索历史记录
     */
    suspend fun searchHistory(keyword: String): List<String> {
        return memoryStore.searchHistory(keyword)
    }

    /**
     * 清空记忆
     */
    suspend fun clearMemory() {
        memoryStore.clearAll()
    }

    private fun buildSystemPrompt(): String {
        return """
你是 AI 手机助手，用自然、友好的方式与用户交流。

## 你能做什么
- 打开应用、点击、输入、滑动、返回、主页
- 回答各种问题、提供建议

## 工作方式
1. 思考用户需要什么
2. 如果需要操作，执行一个动作
3. 如果只是聊天，直接回答

## 输出格式

**需要操作时：**
```
Thought: 理解用户意图，决定做什么
Action: click(目标)
```

**直接回答时：**
```
Thought: 理解用户意图
Final Answer: 自然、友好的回复
```

## 常见包名
- 微信：com.tencent.mm
- 支付宝：com.eg.android.AlipayGphone
- 设置：com.android.settings

## 记住
- 聊天直接回答，不需要操作
- 一次只做一个动作
- 像朋友一样交流，不要太机械

## 示例

**聊天：**
用户："你好"
Thought: 用户在打招呼
Final Answer: 你好！我是你的 AI 助手，有什么可以帮你的吗？

用户："谢谢"
Thought: 用户在感谢
Final Answer: 不客气！随时为你服务～

**操作：**
用户："打开微信"
Thought: 用户要打开微信
Action: openApp(com.tencent.mm)
""".trimIndent()
    }

    /**
     * 构建系统提示词（包含长期记忆）
     */
    private suspend fun buildSystemPromptWithMemory(): String {
        val basePrompt = buildSystemPrompt()
        val memoryContext = memoryStore.getMemoryContext()

        return if (memoryContext.isNotBlank()) {
            "$basePrompt\n\n$memoryContext"
        } else {
            basePrompt
        }
    }

    private fun buildContextMessage(uiTree: UiTree?): String {
        if (uiTree == null || uiTree.nodes.isEmpty()) {
            // 主动触发扫描获取屏幕内容
            Log.w(TAG, "UI tree is null or empty, forcing fresh scan...")
            val freshUiTree = AccessibilityScanner.forceScanUiTree()
            if (freshUiTree != null && freshUiTree.nodes.isNotEmpty()) {
                Log.d(TAG, "Successfully scanned UI tree with ${freshUiTree.nodes.size} nodes")
                return "当前屏幕内容：\n${freshUiTree.toJson()}"
            }

            // 尝试从 Flow 获取
            val flowUiTree = AccessibilityScanner.getUiTreeSync()
            if (flowUiTree != null && flowUiTree.nodes.isNotEmpty()) {
                Log.d(TAG, "Got UI tree from flow with ${flowUiTree.nodes.size} nodes")
                return "当前屏幕内容：\n${flowUiTree.toJson()}"
            }

            // 如果仍然无法获取，尝试获取当前活动应用
            val packageName = AccessibilityScanner.instance?.packageName?.toString() ?: "未知"

            // 检查是否在自己应用界面（这是正常的）
            val isOwnApp = packageName == "com.agent.apk"

            val errorMessage = buildString {
                if (isOwnApp) {
                    appendLine("[当前在 AI 助手应用界面]")
                    appendLine()
                    appendLine("提示：你正在与 AI 助手对话，无障碍服务无法获取自己应用的界面内容。")
                    appendLine("请切换到其他应用（如设置、微信等），然后我可以帮你操作。")
                } else {
                    appendLine("当前屏幕内容暂时不可用")
                    appendLine("当前活动应用：$packageName")
                    appendLine()
                    appendLine("提示：无障碍服务正在获取屏幕内容，请稍等重试")
                }
            }
            Log.w(TAG, "UI tree still unavailable, package: $packageName, isOwnApp: $isOwnApp")
            return errorMessage
        }
        Log.d(TAG, "Building context message with ${uiTree.nodes.size} nodes")
        return "当前屏幕内容：\n${uiTree.toJson()}"
    }

    private suspend fun callLlm(
        history: List<ReActMessage>,
        context: String
    ): String {
        Log.d(TAG, "callLlm: Building request with ${history.size} messages")

        val messages = history.map {
            Message(role = it.role, content = it.content)
        } + Message(role = "user", content = context)

        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = 0.7f,
            maxTokens = 2048
        )

        try {
            Log.d(TAG, "callLlm: Sending streaming request to LLM")
            // 使用流式输出
            val fullContent = llmClient.streamChatCompletion(request) { token ->
                // 实时回调每个 token
                onStreamToken?.invoke(token, StreamType.THOUGHT)
            }
            Log.d(TAG, "callLlm: Received streaming response with ${fullContent.length} characters")
            return fullContent
        } catch (e: Exception) {
            Log.e(TAG, "callLlm: Failed to get LLM response: ${e.message}", e)
            throw e
        }
    }

    private fun parseReActResponse(response: String, uiTree: UiTree?): ReActResult {
        val thought = extractThought(response)
        val action = extractAction(response, uiTree)
        var finalAnswer = extractFinalAnswer(response)

        // 如果没有动作且没有 Final Answer，但回复内容看起来是完整的回答，自动标记为完成
        // 这处理聊天对话场景（如"你好"），LLM 可能不会输出 "Final Answer:" 格式
        if (action == null && finalAnswer == null && response.isNotBlank()) {
            // 检查是否是需要操作的场景
            val isActionRequired = checkIsActionRequired(response, uiTree)
            if (!isActionRequired) {
                // 这是聊天对话，直接将回复作为 Final Answer
                finalAnswer = response
                Log.d(TAG, "Detected chat conversation, marking as complete without action")
            }
        }

        return ReActResult(
            thought = thought,
            action = action,
            finalAnswer = finalAnswer,
            isComplete = finalAnswer != null
        )
    }

    /**
     * 检查回复是否需要执行动作
     * @param response LLM 回复内容
     * @param uiTree 当前 UI 树
     * @return true 表示需要执行动作，false 表示只是聊天
     */
    private fun checkIsActionRequired(response: String, uiTree: UiTree?): Boolean {
        // 检查回复中是否包含动作意图的关键词
        val actionKeywords = listOf("点击", "打开", "滑动", "输入", "返回", "主页", "swipe", "click", "open", "type")
        val hasActionIntent = actionKeywords.any { it in response }

        // 检查是否在用户应用界面（而不是在自己应用界面）
        val isInUserApp = uiTree?.packageName != "com.agent.apk"

        // 如果是聊天内容（如问候、问答），不需要动作
        val chatKeywords = listOf("你好", "您好", "嗨", "hello", "hi", "谢谢", "再见", "叫什么", "是谁", "天气", "时间")
        val isChat = chatKeywords.any { it in response.lowercase() }

        // 如果包含动作关键词且不在自己应用界面，可能需要动作
        if (hasActionIntent && isInUserApp) {
            return true
        }

        // 如果是聊天内容，不需要动作
        if (isChat) {
            return false
        }

        // 默认：如果回复看起来是完整的回答（包含问候、解释、建议），不需要动作
        val isCompleteResponse = response.length > 20 && !hasActionIntent
        return !isCompleteResponse
    }

    private fun extractThought(response: String): String {
        val thoughtMatch = Regex("Thought:\\s*(.+?)(?=\\n|Action|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        return thoughtMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractAction(response: String, uiTree: UiTree?): Action? {
        val actionMatch = Regex("Action:\\s*(.+)")
            .find(response)
            ?: return null

        val actionStr = actionMatch.groupValues[1].trim()

        // 解析动作字符串
        return when {
            actionStr.startsWith("click(") -> parseClickAction(actionStr, uiTree)
            actionStr.startsWith("swipe(") -> parseSwipeAction(actionStr)
            actionStr.startsWith("type(") -> parseTypeAction(actionStr)
            actionStr.startsWith("openApp(") -> parseOpenAppAction(actionStr)
            actionStr == "goBack()" -> NavigateAction.back()
            actionStr == "goHome()" -> NavigateAction.home()
            else -> null
        }
    }

    private fun extractFinalAnswer(response: String): String? {
        val match = Regex("Final Answer:\\s*(.+)")
            .find(response)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun parseClickAction(actionStr: String, uiTree: UiTree?): Action? {
        val target = Regex("click\\((.+)\\)").find(actionStr)?.groupValues?.get(1)?.trim()
            ?: return null

        // 如果 UI 树为空，返回基本的 ClickAction
        if (uiTree == null || uiTree.nodes.isEmpty()) {
            Log.w(TAG, "UI tree is empty, creating click action without node info")
            return ClickAction(target = target, reason = "用户请求")
        }

        // 在 UI 树中匹配元素
        val matchedNode = findMatchingNode(uiTree, target)

        return if (matchedNode != null) {
            Log.d(TAG, "Matched click target '$target' to node ${matchedNode.id}")
            ClickAction(
                target = target,
                reason = "用户请求",
                nodeId = matchedNode.id,
                bounds = matchedNode.bounds
            )
        } else {
            Log.w(TAG, "Could not match click target '$target' to any UI node")
            // 降级：只返回 target，让 ReActEngine 尝试其他方式
            ClickAction(target = target, reason = "用户请求")
        }
    }

    /**
     * 在 UI 树中匹配元素
     * @param uiTree UI 树
     * @param target 用户指定的目标（可以是 text、desc、className）
     */
    private fun findMatchingNode(uiTree: UiTree, target: String): UiNode? {
        val visibleNodes = uiTree.nodes.filter { it.isVisible && it.isEnabled }

        // 1. 精确匹配 text
        val exactTextMatch = visibleNodes.find { it.text?.equals(target, ignoreCase = true) == true }
        if (exactTextMatch != null) return exactTextMatch

        // 2. 精确匹配 contentDescription
        val exactDescMatch = visibleNodes.find { it.contentDescription?.equals(target, ignoreCase = true) == true }
        if (exactDescMatch != null) return exactDescMatch

        // 3. 模糊匹配 text（包含）
        val containsTextMatch = visibleNodes.find { it.text?.contains(target, ignoreCase = true) == true }
        if (containsTextMatch != null) return containsTextMatch

        // 4. 模糊匹配 desc
        val containsDescMatch = visibleNodes.find { it.contentDescription?.contains(target, ignoreCase = true) == true }
        if (containsDescMatch != null) return containsDescMatch

        // 5. 匹配 className（如果 target 像类名）
        val classMatch = visibleNodes.find { it.className.endsWith(target, ignoreCase = true) }
        if (classMatch != null) return classMatch

        return null
    }

    private fun parseSwipeAction(actionStr: String): Action? {
        val direction = Regex("swipe\\((.+)\\)").find(actionStr)?.groupValues?.get(1)?.trim()
            ?: return null
        return SwipeAction(
            target = direction,
            reason = "用户请求",
            fromX = 500, fromY = 1500,
            toX = 500, toY = 500
        )
    }

    private fun parseTypeAction(actionStr: String): Action? {
        val text = Regex("type\\((.+)\\)").find(actionStr)?.groupValues?.get(1)?.trim()
            ?: return null
        return TypeAction(target = "input", reason = "用户请求", text = text)
    }

    private fun parseOpenAppAction(actionStr: String): Action? {
        val packageName = Regex("openApp\\((.+)\\)").find(actionStr)?.groupValues?.get(1)?.trim()
            ?: return null
        return OpenAppAction(target = packageName, reason = "用户请求", packageName = packageName)
    }
}

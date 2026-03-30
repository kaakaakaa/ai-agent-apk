// File: app/src/main/java/com/agent/apk/agent/AgentLoop.kt
package com.agent.apk.agent

import android.util.Log
import com.agent.apk.agent.cloud.*
import com.agent.apk.agent.session.Session
import com.agent.apk.agent.session.SessionManager
import com.agent.apk.agent.tools.*
import com.agent.apk.infra.ApiKeyManager
import com.agent.apk.infra.MemoryStore
import com.agent.apk.model.ReActMessage
import com.agent.apk.model.VendorConfig
import com.agent.apk.perception.AccessibilityScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Agent 核心循环 - 基于 nanobot 架构优化
 *
 * 职责:
 * 1. 接收用户输入
 * 2. 构建上下文（历史 + 记忆 + 当前屏幕）
 * 3. 调用 LLM
 * 4. 执行工具调用
 * 5. 更新记忆系统
 *
 * 优化特性:
 * - Token 预算管理（避免上下文超限）
 * - 流式输出支持
 * - 失败重试机制
 * - 记忆自动整合
 */
class AgentLoop private constructor(
    private val context: android.content.Context
) {
    companion object {
        private const val TAG = "AgentLoop"

        // Token 预算配置
        private const val CONTEXT_WINDOW_TOKENS = 65_536
        private const val MAX_COMPLETION_TOKENS = 4_096
        private const val SAFETY_BUFFER = 1_024

        @Volatile
        private var INSTANCE: AgentLoop? = null

        fun getInstance(context: android.content.Context): AgentLoop {
            return INSTANCE ?: synchronized(this) {
                val instance = AgentLoop(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // 核心组件
    private val sessionManager: SessionManager = SessionManager.getInstance(context)
    private val memoryStore: MemoryStore = MemoryStore.getInstance(context)
    private val toolRegistry: ToolRegistry = ToolRegistry.getInstance()
    private val apiKeyManager: ApiKeyManager = ApiKeyManager(context)

    // LLM 客户端（懒加载）
    private var llmClient: LlmClient? = null
    private var llmModel: String = "qwen-max"

    // 状态
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    // 流式输出回调
    var onStreamToken: ((token: String, type: StreamType) -> Unit)? = null

    enum class StreamType {
        THOUGHT,
        ACTION,
        FINAL_ANSWER
    }

    // 后台任务 Scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 初始化器：注册所有工具
    init {
        initializeTools()
    }

    /**
     * 初始化工具注册表
     */
    private fun initializeTools() {
        val scanner = AccessibilityScanner.instance
        if (scanner != null) {
            toolRegistry.register(ClickTool(scanner), aliases = arrayOf("tap", "press"))
            toolRegistry.register(TypeTool(scanner))
            toolRegistry.register(NavigateTool(scanner))
            Log.d(TAG, "Tools initialized with scanner instance")
        } else {
            Log.w(TAG, "AccessibilityScanner not available, tools will be limited")
        }

        // 注册不需要 scanner 的工具
        toolRegistry.register(SwipeTool())
        toolRegistry.register(OpenAppTool())

        Log.d(TAG, "Tool registry initialized with ${toolRegistry.getToolNames().size} tools")
    }

    /**
     * 处理用户消息（主入口）
     * @param sessionKey 会话标识
     * @param userMessage 用户消息
     * @param onProgress 进度回调（思考中、执行动作）
     * @return 最终回答
     */
    suspend fun processMessage(
        sessionKey: String,
        userMessage: String,
        onProgress: suspend (content: String, isToolHint: Boolean) -> Unit = { _, _ -> }
    ): String? {
        Log.d(TAG, "Processing message for session: $sessionKey")
        _isRunning.value = true

        try {
            // 1. 获取或创建会话
            val session = sessionManager.getOrCreateSession(sessionKey)
            _currentSession.value = session

            // 2. 检查是否需要记忆整合（token 预检查）
            if (sessionManager.needsConsolidation(session)) {
                Log.d(TAG, "Session needs consolidation, pruning old messages")
                val budget = CONTEXT_WINDOW_TOKENS - MAX_COMPLETION_TOKENS - SAFETY_BUFFER
                session.pruneOldMessages(budget / 2)
            }

            // 3. 添加用户消息到会话
            session.addMessage("user", userMessage)

            // 4. 构建 LLM 请求消息
            val messages = buildMessages(session, userMessage)

            // 5. 调用 LLM（流式输出）
            val (thought, action, finalAnswer) = callLLM(messages, onProgress)

            // 6. 添加 AI 响应到会话
            session.addMessage("assistant", finalAnswer ?: "", mapOf(
                "thought" to thought,
                "action" to action?.toString()
            ))

            // 7. 执行动作（如果有）
            action?.let {
                Log.d(TAG, "Executing action: $it")
                onProgress("执行动作：$it", true)
                val result = executeAction(it)
                Log.d(TAG, "Action result: $result")

                // 添加观察结果到会话
                session.addMessage("user", "Observation: $result")
            }

            // 8. 保存会话（异步）
            scope.launch {
                sessionManager.saveSession(session)

                // 后台记忆整合
                val recentMessages = session.getHistory(10)
                    .map { ReActMessage(it["role"] as String, it["content"] as String) }
                memoryStore.consolidate(recentMessages, finalAnswer)
            }

            _isRunning.value = false
            return finalAnswer

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
            _isRunning.value = false
            throw e
        }
    }

    /**
     * 构建消息列表（包含系统提示、历史、当前上下文）
     */
    private fun buildMessages(session: Session, currentMessage: String): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()

        // 1. 系统提示词
        messages.add(mapOf("role" to "system", "content" to buildSystemPrompt()))

        // 2. 长期记忆（如果有）
        val memoryContext = kotlinx.coroutines.runBlocking {
            memoryStore.getMemoryContext()
        }
        if (memoryContext.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to memoryContext))
        }

        // 3. 历史对话
        session.getHistory().forEach { msg ->
            val role = msg["role"] as? String ?: return@forEach
            val content = msg["content"] as? String ?: return@forEach
            if (role in listOf("user", "assistant")) {
                messages.add(mapOf("role" to role, "content" to content))
            }
        }

        // 4. 当前屏幕上下文
        val uiContext = getCurrentUiContext()
        if (uiContext.isNotBlank()) {
            messages.add(mapOf("role" to "user", "content" to uiContext))
        }

        // 5. 当前消息
        messages.add(mapOf("role" to "user", "content" to currentMessage))

        return messages
    }

    /**
     * 构建系统提示词
     */
    private fun buildSystemPrompt(): String {
        val toolDefinitions = toolRegistry.getToolDefinitions()
            .joinToString("\n\n") { tool ->
                "${tool.name}(${tool.parameters.joinToString(", ") { p -> "${p.name}: ${p.type}" }}) - ${tool.description}"
            }

        return """
你是 AI 手机助手，用自然、友好的方式与用户交流。

## 可用工具
$toolDefinitions

## 工作方式
1. 思考用户需要什么
2. 如果需要操作，调用一个工具
3. 如果只是聊天，直接回答

## 输出格式

需要操作时：
Thought: 理解用户意图，决定做什么
Action: toolName(param1="value1")

直接回答时：
Thought: 理解用户意图
Final Answer: 自然、友好的回复

## 记住
- 聊天直接回答，不需要操作
- 一次只调用一个工具
- 像朋友一样交流，不要太机械
""".trimIndent()
    }

    /**
     * 获取当前屏幕上下文
     */
    private fun getCurrentUiContext(): String {
        return try {
            val uiTree = AccessibilityScanner.getUiTreeSync()
            if (uiTree != null && uiTree.nodes.isNotEmpty()) {
                "当前屏幕内容：\n${uiTree.toJson()}"
            } else {
                val packageName = AccessibilityScanner.instance?.packageName?.toString() ?: "未知"
                "[当前在 AI 助手应用界面，无法获取屏幕内容]"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UI context", e)
            "[屏幕内容暂时不可用]"
        }
    }

    /**
     * 调用 LLM（流式输出）
     *
     * @param messages 消息列表
     * @param onProgress 进度回调
     * @return Triple(thought, action, finalAnswer)
     */
    private suspend fun callLLM(
        messages: List<Map<String, String>>,
        onProgress: suspend (content: String, isToolHint: Boolean) -> Unit
    ): Triple<String?, String?, String?> {
        Log.d(TAG, "LLM call with ${messages.size} messages")

        try {
            // 1. 初始化 LLM 客户端（如果尚未初始化）
            val client = getOrCreateLlmClient()
                ?: return Triple(null, null, "未配置 API Key，请在设置中配置大模型 API")

            // 2. 转换为 ChatRequest 格式
            val chatMessages = messages.map { msg ->
                Message(role = msg["role"]!!, content = msg["content"]!!)
            }

            val request = ChatRequest(
                model = llmModel,
                messages = chatMessages,
                temperature = 0.7f,
                maxTokens = 2048
            )

            Log.d(TAG, "Sending request to LLM: model=$llmModel, messages=${messages.size}")

            // 3. 流式调用 LLM
            val fullContent = client.streamChatCompletion(request) { token ->
                // 流式回调每个 token（非 suspend 方式调用）
                scope.launch {
                    onProgress(token, false)
                }
            }

            Log.d(TAG, "LLM response received: ${fullContent.length} chars")

            // 4. 解析响应
            val thought = extractThought(fullContent)
            val action = extractAction(fullContent)
            val answer = extractFinalAnswer(fullContent)

            // 如果没有 Final Answer 但响应看起来是完整的回答，直接作为答案
            if (answer == null && action == null && fullContent.isNotBlank()) {
                val isChatResponse = isChatResponse(fullContent)
                if (isChatResponse) {
                    return Triple(thought.ifEmpty { "理解用户意图" }, null, fullContent)
                }
            }

            return Triple(
                thought.ifEmpty { null },
                action,
                answer ?: fullContent
            )

        } catch (e: Exception) {
            Log.e(TAG, "LLM call failed", e)
            return Triple(null, null, "LLM 调用失败：${e.message}")
        }
    }

    /**
     * 获取或创建 LLM 客户端
     */
    private suspend fun getOrCreateLlmClient(): LlmClient? {
        if (llmClient != null) return llmClient

        // 从 ApiKeyManager 获取 API Key
        val apiKey = apiKeyManager.getApiKey("aliyun-bailian")
            ?: apiKeyManager.getApiKey("dashscope")
            ?: apiKeyManager.getApiKey("deepseek")
            ?: apiKeyManager.getApiKey("kimi")
            ?: return null

        // 默认使用阿里云百炼
        val config = VendorConfig.dashscope(apiKey)
        llmClient = OpenAiCompatibleClient(config)
        llmModel = config.selectedModel ?: "qwen-max"

        Log.d(TAG, "LLM client initialized: model=$llmModel")
        return llmClient
    }

    /**
     * 从响应中提取思考
     */
    private fun extractThought(response: String): String {
        val thoughtMatch = Regex("Thought:\\s*(.+?)(?=\\n|Action|Final Answer|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        return thoughtMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    /**
     * 从响应中提取动作
     */
    private fun extractAction(response: String): String? {
        val actionMatch = Regex("Action:\\s*(.+)")
            .find(response)
        return actionMatch?.groupValues?.get(1)?.trim()
    }

    /**
     * 从响应中提取最终答案
     */
    private fun extractFinalAnswer(response: String): String? {
        val match = Regex("Final Answer:\\s*(.+)")
            .find(response)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * 判断是否为聊天响应（不需要执行动作）
     */
    private fun isChatResponse(response: String): Boolean {
        val chatKeywords = listOf("你好", "您好", "嗨", "hello", "hi", "谢谢", "再见", "叫什么", "是谁", "天气", "时间", "名字")
        val actionKeywords = listOf("点击", "打开", "滑动", "输入", "返回", "主页", "swipe", "click", "open", "type")

        // 包含聊天关键词且不包含动作关键词
        val hasChatKeyword = chatKeywords.any { it in response.lowercase() }
        val hasActionKeyword = actionKeywords.any { it in response.lowercase() }

        return hasChatKeyword && !hasActionKeyword
    }

    /**
     * 执行工具调用
     *
     * @param action 动作字符串，如 "click(target=\"微信\")"
     * @return 执行结果
     */
    private suspend fun executeAction(action: String): String {
        Log.d(TAG, "Executing action: $action")

        try {
            // 解析动作字符串
            val (toolName, args) = parseActionString(action)
                ?: return "无法解析动作：$action"

            Log.d(TAG, "Parsed action: tool=$toolName, args=$args")

            // 调用工具注册表执行
            val result = toolRegistry.execute(toolName, args)

            return if (result.success) {
                "执行成功：${result.message}"
            } else {
                "执行失败：${result.message}"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed", e)
            return "执行异常：${e.message}"
        }
    }

    /**
     * 解析动作字符串
     * @param action 动作字符串，如 "click(target=\"微信\")"
     * @return Pair(工具名，参数 Map) 或 null
     */
    private fun parseActionString(action: String): Pair<String, Map<String, Any?>>? {
        // 匹配 toolName(args) 格式
        val match = Regex("([a-zA-Z]+)\\((.*)\\)").find(action)
            ?: return null

        val toolName = match.groupValues[1]
        val argsString = match.groupValues[2]

        // 解析参数
        val args = mutableMapOf<String, Any?>()

        if (argsString.isBlank()) {
            return Pair(toolName, args)
        }

        // 解析 key=value 格式的参数
        val paramRegex = """([a-zA-Z]+)="([^"]*)"""".toRegex()
        for (paramMatch in paramRegex.findAll(argsString)) {
            val key = paramMatch.groupValues[1]
            val value = paramMatch.groupValues[2]
            args[key] = value
        }

        // 如果没有解析到具名参数，尝试位置参数（逗号分隔）
        if (args.isEmpty()) {
            val positionalArgs = argsString.split(",").map { it.trim() }
            if (positionalArgs.isNotEmpty()) {
                // 对于简单的单参数工具，如 click(微信)，使用 target 作为参数名
                args["target"] = positionalArgs.joinToString(",")
            }
        }

        return Pair(toolName, args)
    }

    /**
     * 取消当前处理
     */
    fun cancel() {
        Log.d(TAG, "Cancelling current processing")
        _isRunning.value = false
    }

    /**
     * 清空会话
     */
    suspend fun clearSession(sessionKey: String) {
        sessionManager.deleteSession(sessionKey)
        _currentSession.value = null
        Log.d(TAG, "Session cleared: $sessionKey")
    }

    /**
     * 获取会话历史
     */
    suspend fun getSessionHistory(sessionKey: String, limit: Int = 50): List<Map<String, Any?>> {
        return sessionManager.loadSessionHistory(sessionKey, limit)
    }
}

// 辅助函数已移除，直接使用 kotlinx.coroutines.runBlocking

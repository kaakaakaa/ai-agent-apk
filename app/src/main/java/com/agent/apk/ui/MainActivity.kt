// File: app/src/main/java/com/agent/apk/ui/MainActivity.kt
package com.agent.apk.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agent.apk.R
import com.agent.apk.agent.AgentService
import com.agent.apk.agent.AgentStatus
import com.agent.apk.agent.AgentType
import com.agent.apk.model.AppSettings
import com.agent.apk.infra.ApiKeyManager
import com.agent.apk.infra.ConversationHistoryManager
import com.agent.apk.infra.ConversationHistoryEntity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面：用户与 Agent 的聊天交互入口
 */
class MainActivity : AppCompatActivity() {

    private lateinit var agentService: AgentService
    private lateinit var appSettings: AppSettings
    private lateinit var apiKeyManager: ApiKeyManager

    // UI 组件
    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var modelIndicator: TextView
    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var quickOpenWechat: MaterialButton
    private lateinit var quickOpenSettings: MaterialButton
    private lateinit var quickReturn: MaterialButton
    private lateinit var settingsButton: MaterialButton

    // 消息适配器
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<MessageItem>()

    private var isTaskRunning = false

    // 聊天历史管理器
    private val historyManager by lazy { ConversationHistoryManager.getInstance(this) }

    // 当前会话 ID（保持不变，用于持久化）
    private var currentSessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化组件
        appSettings = AppSettings(this)
        apiKeyManager = ApiKeyManager(this)

        // 获取或创建会话 ID
        currentSessionId = historyManager.getCurrentSessionId()

        bindViews()
        setupMessageList()
        setupListeners()

        // 加载历史聊天记录
        loadChatHistory()

        // 如果没有历史记录，添加欢迎消息
        if (messages.isEmpty()) {
            addWelcomeMessage()
        }

        // 自动初始化 Agent 服务
        autoInitializeAgentService()
    }

    override fun onResume() {
        super.onResume()
        // 重新检查 Agent 状态，因为用户可能在 SettingsActivity 中修改了配置
        updateAgentStatus()
    }

    /**
     * 自动初始化 Agent 服务并显示状态
     */
    private fun autoInitializeAgentService() {
        // 先显示"初始化中..."状态
        statusDot.setBackgroundResource(R.drawable.circle_green)
        statusText.text = "初始化中..."
        modelIndicator.text = "请稍候"

        lifecycleScope.launch {
            try {
                agentService = AgentService.getInstance(this@MainActivity)

                // 检查是否需要初始化
                if (!agentService.isInitialized.value) {
                    // 检查 API Key 是否配置
                    val apiKey = apiKeyManager.getApiKey(appSettings.selectedVendorId)
                    if (apiKey.isNullOrBlank()) {
                        // 未配置 API Key，显示引导
                        showNeedsConfig()
                        return@launch
                    }

                    // 自动初始化
                    try {
                        agentService.initialize()
                        Log.d("MainActivity", "Auto initialization successful")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Auto initialization failed", e)
                        showNeedsConfig()
                        return@launch
                    }
                }

                // 初始化成功，更新状态（切换到主线程）
                withContext(Dispatchers.Main) {
                    updateAgentStatus()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to get agent service", e)
                withContext(Dispatchers.Main) {
                    showNeedsConfig()
                }
            }
        }
    }

    /**
     * 显示需要配置的状态
     */
    private fun showNeedsConfig() {
        statusDot.setBackgroundResource(R.drawable.circle_red)
        statusText.text = "未配置 API Key"
        modelIndicator.text = "点击设置→配置"
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        modelIndicator = findViewById(R.id.modelIndicator)
        messageList = findViewById(R.id.messageList)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        quickOpenWechat = findViewById(R.id.quickOpenWechat)
        quickOpenSettings = findViewById(R.id.quickOpenSettings)
        quickReturn = findViewById(R.id.quickReturn)
        settingsButton = findViewById(R.id.settingsButton)

        setSupportActionBar(toolbar)
    }

    private fun setupMessageList() {
        messageAdapter = MessageAdapter(messages)
        messageList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 从底部开始显示
        }
        messageList.adapter = messageAdapter
        // 自动滚动到最新消息
        scrollToBottom()
    }

    private fun scrollToBottom() {
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                messageList.scrollToPosition(messages.size - 1)
            }
        })
    }

    private fun setupListeners() {
        // 发送按钮
        sendButton.setOnClickListener {
            sendMessage()
        }

        // 快捷指令
        quickOpenWechat.setOnClickListener {
            sendQuickCommand("打开微信")
        }
        quickOpenSettings.setOnClickListener {
            sendQuickCommand("打开设置")
        }
        quickReturn.setOnClickListener {
            sendQuickCommand("返回")
        }

        // 设置按钮
        settingsButton.setOnClickListener {
            openSettings()
        }

        // 状态栏点击 - 打开设置
        findViewById<View>(R.id.statusBar).setOnClickListener {
            openSettings()
        }

        // 回车发送
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // 工具栏菜单点击
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    openSettings()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 打开设置界面
     */
    private fun openSettings() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun addWelcomeMessage() {
        messages.add(
            MessageItem(
                role = "assistant",
                content = "嗨～我是你的 AI 助手！\n\n我可以帮你操作手机，比如：\n• 打开微信、支付宝\n• 发送消息\n• 查看天气\n• 设置闹钟\n\n直接告诉我想做什么就好～",
                thought = null,
                action = null,
                isStreaming = false
            )
        )
        messageAdapter.notifyDataSetChanged()
    }

    /**
     * 加载历史聊天记录
     */
    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                val history = historyManager.getRecentConversations(100)
                if (history.isNotEmpty()) {
                    Log.d("MainActivity", "Loaded ${history.size} chat history entries")
                    // 按时间正序排列
                    val sortedHistory = history.sortedBy { it.timestamp }
                    sortedHistory.forEach { entity ->
                        messages.add(
                            MessageItem(
                                role = entity.role,
                                content = entity.content,
                                thought = entity.thought,
                                action = entity.action
                            )
                        )
                    }
                    messageAdapter.notifyDataSetChanged()
                    messageList.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load chat history", e)
            }
        }
    }

    /**
     * 保存消息到数据库
     */
    private fun saveMessageToHistory(role: String, content: String, thought: String?, action: String?, completed: Boolean = false) {
        lifecycleScope.launch {
            try {
                historyManager.saveMessage(
                    sessionId = currentSessionId,
                    role = role,
                    content = content,
                    thought = thought,
                    action = action,
                    taskCompleted = completed
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to save message", e)
            }
        }
    }

    private fun sendMessage() {
        val inputText = messageInput.text.toString().trim()
        if (inputText.isEmpty()) {
            Toast.makeText(this, "说点什么吧～", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTaskRunning) {
            Toast.makeText(this, "稍等一下，正在处理中...", Toast.LENGTH_SHORT).show()
            return
        }

        // 添加用户消息
        messages.add(
            MessageItem(
                role = "user",
                content = inputText,
                thought = null,
                action = null,
                isStreaming = false
            )
        )
        messageAdapter.notifyDataSetChanged()
        messageInput.text.clear()

        // 保存用户消息到数据库
        saveMessageToHistory("user", inputText, null, null, false)

        // 执行任务
        executeTask(inputText)
    }

    private fun sendQuickCommand(command: String) {
        if (isTaskRunning) {
            Toast.makeText(this, "稍等一下～", Toast.LENGTH_SHORT).show()
            return
        }

        // 添加用户消息
        messages.add(
            MessageItem(
                role = "user",
                content = command,
                thought = null,
                action = null,
                isStreaming = false
            )
        )
        messageAdapter.notifyDataSetChanged()

        // 执行任务
        executeTask(command)
    }

    // 用于监听 ReAct 引擎状态
    private var reActStateJob: kotlinx.coroutines.Job? = null
    // 后台任务 Scope，用于异步执行任务（不阻塞 UI）
    private val taskScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    private fun executeTask(command: String) {
        isTaskRunning = true
        updateSendButtonState()

        // 添加思考中的消息（占位）
        val thinkingId = messages.size
        messages.add(
            MessageItem(
                role = "assistant",
                content = "",
                thought = "正在初始化...",
                action = null,
                isStreaming = true
            )
        )
        messageAdapter.notifyDataSetChanged()
        messageList.scrollToPosition(messages.size - 1)

        // 在后台线程池执行任务，完全不阻塞 UI
        taskScope.launch {
            try {
                // 初始化 Agent 服务
                agentService = AgentService.getInstance(this@MainActivity)

                // 如果服务未初始化，先初始化
                if (!agentService.isInitialized.value) {
                    try {
                        agentService.initialize()
                    } catch (e: Exception) {
                        val errorMsg = buildString {
                            appendLine("哎呀，初始化遇到问题了～")
                            appendLine()
                            when {
                                e.message?.contains("AccessibilityService") == true -> {
                                    appendLine("需要开启无障碍权限哦！")
                                    appendLine()
                                    appendLine("开启步骤：")
                                    appendLine("1. 打开 设置 → 无障碍 → 已下载的服务")
                                    appendLine("2. 找到 **AI Agent** 并开启")
                                    appendLine("3. 返回应用重试")
                                }
                                e.message?.contains("API key") == true || e.message?.contains("API Key") == true -> {
                                    appendLine("需要配置 API Key！")
                                    appendLine()
                                    appendLine("配置步骤：")
                                    appendLine("1. 点击底部 **⚙️ 设置** 按钮")
                                    appendLine("2. 选择大模型厂商（推荐阿里云百炼）")
                                    appendLine("3. 输入 API Key 并保存")
                                }
                                else -> {
                                    appendLine("请确保：")
                                    appendLine("1. 已配置 API Key")
                                    appendLine("2. 已开启无障碍权限")
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            messages[thinkingId] = MessageItem(
                                role = "assistant",
                                content = errorMsg,
                                thought = null,
                                action = null,
                                isStreaming = false
                            )
                            messageAdapter.notifyDataSetChanged()
                            messageList.scrollToPosition(messages.size - 1)
                            isTaskRunning = false
                            updateSendButtonState()
                        }
                        return@launch
                    }
                }

                // 检查云端 Agent 是否就绪
                val cloudStatus = agentService.getCloudAgentStatus()
                if (cloudStatus == com.agent.apk.agent.AgentStatus.NO_API_KEY) {
                    withContext(Dispatchers.Main) {
                        messages[thinkingId] = MessageItem(
                            role = "assistant",
                            content = buildString {
                                appendLine("还没配置 API Key 哦～")
                                appendLine()
                                appendLine("配置步骤：")
                                appendLine("1. 点击底部 **⚙️ 设置** 按钮")
                                appendLine("2. 选择大模型厂商（推荐阿里云百炼）")
                                appendLine("3. 输入 API Key 并保存")
                                appendLine()
                                appendLine("国内可用厂商：")
                                appendLine("• 阿里云百炼：https://bailian.console.aliyun.com/")
                                appendLine("• DeepSeek：https://www.deepseek.com/")
                                appendLine("• Kimi：https://kimi.moonshot.cn/")
                            },
                            thought = null,
                            action = null,
                            isStreaming = false
                        )
                        messageAdapter.notifyDataSetChanged()
                        isTaskRunning = false
                        updateSendButtonState()
                    }
                    return@launch
                }

                // 如果云端 Agent 未就绪，重新加载配置
                if (!agentService.isCloudAgentReady.value) {
                    android.util.Log.d("MainActivity", "CloudAgent not ready, reloading config...")
                    agentService.reloadCloudConfig()
                }

                updateAgentStatus()

                if (!agentService.isCloudAgentReady.value) {
                    withContext(Dispatchers.Main) {
                        messages[thinkingId] = MessageItem(
                            role = "assistant",
                            content = buildString {
                                appendLine("云端模型初始化失败了～")
                                appendLine()
                                appendLine("可能原因：")
                                appendLine("1. API Key 格式不正确")
                                appendLine("2. 网络连接问题")
                                appendLine("3. 所选厂商服务不可用")
                                appendLine()
                                appendLine("请尝试：")
                                appendLine("1. 点击底部 **⚙️ 设置** 按钮")
                                appendLine("2. 重新保存 API Key")
                                appendLine("3. 返回重试")
                            },
                            thought = null,
                            action = null,
                            isStreaming = false
                        )
                        messageAdapter.notifyDataSetChanged()
                        isTaskRunning = false
                        updateSendButtonState()
                    }
                    return@launch
                }

                // 获取 ReActEngine 实例以监听状态
                val reActEngineField = agentService::class.java.getDeclaredField("_reActEngine")
                reActEngineField.isAccessible = true
                val reActEngine = reActEngineField.get(agentService) as? com.agent.apk.agent.cloud.ReActEngine

                // 用于流式输出的 StringBuilder
                val streamingThought = StringBuilder()
                val streamingAction = StringBuilder()
                val streamingFinalAnswer = StringBuilder()

                // 订阅 ReAct 引擎的流式输出
                if (reActEngine != null) {
                    // 设置流式回调
                    reActEngine.onStreamUpdate = { text, type ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                when (type) {
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.THOUGHT_START -> {
                                        // 开始思考，清空之前的内容
                                        streamingThought.clear()
                                        streamingAction.clear()
                                        streamingFinalAnswer.clear()
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = "",
                                                thought = "",
                                                action = null,
                                                isStreaming = true
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.THOUGHT_TOKEN -> {
                                        // 思考中的每个 token
                                        streamingThought.append(text)
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = "",
                                                thought = streamingThought.toString(),
                                                action = null,
                                                isStreaming = true
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.ACTION_START -> {
                                        // 开始执行动作
                                        streamingAction.clear()
                                        streamingAction.append(text)
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = "",
                                                thought = streamingThought.toString(),
                                                action = text,
                                                isStreaming = true
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.ACTION_TOKEN -> {
                                        // 动作执行中的 token
                                        streamingAction.append(text)
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = "",
                                                thought = streamingThought.toString(),
                                                action = streamingAction.toString(),
                                                isStreaming = true
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.ANSWER_TOKEN -> {
                                        // 最终回答的 token
                                        streamingFinalAnswer.append(text)
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = streamingFinalAnswer.toString(),
                                                thought = streamingThought.toString(),
                                                action = streamingAction.toString().takeIf { it.isNotEmpty() },
                                                isStreaming = true
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                    com.agent.apk.agent.cloud.ReActEngine.StreamUpdateType.COMPLETE -> {
                                        // 流式输出完成
                                        val finalContent = if (streamingFinalAnswer.isNotEmpty()) {
                                            streamingFinalAnswer.toString()
                                        } else {
                                            text
                                        }
                                        messages.getOrNull(thinkingId)?.let {
                                            messages[thinkingId] = MessageItem(
                                                role = "assistant",
                                                content = finalContent,
                                                thought = null,
                                                action = null,
                                                isStreaming = false
                                            )
                                            messageAdapter.notifyDataSetChanged()
                                            messageList.scrollToPosition(messages.size - 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 执行任务并获取结果
                val result = agentService.executeTask(command)

                // 确保流式输出已完成（如果没有通过 COMPLETE 回调完成）
                withContext(Dispatchers.Main) {
                    val finalMessage = if (streamingFinalAnswer.isNotEmpty()) {
                        streamingFinalAnswer.toString()
                    } else {
                        result ?: "任务已完成"
                    }

                    // 确保消息状态为非流式
                    messages.getOrNull(thinkingId)?.let {
                        if (it.isStreaming) {
                            messages[thinkingId] = MessageItem(
                                role = "assistant",
                                content = finalMessage,
                                thought = null,
                                action = null,
                                isStreaming = false
                            )
                            messageAdapter.notifyDataSetChanged()
                            messageList.scrollToPosition(messages.size - 1)
                        }
                    }

                    // 保存 AI 回答到数据库
                    saveMessageToHistory("assistant", finalMessage, null, null, true)
                }

            } catch (e: Exception) {
                reActStateJob?.cancel()
                reActStateJob = null

                withContext(Dispatchers.Main) {
                    val errorMsg = "哎呀，执行出错了：${e.message}"
                    messages[thinkingId] = MessageItem(
                        role = "assistant",
                        content = errorMsg,
                        thought = null,
                        action = null,
                        isStreaming = false
                    )
                    messageAdapter.notifyDataSetChanged()
                    messageList.scrollToPosition(messages.size - 1)
                    android.util.Log.e("MainActivity", "executeTask failed", e)
                    Toast.makeText(this@MainActivity, "出错了，请稍后重试", Toast.LENGTH_SHORT).show()

                    // 保存错误消息到数据库
                    saveMessageToHistory("assistant", errorMsg, null, null, true)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTaskRunning = false
                    updateSendButtonState()
                }
            }
        }
    }

    private fun updateAgentStatus() {
        try {
            agentService = AgentService.getInstance(this)

            // 检查服务是否已初始化
            if (!agentService.isInitialized.value) {
                statusDot.setBackgroundResource(R.drawable.circle_red)
                statusText.text = "正在初始化..."
                modelIndicator.text = "请稍候"
                return
            }

            val agentType = agentService.getCurrentAgentType()
            val cloudStatus = agentService.getCloudAgentStatus()
            val localStatus = agentService.getLocalAgentStatus()
            val debugInfo = agentService.getDebugStatus()
            android.util.Log.d("MainActivity", "Status: $debugInfo")

            when (agentType) {
                AgentType.CLOUD -> {
                    statusDot.setBackgroundResource(R.drawable.circle_green)
                    statusText.text = "准备就绪"
                    modelIndicator.text = "云端模型"
                }
                AgentType.LOCAL -> {
                    statusDot.setBackgroundResource(R.drawable.circle_green)
                    statusText.text = "准备就绪"
                    modelIndicator.text = "本地模型"
                }
                AgentType.NONE -> {
                    statusDot.setBackgroundResource(R.drawable.circle_red)
                    // 显示具体原因
                    statusText.text = when {
                        cloudStatus == com.agent.apk.agent.AgentStatus.NO_API_KEY -> "未配置 API Key"
                        localStatus == com.agent.apk.agent.AgentStatus.DEVICE_NOT_SUPPORTED -> "设备不支持"
                        !agentService.isInitialized.value -> "未初始化"
                        else -> "未就绪"
                    }
                    modelIndicator.text = when {
                        cloudStatus == com.agent.apk.agent.AgentStatus.NO_API_KEY -> "点击设置配置"
                        localStatus == com.agent.apk.agent.AgentStatus.DEVICE_NOT_SUPPORTED -> "需云端模型"
                        !agentService.isInitialized.value -> "请稍候"
                        else -> "无可用模型"
                    }
                }
            }
        } catch (e: Exception) {
            statusDot.setBackgroundResource(R.drawable.circle_red)
            statusText.text = "错误：${e.message}"
            modelIndicator.text = "查看日志"
            android.util.Log.e("MainActivity", "updateAgentStatus failed", e)
        }
    }

    private fun updateSendButtonState() {
        sendButton.isEnabled = !isTaskRunning
        sendButton.text = if (isTaskRunning) "执行中..." else "发送"
    }
}

/**
 * 消息数据类
 */
data class MessageItem(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thought: String?,
    val action: String?,
    val isStreaming: Boolean = false  // 标记是否是流式更新中
)

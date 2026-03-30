// File: app/src/main/java/com/agent/apk/agent/AgentService.kt
package com.agent.apk.agent

import android.content.Context
import com.agent.apk.agent.cloud.CloudAgent
import com.agent.apk.agent.cloud.LlmClient
import com.agent.apk.agent.cloud.OpenAiCompatibleClient
import com.agent.apk.agent.cloud.ReActEngine
import com.agent.apk.action.AccessibilityActionExecutor
import com.agent.apk.infra.ApiKeyManager
import com.agent.apk.infra.DeviceCapabilityDetector
import com.agent.apk.infra.DeviceCapabilityProvider
import com.agent.apk.infra.DeviceTier
import com.agent.apk.infra.NetworkMonitor
import com.agent.apk.model.AppSettings
import com.agent.apk.model.SessionStatus
import com.agent.apk.model.Task
import com.agent.apk.model.VendorConfig
import com.agent.apk.agent.local.LocalAgent
import com.agent.apk.agent.local.ModelManager
import com.agent.apk.perception.AccessibilityScanner
import com.agent.apk.ui.AgentOverlay
import com.agent.apk.util.AgentLogger
import com.agent.apk.util.LoggerByLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Agent 服务管理器
 *
 * 统一管理所有 Agent 相关的服务，提供依赖注入和功能访问入口
 *
 * 使用示例:
 * ```
 * val agentService = AgentService.getInstance()
 * agentService.executeTask("打开微信")
 * ```
 */
class AgentService private constructor(
    private val context: Context
) : LoggerByLog {

    // 核心组件（懒加载）
    private val apiKeyManager: ApiKeyManager by lazy { ApiKeyManager(context) }
    private val deviceCapabilityDetector: DeviceCapabilityDetector by lazy { DeviceCapabilityDetector(context) }
    private val appSettings: AppSettings by lazy { AppSettings(context) }
    private val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context) }
    private val modelManager: ModelManager by lazy { ModelManager(context) }

    // AgentOverlay 悬浮窗
    private var agentOverlay: AgentOverlay? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 执行器
    private val actionExecutor: ActionExecutor by lazy {
        AccessibilityScanner.instance?.let {
            AccessibilityActionExecutor(it)
        } ?: run {
            log.e("AgentService", "AccessibilityScanner service not enabled")
            throw IllegalStateException("AccessibilityScanner service not enabled")
        }
    }

    // LLM 客户端（根据配置动态创建）
    private var _llmClient: LlmClient? = null
    private var _cloudAgent: CloudAgent? = null
    private var _reActEngine: ReActEngine? = null
    private var _localAgent: LocalAgent? = null

    // 状态
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isCloudAgentReady = MutableStateFlow(false)
    val isCloudAgentReady: StateFlow<Boolean> = _isCloudAgentReady.asStateFlow()

    private val _isLocalAgentReady = MutableStateFlow(false)
    val isLocalAgentReady: StateFlow<Boolean> = _isLocalAgentReady.asStateFlow()

    // 错误状态
    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()

    /**
     * 初始化 Agent 服务
     * 调用此方法会：
     * 1. 检查无障碍服务是否启用
     * 2. 加载用户配置
     * 3. 初始化云端 Agent（如果配置了 API Key）
     * 4. 初始化本地 Agent（如果设备支持）
     */
    suspend fun initialize() {
        log.i("AgentService", "Initializing AgentService...")
        try {
            // 检查无障碍服务
            if (AccessibilityScanner.instance == null) {
                val error = IllegalStateException("AccessibilityService not enabled")
                log.e("AgentService", "AccessibilityService not enabled", error)
                _lastError.value = error
                throw error
            }
            log.i("AgentService", "AccessibilityService check passed")

            // 初始化云端 Agent
            initializeCloudAgent()

            // 初始化本地 Agent
            initializeLocalAgent()

            _isInitialized.value = true
            log.i("AgentService", "AgentService initialized successfully")
        } catch (e: Exception) {
            log.e("AgentService", "Failed to initialize AgentService", e)
            _lastError.value = e
            _isInitialized.value = false
            throw e
        }
    }

    /**
     * 初始化云端 Agent
     */
    private suspend fun initializeCloudAgent() {
        log.d("AgentService", "Initializing CloudAgent...")
        try {
            val vendorId = appSettings.selectedVendorId
            val apiKey = apiKeyManager.getApiKey(vendorId)

            if (apiKey.isNullOrBlank()) {
                log.w("AgentService", "No API key configured for vendor: $vendorId")
                _isCloudAgentReady.value = false
                return
            }

            val config = VendorConfig.getVendorConfigById(vendorId, apiKey)
            log.d("AgentService", "Using vendor: ${config.name}, model: ${config.models.firstOrNull()}")

            val llmClient = OpenAiCompatibleClient(config)
            _llmClient = llmClient

            _cloudAgent = CloudAgent(
                context = context,
                llmClient = llmClient,
                model = config.models.firstOrNull() ?: "default",
                supportsVision = config.supportsVision
            )

            _reActEngine = ReActEngine(
                cloudAgent = _cloudAgent!!,
                actionExecutor = actionExecutor
            )

            // 初始化 AgentOverlay 并订阅状态
            initializeAgentOverlay()

            _isCloudAgentReady.value = true
            log.i("AgentService", "CloudAgent initialized successfully")
        } catch (e: Exception) {
            log.e("AgentService", "Failed to initialize CloudAgent", e)
            _lastError.value = e
            _isCloudAgentReady.value = false
        }
    }

    /**
     * 初始化 AgentOverlay 悬浮窗
     */
    private fun initializeAgentOverlay() {
        try {
            agentOverlay = AgentOverlay(context)

            // 订阅 ReActEngine 状态
            _reActEngine?.let { engine ->
                serviceScope.launch {
                    engine.currentStep.collect { step ->
                        step?.let {
                            agentOverlay?.updateStatus(it, isComplete = it.isComplete)
                        }
                    }
                }

                serviceScope.launch {
                    engine.sessionState.collect { session ->
                        if (session?.status == SessionStatus.COMPLETED ||
                            session?.status == SessionStatus.FAILED
                        ) {
                            agentOverlay?.updateStatus(null, isComplete = true)
                        }
                    }
                }
            }

            log.d("AgentService", "AgentOverlay initialized")
        } catch (e: Exception) {
            log.w("AgentService", "Failed to initialize AgentOverlay: ${e.message}")
            agentOverlay = null
        }
    }

    /**
     * 初始化本地 Agent
     */
    private suspend fun initializeLocalAgent() {
        log.d("AgentService", "Initializing LocalAgent...")
        try {
            val totalRam = deviceCapabilityDetector.getTotalRam()
            val availableStorage = deviceCapabilityDetector.getAvailableStorage()
            val deviceTier = deviceCapabilityDetector.getDeviceTier()

            log.d("AgentService", "Device info: tier=$deviceTier, ram=${totalRam / 1024 / 1024 / 1024}GB, storage=${availableStorage / 1024 / 1024 / 1024}GB")

            // 根据设备能力选择模型
            val selectedModel = modelManager.selectModelForDevice(totalRam, availableStorage)

            if (selectedModel == null) {
                log.w("AgentService", "No suitable local model for this device")
                _isLocalAgentReady.value = false
                return
            }

            log.d("AgentService", "Selected local model: ${selectedModel.name}")

            // 如果用户选择了本地模型，尝试加载
            if (appSettings.agentTarget == AgentTarget.LOCAL.name) {
                val result = modelManager.loadModel(selectedModel.id)
                if (result.isSuccess) {
                    _localAgent = LocalAgent(modelManager, actionExecutor)
                    _isLocalAgentReady.value = true
                    log.i("AgentService", "Local model loaded successfully: ${selectedModel.name}")
                } else {
                    log.e("AgentService", "Failed to load local model", result.exceptionOrNull())
                    _lastError.value = result.exceptionOrNull()
                }
            } else {
                // 仅初始化，不加载模型（按需加载）
                _localAgent = LocalAgent(modelManager, actionExecutor)
                _isLocalAgentReady.value = true
                log.d("AgentService", "LocalAgent initialized (model not loaded yet)")
            }
        } catch (e: Exception) {
            log.e("AgentService", "Failed to initialize LocalAgent", e)
            _lastError.value = e
            _isLocalAgentReady.value = false
        }
    }

    /**
     * 执行任务
     * 自动根据路由决策选择本地或云端 Agent
     * @return 任务执行结果（最终回答）
     */
    suspend fun executeTask(userGoal: String): String? {
        log.i("AgentService", "Executing task: $userGoal")

        if (!_isInitialized.value) {
            val error = IllegalStateException("AgentService not initialized")
            log.e("AgentService", "Task execution failed: service not initialized", error)
            throw error
        }

        val task = Task(
            id = java.util.UUID.randomUUID().toString(),
            userInstruction = userGoal
        )
        val target = taskRouter.route(task)
        log.d("AgentService", "Task routed to: $target")

        return when (target) {
            AgentTarget.LOCAL -> {
                executeWithLocalAgent(task)
            }
            AgentTarget.CLOUD -> {
                executeWithCloudAgent(task)
            }
            AgentTarget.AUTO -> {
                // 自动选择：优先云端，失败时降级本地
                if (_isCloudAgentReady.value == true) {
                    log.d("AgentService", "Using CloudAgent (auto)")
                    executeWithCloudAgent(task)
                } else if (_isLocalAgentReady.value == true) {
                    log.d("AgentService", "Using LocalAgent (fallback)")
                    executeWithLocalAgent(task)
                } else {
                    val error = AgentNotReadyException("No agent available")
                    log.e("AgentService", "No agent available for task execution", error)
                    _lastError.value = error
                    throw error
                }
            }
        }
    }

    /**
     * 使用本地 Agent 执行任务
     */
    private suspend fun executeWithLocalAgent(task: Task): String? {
        val localAgent = _localAgent ?: throw AgentNotReadyException("Local agent not ready")
        try {
            log.d("AgentService", "Executing task with LocalAgent")
            localAgent.execute(task)
            log.i("AgentService", "Task completed by LocalAgent")
            return "任务已完成"
        } catch (e: Exception) {
            log.e("AgentService", "LocalAgent task execution failed", e)
            _lastError.value = e
            throw e
        }
    }

    /**
     * 使用云端 Agent 执行任务（ReAct 模式）
     * @return 任务执行结果（最终回答）
     */
    private suspend fun executeWithCloudAgent(task: Task): String? {
        val reActEngine = _reActEngine ?: throw AgentNotReadyException("Cloud agent not ready")
        try {
            log.d("AgentService", "Executing task with CloudAgent")

            // 显示悬浮窗
            agentOverlay?.show()

            val result = reActEngine.executeTask(task.userInstruction)

            // 任务完成后隐藏悬浮窗
            agentOverlay?.hide()

            log.i("AgentService", "Task completed by CloudAgent, result: $result")
            return result
        } catch (e: Exception) {
            log.e("AgentService", "CloudAgent task execution failed", e)
            agentOverlay?.hide()
            _lastError.value = e
            throw e
        }
    }

    /**
     * 任务分发器
     */
    private val taskRouter: TaskRouter by lazy {
        TaskRouter(
            networkStatus = networkMonitor,
            deviceCapabilityProvider = object : DeviceCapabilityProvider {
                override fun getDeviceTier(): DeviceTier = deviceCapabilityDetector.getDeviceTier()
            },
            settings = appSettings
        )
    }

    /**
     * 重新加载云端配置
     * 当用户更改 API Key 或厂商配置时调用
     */
    suspend fun reloadCloudConfig() {
        log.i("AgentService", "Reloading cloud configuration...")
        initializeCloudAgent()
    }

    /**
     * 获取当前使用的 Agent 类型
     */
    fun getCurrentAgentType(): AgentType {
        return when {
            _isCloudAgentReady.value && appSettings.cloudFirst -> AgentType.CLOUD
            _isLocalAgentReady.value -> AgentType.LOCAL
            else -> AgentType.NONE
        }
    }

    /**
     * 获取云端 Agent 状态
     */
    fun getCloudAgentStatus(): AgentStatus {
        return when {
            _isCloudAgentReady.value -> AgentStatus.READY
            apiKeyManager.getApiKey(appSettings.selectedVendorId).isNullOrBlank() -> AgentStatus.NO_API_KEY
            else -> AgentStatus.ERROR
        }
    }

    /**
     * 获取本地 Agent 状态
     */
    fun getLocalAgentStatus(): AgentStatus {
        return when {
            _isLocalAgentReady.value -> AgentStatus.READY
            deviceCapabilityDetector.getDeviceTier() == DeviceTier.LOW ->
                AgentStatus.DEVICE_NOT_SUPPORTED
            else -> AgentStatus.NOT_LOADED
        }
    }

    /**
     * 获取所有状态信息（用于调试）
     */
    fun getDebugStatus(): String {
        return buildString {
            appendLine("=== AgentService Debug Status ===")
            appendLine("Initialized: ${_isInitialized.value}")
            appendLine("CloudAgent Ready: ${_isCloudAgentReady.value}")
            appendLine("LocalAgent Ready: ${_isLocalAgentReady.value}")
            appendLine("Current Agent Type: ${getCurrentAgentType()}")
            appendLine("Selected Vendor: ${appSettings.selectedVendorId}")
            appendLine("Cloud First: ${appSettings.cloudFirst}")
            appendLine("Device Tier: ${deviceCapabilityDetector.getDeviceTier()}")
            appendLine("Last Error: ${_lastError.value?.message ?: "None"}")
        }
    }

    companion object {
        /**
         * 获取单例实例
         * 必须先调用 [initialize] 初始化
         */
        fun getInstance(context: Context? = null): AgentService {
            if (instance == null) {
                if (context == null) {
                    throw IllegalStateException("AgentService not initialized. Call initialize() first.")
                }
                synchronized(this) {
                    if (instance == null) {
                        instance = AgentService(context.applicationContext)
                    }
                }
            }
            return instance ?: throw IllegalStateException("AgentService instance is null")
        }

        /**
         * 重置实例（用于测试）
         */
        fun resetInstance() {
            instance = null
        }

        private var instance: AgentService? = null
    }
}

/**
 * Agent 类型枚举
 */
enum class AgentType {
    CLOUD,      // 云端 Agent
    LOCAL,      // 本地 Agent
    NONE        // 无可用 Agent
}

/**
 * Agent 状态枚举
 */
enum class AgentStatus {
    READY,              // 已就绪
    NOT_LOADED,         // 未加载
    NO_API_KEY,         // 缺少 API Key
    DEVICE_NOT_SUPPORTED, // 设备不支持
    ERROR               // 错误状态
}

/**
 * Agent 未就绪异常
 */
class AgentNotReadyException(message: String) : Exception(message)

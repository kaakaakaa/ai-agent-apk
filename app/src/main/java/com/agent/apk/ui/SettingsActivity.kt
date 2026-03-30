// File: app/src/main/java/com/agent/apk/ui/SettingsActivity.kt
package com.agent.apk.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agent.apk.R
import com.agent.apk.agent.AgentTarget
import com.agent.apk.agent.cloud.BailianClient
import com.agent.apk.agent.cloud.ConnectionTestResult
import com.agent.apk.agent.local.LocalAgent
import com.agent.apk.agent.local.ModelManager
import com.agent.apk.infra.ApiKeyManager
import com.agent.apk.infra.DeviceCapabilityDetector
import com.agent.apk.model.AppSettings
import com.agent.apk.model.VendorConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

/**
 * 设置界面：配置 API、模型、语音等选项
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var modelManager: ModelManager
    private lateinit var deviceCapabilityDetector: DeviceCapabilityDetector
    private lateinit var appSettings: AppSettings

    // UI 控件
    private lateinit var vendorSpinner: MaterialAutoCompleteTextView
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var modelSpinner: MaterialAutoCompleteTextView
    private lateinit var modelSpinnerLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var testApiButton: MaterialButton
    private lateinit var apiTestStatus: android.widget.TextView
    private lateinit var saveApiKeyButton: MaterialButton
    private lateinit var localModelStatus: android.widget.TextView
    private lateinit var loadLocalModelButton: MaterialButton
    private lateinit var voiceInputSwitch: SwitchMaterial
    private lateinit var voiceOutputSwitch: SwitchMaterial
    private lateinit var modelDownloadHint: android.widget.TextView

    // 厂商列表
    private val vendorList = listOf(
        "阿里云百炼",
        "DeepSeek",
        "Kimi",
        "Azure OpenAI"
    )

    // 当前选择的厂商 ID
    private var currentVendorId: String = "aliyun-bailian"

    // 当前可用的模型列表
    private var availableModels: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("SettingsActivity", "onCreate started")

        try {
            setContentView(R.layout.activity_settings)
            android.util.Log.d("SettingsActivity", "setContentView succeeded")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to set content view: ${e.message}", e)
            Toast.makeText(this, "布局加载失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            android.util.Log.d("SettingsActivity", "Initializing components...")
            // 初始化组件
            apiKeyManager = ApiKeyManager(this)
            android.util.Log.d("SettingsActivity", "ApiKeyManager initialized")
            deviceCapabilityDetector = DeviceCapabilityDetector(this)
            android.util.Log.d("SettingsActivity", "DeviceCapabilityDetector initialized")
            modelManager = ModelManager(this)
            android.util.Log.d("SettingsActivity", "ModelManager initialized")
            appSettings = AppSettings(this)
            android.util.Log.d("SettingsActivity", "AppSettings initialized")

            bindViews()
            android.util.Log.d("SettingsActivity", "bindViews completed")
            setupVendorSpinner()
            android.util.Log.d("SettingsActivity", "setupVendorSpinner completed")

            // 同步加载设置，不使用协程
            loadSavedSettingsSync()
            android.util.Log.d("SettingsActivity", "loadSavedSettingsSync completed")

            setupListeners()
            android.util.Log.d("SettingsActivity", "setupListeners completed")
            updateLocalModelStatus()
            android.util.Log.d("SettingsActivity", "updateLocalModelStatus completed")

            android.util.Log.d("SettingsActivity", "Activity created successfully")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "初始化失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun bindViews() {
        android.util.Log.d("SettingsActivity", "bindViews started")
        try {
            vendorSpinner = findViewById(R.id.vendorSpinner)
            android.util.Log.d("SettingsActivity", "vendorSpinner bound")
            apiKeyInput = findViewById(R.id.apiKeyInput)
            android.util.Log.d("SettingsActivity", "apiKeyInput bound")
            modelSpinner = findViewById(R.id.modelSpinner)
            android.util.Log.d("SettingsActivity", "modelSpinner bound")
            modelSpinnerLayout = findViewById(R.id.modelSpinnerLayout)
            android.util.Log.d("SettingsActivity", "modelSpinnerLayout bound")
            testApiButton = findViewById(R.id.testApiButton)
            android.util.Log.d("SettingsActivity", "testApiButton bound")
            apiTestStatus = findViewById(R.id.apiTestStatus)
            android.util.Log.d("SettingsActivity", "apiTestStatus bound")
            saveApiKeyButton = findViewById(R.id.saveApiKeyButton)
            android.util.Log.d("SettingsActivity", "saveApiKeyButton bound")
            localModelStatus = findViewById(R.id.localModelStatus)
            android.util.Log.d("SettingsActivity", "localModelStatus bound")
            loadLocalModelButton = findViewById(R.id.loadLocalModelButton)
            android.util.Log.d("SettingsActivity", "loadLocalModelButton bound")
            voiceInputSwitch = findViewById(R.id.voiceInputSwitch)
            android.util.Log.d("SettingsActivity", "voiceInputSwitch bound")
            voiceOutputSwitch = findViewById(R.id.voiceOutputSwitch)
            android.util.Log.d("SettingsActivity", "voiceOutputSwitch bound")
            modelDownloadHint = findViewById(R.id.modelDownloadHint)
            android.util.Log.d("SettingsActivity", "modelDownloadHint bound")
            android.util.Log.d("SettingsActivity", "bindViews completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to bind views: ${e.message}", e)
            throw IllegalStateException("Layout resource not found or incomplete: ${e.message}", e)
        }
    }

    private fun setupVendorSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vendorList)
        vendorSpinner.setAdapter(adapter)
    }

    private fun loadSavedSettingsSync() {
        android.util.Log.d("SettingsActivity", "loadSavedSettingsSync started")
        try {
            // 加载保存的厂商和 API Key
            val savedVendor = appSettings.selectedVendorId
            android.util.Log.d("SettingsActivity", "savedVendor: $savedVendor")
            val vendorIndex = vendorList.indexOf(getVendorNameById(savedVendor))
            if (vendorIndex >= 0) {
                vendorSpinner.setText(vendorList[vendorIndex], false)
                android.util.Log.d("SettingsActivity", "vendorSpinner set to: ${vendorList[vendorIndex]}")
            }

            val apiKey = apiKeyManager.getApiKey(savedVendor)
            apiKeyInput.setText(apiKey)
            android.util.Log.d("SettingsActivity", "apiKey loaded")

            // 加载模型选择设置
            val agentTargetStr = appSettings.agentTarget
            android.util.Log.d("SettingsActivity", "agentTargetStr: $agentTargetStr")
            if (agentTargetStr.isNotEmpty()) {
                try {
                    val currentTarget = AgentTarget.valueOf(agentTargetStr)
                    when (currentTarget) {
                        AgentTarget.AUTO -> {
                            findViewById<MaterialRadioButton>(R.id.autoModelRadio).isChecked = true
                        }
                        AgentTarget.CLOUD -> {
                            findViewById<MaterialRadioButton>(R.id.cloudFirstRadio).isChecked = true
                        }
                        AgentTarget.LOCAL -> {
                            findViewById<MaterialRadioButton>(R.id.localOnlyRadio).isChecked = true
                        }
                    }
                    android.util.Log.d("SettingsActivity", "model selection set to: $currentTarget")
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("SettingsActivity", "Invalid agentTarget: $agentTargetStr")
                }
            }

            // 加载语音设置
            voiceInputSwitch.isChecked = appSettings.voiceInputEnabled
            voiceOutputSwitch.isChecked = appSettings.voiceOutputEnabled
            android.util.Log.d("SettingsActivity", "voice settings loaded")
            android.util.Log.d("SettingsActivity", "loadSavedSettingsSync completed")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error loading settings: ${e.message}", e)
        }
    }

    private fun loadSavedSettings() {
        lifecycleScope.launch {
            try {
                loadSavedSettingsSync()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error in loadSavedSettings: ${e.message}", e)
            }
        }
    }

    private fun getVendorNameById(vendorId: String): String {
        return when (vendorId) {
            "aliyun-bailian" -> "阿里云百炼"
            "deepseek" -> "DeepSeek"
            "kimi" -> "Kimi"
            "azure-openai" -> "Azure OpenAI"
            else -> "阿里云百炼"
        }
    }

    private fun getVendorIdByIndex(index: Int): String {
        return when (index) {
            0 -> "aliyun-bailian"
            1 -> "deepseek"
            2 -> "kimi"
            3 -> "azure-openai"
            else -> "aliyun-bailian"
        }
    }

    private fun setupListeners() {
        // 厂商选择变化时
        vendorSpinner.setOnDismissListener {
            val selectedVendorText = vendorSpinner.text.toString()
            val vendorIndex = vendorList.indexOf(selectedVendorText)
            if (vendorIndex >= 0) {
                currentVendorId = getVendorIdByIndex(vendorIndex)
                // 加载已保存的 API Key
                val savedApiKey = apiKeyManager.getApiKey(currentVendorId)
                if (!savedApiKey.isNullOrBlank()) {
                    apiKeyInput.setText(savedApiKey)
                } else {
                    apiKeyInput.text?.clear()
                }
                // 隐藏模型选择和测试状态
                modelSpinnerLayout.visibility = android.view.View.GONE
                apiTestStatus.visibility = android.view.View.GONE
            }
        }

        // 测试 API 连接
        testApiButton.setOnClickListener {
            testApiConnection()
        }

        // 模型选择变化时
        modelSpinner.setOnDismissListener {
            val selectedModel = modelSpinner.text.toString()
            android.util.Log.d("SettingsActivity", "Selected model: $selectedModel")
        }

        saveApiKeyButton.setOnClickListener {
            saveApiKey()
        }

        loadLocalModelButton.setOnClickListener {
            loadLocalModel()
        }

        findViewById<MaterialRadioButton>(R.id.autoModelRadio).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                appSettings.agentTarget = AgentTarget.AUTO.name
            }
        }

        findViewById<MaterialRadioButton>(R.id.cloudFirstRadio).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                appSettings.agentTarget = AgentTarget.CLOUD.name
            }
        }

        findViewById<MaterialRadioButton>(R.id.localOnlyRadio).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                appSettings.agentTarget = AgentTarget.LOCAL.name
            }
        }

        voiceInputSwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.voiceInputEnabled = isChecked
        }

        voiceOutputSwitch.setOnCheckedChangeListener { _, isChecked ->
            appSettings.voiceOutputEnabled = isChecked
        }
    }

    /**
     * 测试 API 连接
     */
    private fun testApiConnection() {
        val selectedVendorText = vendorSpinner.text.toString()
        val vendorIndex = vendorList.indexOf(selectedVendorText)
        if (vendorIndex < 0) {
            Toast.makeText(this, "请选择厂商", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = apiKeyInput.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val vendorId = getVendorIdByIndex(vendorIndex)
        val config = VendorConfig.getVendorConfigById(vendorId, apiKey)

        apiTestStatus.visibility = android.view.View.VISIBLE
        apiTestStatus.text = "正在测试连接..."
        testApiButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val bailianClient = BailianClient(config)
                val result = bailianClient.testConnection()

                apiTestStatus.text = if (result.success) {
                    "✓ API 连接成功！\n可用模型：${result.availableModels.joinToString(", ")}"
                } else {
                    "✗ ${result.message}"
                }
                apiTestStatus.setTextColor(
                    getColor(if (result.success) R.color.success else R.color.error)
                )

                // 显示模型选择器
                if (result.success && result.availableModels.isNotEmpty()) {
                    availableModels = result.availableModels
                    setupModelSpinner(availableModels)
                    modelSpinnerLayout.visibility = android.view.View.VISIBLE
                }
            } catch (e: Exception) {
                apiTestStatus.text = "✗ 测试失败：${e.message}"
                apiTestStatus.setTextColor(getColor(R.color.error))
            } finally {
                testApiButton.isEnabled = true
            }
        }
    }

    /**
     * 设置模型选择器
     */
    private fun setupModelSpinner(models: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, models)
        modelSpinner.setAdapter(adapter)

        // 如果有已保存的模型选择，设置为默认值
        val savedModel = appSettings.localModelId // 复用 localModelId 字段
        if (models.contains(savedModel)) {
            modelSpinner.setText(savedModel, false)
        } else if (models.isNotEmpty()) {
            modelSpinner.setText(models.first(), false)
        }
    }

    private fun saveApiKey() {
        val selectedVendorText = vendorSpinner.text.toString()
        android.util.Log.d("SettingsActivity", "saveApiKey: selectedVendorText=$selectedVendorText")

        val selectedVendorIndex = vendorList.indexOf(selectedVendorText)
        android.util.Log.d("SettingsActivity", "saveApiKey: selectedVendorIndex=$selectedVendorIndex")

        if (selectedVendorIndex < 0) {
            Toast.makeText(this, "请选择厂商", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = apiKeyInput.text.toString().trim()
        android.util.Log.d("SettingsActivity", "saveApiKey: apiKey length=${apiKey.length}")

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val vendorId = getVendorIdByIndex(selectedVendorIndex)
        android.util.Log.d("SettingsActivity", "saveApiKey: vendorId=$vendorId")

        lifecycleScope.launch {
            try {
                apiKeyManager.saveApiKey(vendorId, apiKey)
                android.util.Log.d("SettingsActivity", "saveApiKey: saved to ApiKeyManager")

                appSettings.selectedVendorId = vendorId
                android.util.Log.d("SettingsActivity", "saveApiKey: saved to AppSettings")

                // 验证是否保存成功
                val savedKey = apiKeyManager.getApiKey(vendorId)
                android.util.Log.d("SettingsActivity", "saveApiKey: verification - saved key exists: ${savedKey != null}")

                Toast.makeText(this@SettingsActivity, "API Key 已保存 (${vendorId})", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "saveApiKey: exception - ${e.message}", e)
                Toast.makeText(this@SettingsActivity, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadLocalModel() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("SettingsActivity", "loadLocalModel started")
                localModelStatus.text = "本地模型：加载中..."

                val totalRam = deviceCapabilityDetector.getTotalRam()
                val availableStorage = deviceCapabilityDetector.getAvailableStorage()

                android.util.Log.d("SettingsActivity", "Device RAM: ${totalRam / 1024 / 1024 / 1024}GB, Storage: ${availableStorage / 1024 / 1024 / 1024}GB")

                val selectedModel = modelManager.selectModelForDevice(totalRam, availableStorage)

                if (selectedModel == null) {
                    localModelStatus.text = "本地模型：设备配置不足，无法加载任何模型"
                    android.util.Log.w("SettingsActivity", "No suitable model for device")
                    Toast.makeText(
                        this@SettingsActivity,
                        "设备配置不足以运行任何本地模型",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                android.util.Log.d("SettingsActivity", "Selected model: ${selectedModel.name} (${selectedModel.id})")

                val result = modelManager.loadModel(selectedModel.id)

                if (result.isSuccess) {
                    localModelStatus.text = "本地模型：${selectedModel.name} 已加载"
                    android.util.Log.d("SettingsActivity", "Model loaded successfully")
                    Toast.makeText(this@SettingsActivity, "${selectedModel.name} 加载成功", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                    android.util.Log.e("SettingsActivity", "Model load failed: $errorMsg")

                    // 检查是否是模型文件缺失
                    if (errorMsg.contains("模型文件不存在") || errorMsg.contains("file not found")) {
                        localModelStatus.text = "本地模型：模型文件未安装"
                        Toast.makeText(
                            this@SettingsActivity,
                            "模型文件未安装\n\n说明：\n本地模型文件需要单独下载\n建议使用云端模型（阿里云百炼、DeepSeek、Kimi）",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        localModelStatus.text = "本地模型：加载失败"
                        Toast.makeText(
                            this@SettingsActivity,
                            "模型加载失败：${errorMsg}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "loadLocalModel exception: ${e.message}", e)
                localModelStatus.text = "本地模型：发生错误"
                Toast.makeText(
                    this@SettingsActivity,
                    "模型加载异常：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateLocalModelStatus() {
        if (modelManager.isModelLoaded()) {
            val model = modelManager.getCurrentModel()
            localModelStatus.text = "本地模型：${model?.name ?: "未知"} 已加载"
        } else {
            localModelStatus.text = "本地模型：未加载"
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocalModelStatus()
    }
}

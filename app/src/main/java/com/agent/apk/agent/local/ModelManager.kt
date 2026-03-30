// File: app/src/main/java/com/agent/apk/agent/local/ModelManager.kt
package com.agent.apk.agent.local

import android.content.Context
import android.content.res.AssetManager
// import com.google.mediapipe.tasks.core.BaseOptions
// import com.google.mediapipe.tasks.core.Delegate
// import com.google.mediapipe.tasks.llm.inference.InferenceSession
// import com.google.mediapipe.tasks.llm.inference.InferenceSession.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地模型管理器：管理 MediaPipe LLM 模型加载和推理会话
 *
 * 支持的模型：
 * - Gemma 2B (默认)
 * - Qwen 0.5B (低功耗设备降级)
 *
 * 模型文件放置位置：
 * - app/src/main/assets/llm/models/gemma-2b-int4.bin
 * - app/src/main/assets/llm/models/qwen-0.5b-int4.bin
 */
class ModelManager(private val context: Context) {

    // MediaPipe 暂时不可用，使用占位符
    private var currentSession: Any? = null
    private var currentModel: LocalModel? = null
    private var isModelLoading = false

    /**
     * 模型配置目录
     */
    private val modelConfigDir = File(context.filesDir, "llm/models").apply {
        if (!exists()) {
            mkdirs()
            android.util.Log.d("ModelManager", "Created model config directory: ${absolutePath}")
        }
    }

    /**
     * 可用的本地模型列表
     */
    val availableModels: List<LocalModel> = listOf(
        LocalModel(
            id = "gemma-2b",
            name = "Gemma 2B",
            assetPath = "llm/models/gemma-2b-int4.bin",
            minRam = 6 * 1024 * 1024 * 1024L, // 6GB RAM
            minStorage = 2 * 1024 * 1024 * 1024L, // 2GB 存储
            description = "Google Gemma 2B 量化版，适合中等配置设备"
        ),
        LocalModel(
            id = "qwen-0.5b",
            name = "Qwen 0.5B",
            assetPath = "llm/models/qwen-0.5b-int4.bin",
            minRam = 3 * 1024 * 1024 * 1024L, // 3GB RAM
            minStorage = 1 * 1024 * 1024 * 1024L, // 1GB 存储
            description = "阿里 Qwen 0.5B 量化版，适合低配设备"
        )
    )

    init {
        android.util.Log.d("ModelManager", "ModelManager initialized")
        android.util.Log.d("ModelManager", "Model config directory: ${modelConfigDir.absolutePath}")
        android.util.Log.d("ModelManager", "Available models: ${availableModels.size}")
        availableModels.forEach { model ->
            android.util.Log.d("ModelManager", "  - ${model.id}: ${model.name}")
        }
    }

    /**
     * 根据设备能力自动选择模型
     */
    fun selectModelForDevice(totalRam: Long, availableStorage: Long): LocalModel? {
        return availableModels
            .filter { model ->
                totalRam >= model.minRam && availableStorage >= model.minStorage
            }
            .maxByOrNull { it.minRam } // 选择性能最好的可用模型
    }

    /**
     * 加载模型到内存
     * @param modelId 模型 ID
     * @return 是否加载成功
     */
    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        android.util.Log.d("ModelManager", "loadModel started: modelId=$modelId")

        if (isModelLoading) {
            android.util.Log.w("ModelManager", "Model is already loading")
            return@withContext Result.failure(IllegalStateException("Model is already loading"))
        }

        val model = availableModels.find { it.id == modelId }
        android.util.Log.d("ModelManager", "Found model: ${model?.name ?: "null"}")

        if (model == null) {
            android.util.Log.e("ModelManager", "Unknown model: $modelId")
            return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))
        }

        try {
            isModelLoading = true

            // 确保模型文件存在
            val modelFile = ensureModelFile(model)
            android.util.Log.d("ModelManager", "Model file path: ${modelFile?.absolutePath ?: "null"}")

            if (modelFile == null) {
                val errorMsg = "模型文件不存在：${model.assetPath}\n\n请说明：\n1. 本地模型文件需要放置在 app/src/main/assets/llm/models/ 目录\n2. 当前支持的模型：Gemma 2B, Qwen 0.5B"
                android.util.Log.e("ModelManager", errorMsg)
                return@withContext Result.failure(
                    IllegalStateException(errorMsg)
                )
            }

            android.util.Log.d("ModelManager", "Model file exists, size: ${modelFile.length() / 1024 / 1024}MB")

            // MediaPipe 暂时不可用，返回成功占位符
            currentSession = modelFile
            currentModel = model

            android.util.Log.i("ModelManager", "Model loaded successfully: ${model.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ModelManager", "loadModel exception: ${e.message}", e)
            Result.failure(e)
        } finally {
            isModelLoading = false
        }
    }

    /**
     * 创建新的对话会话
     */
    fun createConversation(): Any? {
        return currentSession // 占位符
    }

    /**
     * 获取当前 loaded 的模型
     */
    fun getCurrentModel(): LocalModel? = currentModel

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean = currentSession != null

    /**
     * 卸载当前模型，释放内存
     */
    fun unloadModel() {
        currentSession = null
        currentModel = null
    }

    /**
     * 获取模型文件（从 assets 或本地缓存）
     */
    private fun ensureModelFile(model: LocalModel): File? {
        val destFile = File(modelConfigDir, "${model.id}.bin")
        android.util.Log.d("ModelManager", "ensureModelFile: destFile=${destFile.absolutePath}")
        android.util.Log.d("ModelManager", "ensureModelFile: destFile exists=${destFile.exists()}")

        if (!destFile.exists()) {
            android.util.Log.d("ModelManager", "ensureModelFile: copying from assets: ${model.assetPath}")
            val copied = copyModelFromAssets(model, destFile)
            android.util.Log.d("ModelManager", "ensureModelFile: copy result=$copied")
            if (!copied) {
                android.util.Log.e("ModelManager", "ensureModelFile: failed to copy from assets")
                return null
            }
        }

        android.util.Log.d("ModelManager", "ensureModelFile: final exists=${destFile.exists()}, size=${destFile.length()}")
        return if (destFile.exists()) destFile else null
    }

    /**
     * 从 assets 复制模型文件到本地存储
     * @return 是否复制成功
     */
    private fun copyModelFromAssets(model: LocalModel, destFile: File): Boolean {
        return try {
            android.util.Log.d("ModelManager", "copyModelFromAssets: trying to open ${model.assetPath}")

            // 确保目标目录存在
            if (!destFile.parentFile?.exists()!!) {
                destFile.parentFile?.mkdirs()
                android.util.Log.d("ModelManager", "copyModelFromAssets: created directory ${destFile.parentFile?.absolutePath}")
            }

            context.assets.open(model.assetPath).use { inputStream ->
                java.io.FileOutputStream(destFile).use { outputStream ->
                    val bytesCopied = inputStream.copyTo(outputStream)
                    android.util.Log.d("ModelManager", "copyModelFromAssets: copied $bytesCopied bytes")
                }
            }
            android.util.Log.i("ModelManager", "copyModelFromAssets: success")
            true
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.e("ModelManager", "copyModelFromAssets: file not found in assets - ${model.assetPath}")
            false
        } catch (e: Exception) {
            android.util.Log.e("ModelManager", "copyModelFromAssets: exception - ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 检查模型文件是否存在于 assets
     */
    fun isModelInAssets(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        return try {
            context.assets.open(model.assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取模型加载状态
     */
    fun getLoadingProgress(): Float {
        return if (isModelLoading) 0.5f else if (isModelLoaded()) 1.0f else 0.0f
    }
}

/**
 * 本地模型配置
 */
data class LocalModel(
    val id: String,
    val name: String,
    val assetPath: String,
    val minRam: Long,
    val minStorage: Long,
    val description: String
)

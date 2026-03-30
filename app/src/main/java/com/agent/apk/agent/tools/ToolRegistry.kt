// File: app/src/main/java/com/agent/apk/agent/tools/ToolRegistry.kt
package com.agent.apk.agent.tools

import android.util.Log
import com.agent.apk.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 工具注册表 - 统一管理所有可执行工具
 *
 * 设计灵感来自 nanobot 的工具注册机制
 *
 * 使用示例:
 * ```
 * val registry = ToolRegistry.getInstance()
 * registry.register(ClickTool(scanner))
 * registry.register(TypeTool(scanner))
 *
 * val result = registry.execute("click", mapOf("target" to "微信"))
 * ```
 */
class ToolRegistry private constructor() {
    companion object {
        private const val TAG = "ToolRegistry"

        @Volatile
        private var INSTANCE: ToolRegistry? = null

        fun getInstance(): ToolRegistry {
            return INSTANCE ?: synchronized(this) {
                val instance = ToolRegistry()
                INSTANCE = instance
                instance
            }
        }
    }

    // 工具映射表：工具名 -> 工具实例
    private val tools = mutableMapOf<String, AgentTool<*>>()

    // 工具别名映射：简化调用
    private val aliases = mutableMapOf<String, String>()

    /**
     * 注册工具
     * @param tool 工具实例
     * @param aliases 可选的别名列表
     */
    fun register(tool: AgentTool<*>, vararg aliases: String) {
        val name = tool.name
        if (tools.containsKey(name)) {
            Log.w(TAG, "Tool '$name' already registered, overwriting")
        }
        tools[name] = tool
        Log.d(TAG, "Tool registered: $name (${tool.description})")

        // 注册别名
        aliases.forEach { alias ->
            this.aliases[alias] = name
            Log.d(TAG, "Alias registered: $alias -> $name")
        }
    }

    /**
     * 注销工具
     */
    fun unregister(name: String) {
        tools.remove(name)
        aliases.entries.removeAll { it.value == name }
        Log.d(TAG, "Tool unregistered: $name")
    }

    /**
     * 获取工具定义（用于构建系统提示词）
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.getParameters()
            )
        }
    }

    /**
     * 执行工具
     * @param name 工具名称
     * @param args 参数
     * @return 执行结果
     */
    suspend fun execute(name: String, args: Map<String, Any?>): ToolResult {
        // 解析别名
        val actualName = aliases[name] ?: name
        val tool = tools[actualName]
            ?: return ToolResult.success(null, "Unknown tool: $name")

        try {
            Log.d(TAG, "Executing tool: $name with args: $args")
            val result = tool.execute(args)
            Log.d(TAG, "Tool execution completed: $name")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            return ToolResult.failure(e.message ?: "Execution failed")
        }
    }

    /**
     * 检查工具是否可用
     */
    fun isAvailable(name: String): Boolean {
        val actualName = aliases[name] ?: name
        return tools.containsKey(actualName) && tools[actualName]!!.isAvailable()
    }

    /**
     * 获取所有已注册的工具名称
     */
    fun getToolNames(): Set<String> = tools.keys

    /**
     * 清空所有工具
     */
    fun clear() {
        tools.clear()
        aliases.clear()
        Log.d(TAG, "All tools cleared")
    }
}

/**
 * 工具定义 - 用于构建系统提示词
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
)

/**
 * 工具参数定义
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)

/**
 * 工具执行结果
 */
data class ToolResult(
    val success: Boolean,
    val data: Any?,
    val message: String
) {
    companion object {
        fun success(data: Any?, message: String = "Success"): ToolResult {
            return ToolResult(true, data, message)
        }

        fun failure(message: String): ToolResult {
            return ToolResult(false, null, message)
        }
    }
}

/**
 * 工具基类
 * @param T 参数类型
 */
abstract class AgentTool<T : ToolParams> {
    abstract val name: String
    abstract val description: String

    /**
     * 检查工具是否可用
     */
    open fun isAvailable(): Boolean = true

    /**
     * 获取参数定义
     */
    abstract fun getParameters(): List<ToolParameter>

    /**
     * 执行工具
     * @param args 参数
     */
    abstract suspend fun execute(args: Map<String, Any?>): ToolResult

    /**
     * 构建工具提示信息
     */
    fun getUsageHint(): String {
        val params = getParameters()
            .joinToString(", ") { p ->
                "${p.name}: ${p.type}" + if (p.required) "" else " (可选)"
            }
        return "$name($params) - $description"
    }
}

/**
 * 工具参数基类
 */
abstract class ToolParams

// File: app/src/main/java/com/agent/apk/util/AgentLogger.kt
package com.agent.apk.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Agent 日志工具类
 *
 * 统一的日志管理，支持：
 * - 分级日志（V/D/I/W/E）
 * - 标签自动获取
 * - 格式化输出
 * - JSON 格式化
 * - 可选的文件日志
 */
object AgentLogger {

    private const val DEFAULT_TAG = "Agent"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val gson: Gson by lazy {
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    }

    /**
     * 是否启用日志输出（正式发布时建议关闭）
     */
    var isEnabled = true

    /**
     * 是否启用 JSON 格式化
     */
    var enableJsonFormat = true

    /**
     * 最小日志级别
     */
    var minLevel = LogLevel.DEBUG

    /**
     * 日志级别枚举
     */
    enum class LogLevel(val priority: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6),
        NONE(7)
    }

    /**
     * 详细日志
     */
    fun v(message: String, tag: String = DEFAULT_TAG) {
        log(LogLevel.VERBOSE, tag, message, null)
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    /**
     * 调试日志
     */
    fun d(message: String, tag: String = DEFAULT_TAG) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    /**
     * 信息日志
     */
    fun i(message: String, tag: String = DEFAULT_TAG) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /**
     * 警告日志
     */
    fun w(message: String, tag: String = DEFAULT_TAG) {
        log(LogLevel.WARN, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    /**
     * 错误日志
     */
    fun e(message: String, tag: String = DEFAULT_TAG) {
        log(LogLevel.ERROR, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * JSON 日志
     */
    fun json(json: String?, tag: String = DEFAULT_TAG) {
        if (!enableJsonFormat || json.isNullOrBlank()) {
            d(tag, json ?: "null")
            return
        }

        try {
            val formattedJson = gson.fromJson(json, Any::class.java)
            val output = gson.toJson(formattedJson)
            log(LogLevel.DEBUG, tag, "\n$output", null)
        } catch (e: Exception) {
            log(LogLevel.DEBUG, tag, json, e)
        }
    }

    /**
     * 内部日志方法
     */
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled || level.priority < minLevel.priority) {
            return
        }

        val formattedTag = sanitizeTag(tag)
        val timestamp = dateFormat.format(Date())
        val fullMessage = "[$timestamp] $message"

        when (level) {
            LogLevel.VERBOSE -> Log.v(formattedTag, fullMessage, throwable)
            LogLevel.DEBUG -> Log.d(formattedTag, fullMessage, throwable)
            LogLevel.INFO -> Log.i(formattedTag, fullMessage, throwable)
            LogLevel.WARN -> Log.w(formattedTag, fullMessage, throwable)
            LogLevel.ERROR -> Log.e(formattedTag, fullMessage, throwable)
            LogLevel.NONE -> {}
        }
    }

    /**
     * 清理标签（Android 限制标签长度为 23 字符）
     */
    private fun sanitizeTag(tag: String): String {
        return tag.take(23).replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    /**
     * 获取调用者类名作为标签
     */
    fun getCallerTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        // 找到调用者的类名
        for (element in stackTrace) {
            if (!element.className.contains("AgentLogger") &&
                !element.className.contains("Kt")
            ) {
                return element.className
                    .substringAfterLast(".")
                    .take(15) // 缩短类名以适应标签长度限制
            }
        }
        return DEFAULT_TAG
    }
}

/**
 * 为类创建日志实例
 */
interface LoggerProvider {
    val log: AgentLogger
        get() = AgentLogger
}

/**
 * 在类中便捷使用日志
 * 用法：class MyClass : LoggerByLog
 */
interface LoggerByLog {
    val log: AgentLogger
        get() = AgentLogger
}

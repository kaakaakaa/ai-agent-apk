// File: app/src/main/java/com/agent/apk/AgentApplication.kt
package com.agent.apk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.agent.apk.infra.ApiKeyManager
import com.agent.apk.infra.DeviceCapabilityDetector
import com.agent.apk.model.AppSettings
import com.agent.apk.voice.SpeechToTextService
import com.agent.apk.voice.TextToSpeechService
import android.util.Log

/**
 * 应用入口类
 */
class AgentApplication : Application() {

    // 核心服务（懒加载）
    val apiKeyManager by lazy { ApiKeyManager(this) }
    val deviceCapabilityDetector by lazy { DeviceCapabilityDetector(this) }
    val appSettings by lazy { AppSettings(this) }

    // 语音服务（懒加载，不自动初始化）
    val speechToTextService by lazy { SpeechToTextService(this) }
    val textToSpeechService by lazy { TextToSpeechService(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 添加崩溃日志
        Log.d("AgentApplication", "Application onCreate called")
        Log.d("AgentApplication", "Package name: $packageName")
        try {
            createNotificationChannel()
            Log.d("AgentApplication", "Notification channel created")
            // 不再自动初始化语音服务，改为按需初始化
            Log.d("AgentApplication", "Application initialized successfully")
        } catch (e: Exception) {
            Log.e("AgentApplication", "Error during initialization: ${e.message}", e)
            // 不要抛出异常，让应用继续运行
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        speechToTextService.destroy()
        textToSpeechService.destroy()
    }

    /**
     * 创建通知渠道（Android 8+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "agent_service",
                "AI Agent 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI Agent 后台服务通知"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: AgentApplication
            private set
    }
}

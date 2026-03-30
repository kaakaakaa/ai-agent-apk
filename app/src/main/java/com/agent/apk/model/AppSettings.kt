// File: app/src/main/java/com/agent/apk/model/AppSettings.kt
package com.agent.apk.model

import android.content.Context
import android.content.SharedPreferences
import com.agent.apk.agent.AgentTarget

/**
 * 应用设置管理器
 */
class AppSettings(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    /**
     * 选中的厂商标 ID
     */
    var selectedVendorId: String
        get() = prefs.getString("selected_vendor_id", "aliyun-bailian") ?: "aliyun-bailian"
        set(value) = prefs.edit().putString("selected_vendor_id", value).apply()

    /**
     * Agent 目标选择（LOCAL, CLOUD, AUTO）
     */
    var agentTarget: String
        get() = prefs.getString("agent_target", AgentTarget.AUTO.name) ?: AgentTarget.AUTO.name
        set(value) = prefs.edit().putString("agent_target", value).apply()

    /**
     * 是否启用语音输入
     */
    var voiceInputEnabled: Boolean
        get() = prefs.getBoolean("voice_input_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_input_enabled", value).apply()

    /**
     * 是否启用语音输出（TTS）
     */
    var voiceOutputEnabled: Boolean
        get() = prefs.getBoolean("voice_output_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_output_enabled", value).apply()

    /**
     * 是否启用悬浮球
     */
    var floatingBallEnabled: Boolean
        get() = prefs.getBoolean("floating_ball_enabled", true)
        set(value) = prefs.edit().putBoolean("floating_ball_enabled", value).apply()

    /**
     * 本地模型 ID
     */
    var localModelId: String
        get() = prefs.getString("local_model_id", "gemma-2b") ?: "gemma-2b"
        set(value) = prefs.edit().putString("local_model_id", value).apply()

    /**
     * 云优先模式
     */
    var cloudFirst: Boolean
        get() = prefs.getBoolean("cloud_first", true)
        set(value) = prefs.edit().putBoolean("cloud_first", value).apply()
}

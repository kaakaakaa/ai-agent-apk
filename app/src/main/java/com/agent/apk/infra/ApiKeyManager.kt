// File: app/src/main/java/com/agent/apk/infra/ApiKeyManager.kt
package com.agent.apk.infra

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 管理器：安全存储和读取 API Key
 *
 * 优先使用 EncryptedSharedPreferences 加密存储
 * 如果设备不支持，降级到普通 SharedPreferences
 */
class ApiKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "ApiKeyManager"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "encrypted_api_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences not available, falling back to normal SharedPreferences: ${e.message}")
            // 降级到普通 SharedPreferences
            context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
        }
    }

    /**
     * 保存 API Key
     */
    suspend fun saveApiKey(vendorId: String, apiKey: String) {
        sharedPreferences.edit().putString("api_key_$vendorId", apiKey).apply()
    }

    /**
     * 获取 API Key
     */
    fun getApiKey(vendorId: String): String? {
        return sharedPreferences.getString("api_key_$vendorId", null)
    }

    /**
     * 删除 API Key
     */
    suspend fun deleteApiKey(vendorId: String) {
        sharedPreferences.edit().remove("api_key_$vendorId").apply()
    }

    /**
     * 检查是否已配置 API Key
     */
    fun hasApiKey(vendorId: String): Boolean {
        return getApiKey(vendorId) != null
    }

    /**
     * 清除所有 API Keys
     */
    suspend fun clearAllApiKeys() {
        sharedPreferences.edit().clear().apply()
    }
}

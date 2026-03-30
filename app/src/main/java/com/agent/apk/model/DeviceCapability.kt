// File: app/src/main/java/com/agent/apk/model/DeviceCapability.kt
package com.agent.apk.model

/**
 * 设备能力数据类
 */
data class DeviceCapability(
    val ramGb: Float,
    val socModel: String,
    val availableStorageGb: Long,
    val androidVersion: Int
)

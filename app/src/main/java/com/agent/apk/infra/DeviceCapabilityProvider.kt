// File: app/src/main/java/com/agent/apk/infra/DeviceCapabilityProvider.kt
package com.agent.apk.infra

/**
 * 设备能力提供者接口（用于测试 mock）
 */
interface DeviceCapabilityProvider {
    fun getDeviceTier(): DeviceTier
}

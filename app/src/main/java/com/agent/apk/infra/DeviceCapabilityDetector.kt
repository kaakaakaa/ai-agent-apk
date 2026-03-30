// File: app/src/main/java/com/agent/apk/infra/DeviceCapabilityDetector.kt
package com.agent.apk.infra

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * 设备能力检测器：获取设备硬件配置信息
 *
 * 用于判断设备是否适合运行本地模型
 */
class DeviceCapabilityDetector(private val context: Context) {

    /**
     * 获取总 RAM（字节）
     */
    fun getTotalRam(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        // 使用总内存而非 availMem
        return memInfo.totalMem
    }

    /**
     * 获取可用 RAM（字节）
     */
    fun getAvailableRam(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    /**
     * 获取可用存储空间（字节）
     */
    fun getAvailableStorage(): Long {
        val file = context.filesDir
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            file.usableSpace
        } else {
            @Suppress("DEPRECATION")
            file.usableSpace
        }
    }

    /**
     * 获取总存储空间（字节）
     */
    fun getTotalStorage(): Long {
        val file = context.filesDir
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            file.totalSpace
        } else {
            @Suppress("DEPRECATION")
            file.totalSpace
        }
    }

    /**
     * 获取 CPU 核心数
     */
    fun getCpuCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    /**
     * 获取 Android API 版本
     */
    fun getApiLevel(): Int {
        return Build.VERSION.SDK_INT
    }

    /**
     * 判断设备是否为低端设备
     */
    fun isLowEndDevice(): Boolean {
        val totalRam = getTotalRam()
        val availableStorage = getAvailableStorage()

        // 低端设备定义：RAM < 4GB 或 存储 < 16GB
        return totalRam < 4L * 1024 * 1024 * 1024 ||
                availableStorage < 16L * 1024 * 1024 * 1024
    }

    /**
     * 判断设备是否为高端设备
     */
    fun isHighEndDevice(): Boolean {
        val totalRam = getTotalRam()
        val availableStorage = getAvailableStorage()

        // 高端设备定义：RAM >= 8GB 且 存储 >= 64GB
        return totalRam >= 8L * 1024 * 1024 * 1024 &&
                availableStorage >= 64L * 1024 * 1024 * 1024
    }

    /**
     * 获取设备能力等级
     */
    fun getDeviceTier(): DeviceTier {
        return when {
            isHighEndDevice() -> DeviceTier.HIGH
            isLowEndDevice() -> DeviceTier.LOW
            else -> DeviceTier.MEDIUM
        }
    }

    /**
     * 从 /proc/meminfo 读取 RAM 信息（兼容旧版本）
     */
    private fun getRamFromProc(): Long {
        return try {
            File("/proc/meminfo").useLines { lines ->
                val memInfoLine = lines.first { it.startsWith("MemTotal:") }
                val parts = memInfoLine.split("\\s+".toRegex())
                val memoryKB = parts.firstOrNull { it.isNotEmpty() && it.all { c -> c.isDigit() } }?.toLongOrNull() ?: return 4L * 1024 * 1024 * 1024
                memoryKB * 1024
            }
        } catch (e: Exception) {
            // 默认返回 4GB
            4L * 1024 * 1024 * 1024
        }
    }
}

/**
 * 设备能力等级
 */
enum class DeviceTier {
    LOW,      // 低端设备（RAM < 4GB）
    MEDIUM,   // 中端设备（4GB <= RAM < 8GB）
    HIGH      // 高端设备（RAM >= 8GB）
}

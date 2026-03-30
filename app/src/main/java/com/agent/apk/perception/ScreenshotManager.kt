// File: app/src/main/java/com/agent/apk/perception/ScreenshotManager.kt
package com.agent.apk.perception

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 截图管理器：获取屏幕截图
 *
 * Android 13+ 可以使用 AccessibilityService 截图
 * 旧版本需要 MediaProjection
 */
class ScreenshotManager(private val service: AccessibilityService) {

    companion object {
        @Volatile
        private var instance: ScreenshotManager? = null

        /**
         * 获取单例实例
         */
        fun getInstance(): ScreenshotManager? {
            return instance
        }

        /**
         * 初始化单例
         */
        fun initialize(service: AccessibilityService) {
            instance = ScreenshotManager(service)
        }

        /**
         * 捕获截图（便捷方法）
         */
        suspend fun captureScreenshot(): ByteArray? {
            return instance?.takeScreenshot()?.let { bitmap ->
                java.io.ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
                    stream.toByteArray()
                }
            }
        }
    }

    /**
     * 获取屏幕截图（Android 13+）
     */
    suspend fun takeScreenshot(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            takeScreenshotTiramisu()
        } else {
            // 旧版本返回 null，需要使用 MediaProjection
            null
        }
    }

    /**
     * Android 13+ 截图实现
     */
    private suspend fun takeScreenshotTiramisu(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)

                        screenshot.hardwareBuffer.close()
                        continuation.resume(bitmap, null)
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null, null)
                    }
                }
            )
        } else {
            continuation.resume(null, null)
        }
    }

    /**
     * 将 Bitmap 转换为字节数组
     */
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray? {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}

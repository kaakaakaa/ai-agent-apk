// File: app/src/main/java/com/agent/apk/action/GesturePerformer.kt
package com.agent.apk.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.ViewConfiguration
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 手势执行器：封装复杂手势操作
 */
class GesturePerformer(private val service: AccessibilityService) {

    /**
     * 双击
     */
    suspend fun doubleTap(x: Int, y: Int): Boolean {
        val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        return performGestures(
            listOf(
                createTapGesture(x, y, tapTimeout) to 0L,
                createTapGesture(x, y, tapTimeout) to doubleTapTimeout
            )
        )
    }

    /**
     * 双指滑动（用于缩放）
     */
    suspend fun pinchScale(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        durationMs: Long
    ): Boolean {
        val path1 = Path().apply {
            moveTo((centerX - startDistance / 2).toFloat(), centerY.toFloat())
            lineTo((centerX - endDistance / 2).toFloat(), centerY.toFloat())
        }
        val path2 = Path().apply {
            moveTo((centerX + startDistance / 2).toFloat(), centerY.toFloat())
            lineTo((centerX + endDistance / 2).toFloat(), centerY.toFloat())
        }

        return performMultiStrokeGesture(
            listOf(
                GestureDescription.StrokeDescription(path1, 0, durationMs),
                GestureDescription.StrokeDescription(path2, 0, durationMs)
            )
        )
    }

    /**
     * 画 L 形手势（返回）
     * 从屏幕左侧中间位置向下滑动，然后向右滑动
     */
    suspend fun drawLBack(): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 动态计算 L 形手势坐标
        val startX = (screenWidth * 0.08).toInt()  // 屏幕左侧 8% 位置
        val startY = (screenHeight * 0.3).toInt()   // 屏幕 30% 高度开始
        val endY = (screenHeight * 0.7).toInt()     // 滑到 70% 高度
        val endX = (screenWidth * 0.5).toInt()      // 向右滑到屏幕中间

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), endY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return performSingleStrokeGesture(path, 500L)
    }

    /**
     * 从底部上滑（回到主页）
     */
    suspend fun swipeUpFromBottom(): Boolean {
        // 获取屏幕尺寸
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = screenWidth / 2
        val startY = (screenHeight * 0.9).toInt()
        val endY = (screenHeight * 0.3).toInt()

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), endY.toFloat())
        }

        return performSingleStrokeGesture(path, 300L)
    }

    /**
     * 从屏幕左侧右滑（返回）
     */
    suspend fun swipeRightFromEdge(): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val startX = 0
        val startY = screenHeight / 2
        val endX = (displayMetrics.widthPixels * 0.3).toInt()

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), startY.toFloat())
        }

        return performSingleStrokeGesture(path, 300L)
    }

    /**
     * 执行单个手势
     */
    private suspend fun performSingleStrokeGesture(
        path: Path,
        durationMs: Long
    ): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return suspendCancellableCoroutine { continuation ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(true, null)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(false, null)
                    }
                }
            }, null)
        }
    }

    /**
     * 执行多笔画手势
     */
    private suspend fun performMultiStrokeGesture(
        strokes: List<GestureDescription.StrokeDescription>
    ): Boolean {
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it) }

        return suspendCancellableCoroutine { continuation ->
            service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(true, null)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (!continuation.isCompleted) {
                        continuation.resume(false, null)
                    }
                }
            }, null)
        }
    }

    /**
     * 执行多个手势序列
     */
    private suspend fun performGestures(
        gesturesWithDelays: List<Pair<GestureDescription, Long>>
    ): Boolean {
        for ((gesture, delay) in gesturesWithDelays) {
            android.os.SystemClock.sleep(delay)
            val result = suspendCancellableCoroutine<Boolean> { continuation ->
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (!continuation.isCompleted) {
                            continuation.resume(true, null)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (!continuation.isCompleted) {
                            continuation.resume(false, null)
                        }
                    }
                }, null)
            }
            if (!result) return false
        }
        return true
    }

    private fun createTapGesture(x: Int, y: Int, duration: Long): GestureDescription {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }
}

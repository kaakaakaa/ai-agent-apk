// File: app/src/main/java/com/agent/apk/ui/FloatingBallService.kt
package com.agent.apk.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.app.Service
import com.agent.apk.R
import com.agent.apk.agent.local.ModelManager
import com.agent.apk.infra.DeviceCapabilityDetector
import com.agent.apk.infra.NetworkMonitor
import com.agent.apk.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮球服务：常驻悬浮入口
 *
 * 功能：
 * - 屏幕悬浮拖拽
 * - 点击弹出菜单（语音输入、任务列表、设置）
 * - 快速启动 Agent 任务
 */
class FloatingBallService : Service() {

    companion object {
        private const val TAG = "FloatingBallService"

        /**
         * 检查悬浮窗权限
         */
        fun canDrawOverlays(context: android.content.Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        /**
         * 请求悬浮窗权限
         */
        fun requestOverlayPermission(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 悬浮球相关
    private var floatingBallView: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var currentX = 0
    private var currentY = 0

    // Agent 相关
    private lateinit var modelManager: ModelManager
    private lateinit var deviceCapabilityDetector: DeviceCapabilityDetector
    private lateinit var appSettings: AppSettings
    private lateinit var networkMonitor: NetworkMonitor

    // 点击判定阈值
    private var touchSlop: Int = 0
    private var tapTimeout: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")

        try {
            // 初始化点击判定阈值
            val viewConfiguration = ViewConfiguration.get(this)
            touchSlop = viewConfiguration.scaledTouchSlop
            tapTimeout = ViewConfiguration.getTapTimeout().toLong()

            modelManager = ModelManager(this)
            Log.d(TAG, "ModelManager initialized")
            deviceCapabilityDetector = DeviceCapabilityDetector(this)
            Log.d(TAG, "DeviceCapabilityDetector initialized")
            appSettings = AppSettings(this)
            Log.d(TAG, "AppSettings initialized")
            networkMonitor = NetworkMonitor(this)
            Log.d(TAG, "NetworkMonitor initialized")

            createFloatingBall()
            Log.d(TAG, "Floating ball created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            // 如果创建悬浮球失败，停止服务
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingBall()
        serviceScope.cancel()
    }

    /**
     * 创建悬浮球
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        Log.d(TAG, "createFloatingBall called")
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "WindowManager obtained")

            val inflater = LayoutInflater.from(this)
            floatingBallView = inflater.inflate(R.layout.floating_ball, null) as FrameLayout
            Log.d(TAG, "Floating ball view inflated")

            // 计算悬浮球初始位置（屏幕右上角区域）
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // 设置布局参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenWidth * 0.85).toInt()  // 屏幕右侧 85% 位置
                y = (screenHeight * 0.1).toInt()   // 屏幕顶部 10% 位置
                width = 180
                height = 180
            }

            try {
                windowManager?.addView(floatingBallView, params)
            } catch (e: Exception) {
                // 悬浮窗权限不足
                Log.e(TAG, "Failed to add view: ${e.message}")
                e.printStackTrace()
                stopSelf()
                return
            }

            // 设置拖拽监听
            floatingBallView?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentX = event.rawX.toInt()
                        currentY = event.rawY.toInt()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - currentX
                        val dy = event.rawY.toInt() - currentY

                        params.x += dx
                        params.y += dy

                        currentX = event.rawX.toInt()
                        currentY = event.rawY.toInt()

                        try {
                            windowManager?.updateViewLayout(floatingBallView, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update view: ${e.message}")
                            e.printStackTrace()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 短按视为点击
                        val deltaTime = event.eventTime - event.downTime
                        val deltaDistance = kotlin.math.sqrt(
                            Math.pow((event.rawX - event.x).toDouble(), 2.0) +
                                    Math.pow((event.rawY - event.y).toDouble(), 2.0)
                        )

                        if (deltaTime < tapTimeout && deltaDistance < touchSlop) {
                            onFloatingBallClick()
                        }
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in createFloatingBall: ${e.message}", e)
            stopSelf()
        }
    }

    /**
     * 移除悬浮球
     */
    private fun removeFloatingBall() {
        try {
            windowManager?.removeView(floatingBallView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        floatingBallView = null
        windowManager = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 悬浮球点击事件
     */
    private fun onFloatingBallClick() {
        // 启动主界面（聊天交互入口）
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

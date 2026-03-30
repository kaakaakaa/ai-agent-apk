// File: app/src/main/java/com/agent/apk/ui/AgentOverlay.kt
package com.agent.apk.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Button
import android.widget.ImageView
import com.agent.apk.R
import com.agent.apk.agent.cloud.ReActEngine
import com.agent.apk.agent.cloud.ReActStep
import com.agent.apk.model.Action
import com.agent.apk.model.ClickAction
import com.agent.apk.model.TypeAction
import com.agent.apk.model.NavigateAction
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.StateFlow

/**
 * Agent 任务执行悬浮窗
 *
 * 显示当前任务执行状态、思考过程和执行的动作
 */
class AgentOverlay(
    private val context: Context
) {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isVisible = false

    // UI 组件
    private var statusText: TextView? = null
    private var thoughtText: TextView? = null
    private var actionText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var stopButton: Button? = null
    private var expandButton: ImageView? = null

    // 状态
    private var isExpanded = false

    /**
     * 显示悬浮窗
     */
    fun show() {
        if (isVisible) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 使用带 MaterialComponents 主题的上下文来 inflate 布局
        val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_AndroidAgent)
        overlayView = LayoutInflater.from(themedContext).inflate(R.layout.agent_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 0
        }

        try {
            windowManager?.addView(overlayView, params)
            isVisible = true
            bindViews()
            setupListeners()
            updateStatus(null, isComplete = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayView = null
        isVisible = false
    }

    /**
     * 更新任务状态
     */
    fun updateStatus(step: ReActStep?, isComplete: Boolean) {
        if (!isVisible) {
            // 如果悬浮窗未显示，不更新
            return
        }

        if (isComplete) {
            statusText?.text = "任务完成"
            progressBar?.visibility = View.GONE
            thoughtText?.visibility = View.GONE
            actionText?.visibility = View.GONE
        } else {
            statusText?.text = "执行中... (步骤 ${step?.stepNumber ?: 1})"
            progressBar?.visibility = View.VISIBLE

            step?.thought?.let { thought ->
                thoughtText?.text = "💭 $thought"
                thoughtText?.visibility = View.VISIBLE
            } ?: run {
                thoughtText?.visibility = View.GONE
            }

            step?.action?.let { action ->
                actionText?.text = "👉 ${formatAction(action)}"
                actionText?.visibility = View.VISIBLE
            } ?: run {
                actionText?.visibility = View.GONE
            }
        }
    }

    /**
     * 设置停止回调
     */
    fun setOnStopListener(listener: () -> Unit) {
        stopButton?.setOnClickListener { listener() }
    }

    /**
     * 绑定视图
     */
    private fun bindViews() {
        overlayView?.let { view ->
            statusText = view.findViewById(R.id.statusText)
            thoughtText = view.findViewById(R.id.thoughtText)
            actionText = view.findViewById(R.id.actionText)
            progressBar = view.findViewById(R.id.progressBar)
            stopButton = view.findViewById(R.id.stopButton)
            expandButton = view.findViewById(R.id.expandButton)
        }
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        expandButton?.setOnClickListener {
            toggleExpand()
        }
    }

    /**
     * 切换展开/收起状态
     */
    private fun toggleExpand() {
        isExpanded = !isExpanded
        overlayView?.let { view ->
            thoughtText?.visibility = if (isExpanded) View.VISIBLE else View.GONE
            actionText?.visibility = if (isExpanded) View.VISIBLE else View.GONE

            expandButton?.rotation = if (isExpanded) 180f else 0f
        }
    }

    /**
     * 格式化动作显示
     */
    private fun formatAction(action: Action): String {
        return when (action) {
            is ClickAction -> "点击：${action.target}"
            is TypeAction -> "输入：${action.text}"
            is NavigateAction -> "导航：${action.target}"
            else -> action.toString()
        }
    }

    companion object {
        /**
         * 检查悬浮窗权限
         */
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        /**
         * 请求悬浮窗权限
         */
        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}

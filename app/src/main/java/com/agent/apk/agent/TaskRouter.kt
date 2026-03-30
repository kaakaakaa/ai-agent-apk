// File: app/src/main/java/com/agent/apk/agent/TaskRouter.kt
package com.agent.apk.agent

import com.agent.apk.infra.DeviceCapabilityProvider
import com.agent.apk.infra.DeviceTier
import com.agent.apk.model.AppSettings
import com.agent.apk.model.Task

/**
 * 任务分发器：决定任务由本地还是云端 Agent 处理
 */
class TaskRouter(
    private val networkStatus: NetworkStatus,
    private val deviceCapabilityProvider: DeviceCapabilityProvider,
    private val settings: AppSettings
) {
    /**
     * 路由决策：决定任务由哪个 Agent 处理
     */
    fun route(task: Task): AgentTarget {
        return when {
            // 无网络 → 本地
            networkStatus.isOffline() -> AgentTarget.LOCAL

            // 用户强制云端 → 云端
            settings.cloudFirst -> AgentTarget.CLOUD

            // 简单指令 → 优先云端（因为本地模型暂不可用）
            task.isSimpleCommand() -> {
                when (deviceCapabilityProvider.getDeviceTier()) {
                    DeviceTier.HIGH -> AgentTarget.LOCAL
                    DeviceTier.MEDIUM -> AgentTarget.CLOUD
                    DeviceTier.LOW -> AgentTarget.CLOUD
                }
            }

            // 复杂任务 → 云端 (ReAct 模式)
            else -> AgentTarget.CLOUD
        }
    }
}

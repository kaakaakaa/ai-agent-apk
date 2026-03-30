// File: app/src/main/java/com/agent/apk/agent/AgentTarget.kt
package com.agent.apk.agent

/**
 * Agent 目标选择：决定使用哪个模型执行任务
 */
enum class AgentTarget {
    /**
     * 自动选择：根据网络状态、设备能力、任务类型动态选择
     */
    AUTO,

    /**
     * 本地模型：始终使用本地模型（离线或用户强制指定）
     */
    LOCAL,

    /**
     * 云端模型：始终使用云端模型（更强的推理能力）
     */
    CLOUD
}

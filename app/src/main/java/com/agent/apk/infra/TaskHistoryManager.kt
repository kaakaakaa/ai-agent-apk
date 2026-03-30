// File: app/src/main/java/com/agent/apk/infra/TaskHistoryManager.kt
package com.agent.apk.infra

import android.content.Context
import androidx.room.Room
import com.agent.apk.model.Task
import com.agent.apk.model.TaskStatus
import com.agent.apk.model.ActionRecord
import com.agent.apk.model.ActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 任务历史管理器
 *
 * 封装 TaskHistoryDao，提供高层 API
 */
class TaskHistoryManager(private val context: Context) {

    private val database: TaskHistoryDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            TaskHistoryDatabase::class.java,
            "task_history_db"
        ).build()
    }

    private val dao: TaskHistoryDao by lazy {
        database.taskHistoryDao
    }

    /**
     * 保存任务记录
     */
    suspend fun saveTask(task: Task, actions: List<ActionRecord>, durationMs: Long, result: String?) {
        val entity = TaskHistoryEntity(
            id = task.id,
            userInstruction = task.userInstruction,
            timestamp = task.timestamp,
            status = task.status.name,
            assignedModel = task.assignedModel.name,
            result = result,
            executedActions = actions.size,
            durationMs = durationMs
        )
        dao.insertTask(entity)
    }

    /**
     * 获取任务历史列表
     */
    fun getTaskHistory(limit: Int = 20, offset: Int = 0): Flow<List<TaskSummary>> {
        return dao.getTasks(limit, offset).map { entities ->
            entities.map { it.toTaskSummary() }
        }
    }

    /**
     * 获取任务详情
     */
    suspend fun getTask(taskId: String): TaskDetail? {
        val entity = dao.getTaskById(taskId) ?: return null
        return entity.toTaskDetail()
    }

    /**
     * 更新任务状态
     */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus, result: String?, durationMs: Long) {
        dao.updateTaskStatus(taskId, status.name, result, durationMs)
    }

    /**
     * 删除任务
     */
    suspend fun deleteTask(taskId: String) {
        dao.deleteTask(taskId)
    }

    /**
     * 清理过期任务（超过 30 天）
     */
    suspend fun cleanupExpiredTasks() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteTasksOlderThan(thirtyDaysAgo)
    }

    /**
     * 获取任务总数
     */
    suspend fun getTotalTaskCount(): Int {
        return dao.getTaskCount()
    }

    /**
     * 获取平均完成时间（毫秒）
     */
    suspend fun getAverageCompletedDuration(): Double? {
        return dao.getAverageCompletedDuration()
    }

    /**
     * 获取指定状态的任务列表
     */
    fun getTasksByStatus(status: TaskStatus): Flow<List<TaskSummary>> {
        return dao.getTasksByStatus(status.name).map { entities ->
            entities.map { it.toTaskSummary() }
        }
    }

    /**
     * 关闭数据库
     */
    fun close() {
        database.close()
    }
}

/**
 * 任务摘要（用于列表显示）
 */
data class TaskSummary(
    val id: String,
    val userInstruction: String,
    val timestamp: Long,
    val status: TaskStatus,
    val assignedModel: String,
    val executedActions: Int,
    val durationMs: Long
)

/**
 * 任务详情（用于详情显示）
 */
data class TaskDetail(
    val id: String,
    val userInstruction: String,
    val timestamp: Long,
    val status: TaskStatus,
    val assignedModel: String,
    val result: String?,
    val executedActions: Int,
    val durationMs: Long
)

/**
 * 将 Entity 转换为 TaskSummary
 */
fun TaskHistoryEntity.toTaskSummary(): TaskSummary {
    return TaskSummary(
        id = id,
        userInstruction = userInstruction,
        timestamp = timestamp,
        status = TaskStatus.valueOf(status),
        assignedModel = assignedModel,
        executedActions = executedActions,
        durationMs = durationMs
    )
}

/**
 * 将 Entity 转换为 TaskDetail
 */
fun TaskHistoryEntity.toTaskDetail(): TaskDetail {
    return TaskDetail(
        id = id,
        userInstruction = userInstruction,
        timestamp = timestamp,
        status = TaskStatus.valueOf(status),
        assignedModel = assignedModel,
        result = result,
        executedActions = executedActions,
        durationMs = durationMs
    )
}

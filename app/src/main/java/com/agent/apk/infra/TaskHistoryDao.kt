// File: app/src/main/java/com/agent/apk/infra/TaskHistoryDao.kt
package com.agent.apk.infra

import androidx.room.*
import com.agent.apk.model.TaskStatus
import kotlinx.coroutines.flow.Flow

/**
 * 任务历史数据访问对象
 */
@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey val id: String,
    val userInstruction: String,
    val timestamp: Long,
    val status: String,
    val assignedModel: String,
    val result: String?,
    val executedActions: Int,
    val durationMs: Long
)

/**
 * 任务历史 DAO
 */
@Dao
interface TaskHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskHistoryEntity)

    @Query("SELECT * FROM task_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getTasks(limit: Int = 20, offset: Int = 0): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskHistoryEntity?

    @Query("SELECT * FROM task_history WHERE status = :status ORDER BY timestamp DESC")
    fun getTasksByStatus(status: String): Flow<List<TaskHistoryEntity>>

    @Query("UPDATE task_history SET status = :status, result = :result, durationMs = :durationMs WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, result: String?, durationMs: Long)

    @Query("DELETE FROM task_history WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM task_history WHERE timestamp < :threshold")
    suspend fun deleteTasksOlderThan(threshold: Long)

    @Query("SELECT COUNT(*) FROM task_history")
    suspend fun getTaskCount(): Int

    @Query("SELECT AVG(durationMs) FROM task_history WHERE status = 'COMPLETED'")
    suspend fun getAverageCompletedDuration(): Double?
}

/**
 * 任务历史数据库
 */
@Database(
    entities = [TaskHistoryEntity::class],
    version = 1
)
abstract class TaskHistoryDatabase : RoomDatabase() {
    abstract val taskHistoryDao: TaskHistoryDao
}

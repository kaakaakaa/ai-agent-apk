// File: app/src/main/java/com/agent/apk/infra/ReActStateManager.kt
package com.agent.apk.infra

import android.content.Context
import androidx.room.*
import com.agent.apk.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

/**
 * ReAct 状态管理器：持久化 ReAct 会话状态
 */
class ReActStateManager(private val context: Context) {

    private val database = Room.databaseBuilder(
        context,
        ReActDatabase::class.java,
        "react_state_database"
    ).build()

    private val sessionDao = database.sessionDao
    private val gson = Gson()

    /**
     * 保存会话
     */
    suspend fun saveSession(session: ReActSession) {
        val sessionEntity = ReActSessionEntity(
            id = session.taskId,
            userGoal = session.userGoal,
            startTime = session.startTime,
            status = session.status.name
        )
        sessionDao.upsertSession(sessionEntity)

        // 保存消息历史
        session.conversationHistory.forEachIndexed { index, message ->
            sessionDao.insertMessage(
                MessageEntity(
                    id = 0,
                    sessionId = session.taskId,
                    role = message.role,
                    content = message.content,
                    timestamp = message.timestamp,
                    orderIndex = index
                )
            )
        }

        // 保存执行的动作
        session.executedActions.forEachIndexed { index, record ->
            sessionDao.insertActionRecord(
                ActionRecordEntity(
                    id = 0,
                    sessionId = session.taskId,
                    actionType = record.action::class.java.simpleName,
                    actionJson = gson.toJson(record.action),
                    resultSuccess = record.result.success,
                    resultMessage = record.result.message ?: "",
                    timestamp = record.timestamp,
                    orderIndex = index
                )
            )
        }
    }

    /**
     * 获取会话
     */
    suspend fun getSession(taskId: String): ReActSession? {
        val sessionEntity = sessionDao.getSessionById(taskId) ?: return null

        val messages = sessionDao.getMessagesForSession(taskId)
            .sortedBy { it.orderIndex }
            .map { entity -> ReActMessage(entity.role, entity.content, entity.timestamp) }

        val actionRecords = sessionDao.getActionRecordsForSession(taskId)
            .sortedBy { it.orderIndex }
            .map { entity ->
                ActionRecord(
                    action = deserializeAction(entity.actionType, entity.actionJson) ?: UnknownAction("Unknown", "Unknown action"),
                    result = ActionResult(entity.resultSuccess, entity.resultMessage),
                    timestamp = entity.timestamp
                )
            }

        return ReActSession(
            taskId = sessionEntity.id,
            userGoal = sessionEntity.userGoal,
            startTime = sessionEntity.startTime,
            status = SessionStatus.valueOf(sessionEntity.status)
        ).apply {
            conversationHistory.addAll(messages)
            executedActions.addAll(actionRecords)
        }
    }

    /**
     * 获取所有会话
     */
    fun getAllSessions(): Flow<List<ReActSessionSummary>> {
        return sessionDao.getAllSessions()
    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(taskId: String) {
        sessionDao.deleteSession(taskId)
    }

    /**
     * 清理过期会话（超过 7 天）
     */
    suspend fun cleanupExpiredSessions() {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        sessionDao.deleteSessionsOlderThan(sevenDaysAgo)
    }

    /**
     * 反序列化 Action
     */
    private fun deserializeAction(type: String, json: String): Action? {
        return try {
            when (type) {
                "ClickAction" -> gson.fromJson(json, ClickAction::class.java)
                "SwipeAction" -> gson.fromJson(json, SwipeAction::class.java)
                "TypeAction" -> gson.fromJson(json, TypeAction::class.java)
                "OpenAppAction" -> gson.fromJson(json, OpenAppAction::class.java)
                "NavigateAction" -> gson.fromJson(json, NavigateAction::class.java)
                "ScrollAction" -> gson.fromJson(json, ScrollAction::class.java)
                "ScreenshotAction" -> gson.fromJson(json, ScreenshotAction::class.java)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Room Entities
@Entity(tableName = "react_sessions")
data class ReActSessionEntity(
    @PrimaryKey val id: String,
    val userGoal: String,
    val startTime: Long,
    val status: String
)

@Entity(tableName = "react_messages", indices = [Index(value = ["sessionId"])])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val orderIndex: Int
)

@Entity(tableName = "react_action_records", indices = [Index(value = ["sessionId"])])
data class ActionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val sessionId: String,
    val actionType: String,
    val actionJson: String,
    val resultSuccess: Boolean,
    val resultMessage: String,
    val timestamp: Long,
    val orderIndex: Int
)

@Dao
interface ReActSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ReActSessionEntity)

    @Query("SELECT * FROM react_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ReActSessionEntity?

    @Query("SELECT id, userGoal, startTime, status FROM react_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ReActSessionSummary>>

    @Query("DELETE FROM react_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM react_sessions WHERE startTime < :threshold")
    suspend fun deleteSessionsOlderThan(threshold: Long)

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM react_messages WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun getMessagesForSession(sessionId: String): List<MessageEntity>

    @Insert
    suspend fun insertActionRecord(record: ActionRecordEntity)

    @Query("SELECT * FROM react_action_records WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun getActionRecordsForSession(sessionId: String): List<ActionRecordEntity>
}

@Database(
    entities = [
        ReActSessionEntity::class,
        MessageEntity::class,
        ActionRecordEntity::class
    ],
    version = 1
)
abstract class ReActDatabase : RoomDatabase() {
    abstract val sessionDao: ReActSessionDao
}

// 会话摘要（用于列表显示）
data class ReActSessionSummary(
    val id: String,
    val userGoal: String,
    val startTime: Long,
    val status: String
)

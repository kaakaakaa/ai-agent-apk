// File: app/src/main/java/com/agent/apk/infra/ConversationHistoryDao.kt
package com.agent.apk.infra

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 对话历史数据访问对象
 */
@Dao
interface ConversationHistoryDao {

    @Query("SELECT * FROM conversation_history ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationHistoryEntity>>

    @Query("SELECT * FROM conversation_history WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getConversationBySession(sessionId: String): List<ConversationHistoryEntity>

    @Query("SELECT * FROM conversation_history WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getConversationBySessionFlow(sessionId: String): Flow<List<ConversationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationHistoryEntity>)

    @Query("DELETE FROM conversation_history WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM conversation_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversation_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int = 50): List<ConversationHistoryEntity>

    @Query("SELECT DISTINCT sessionId FROM conversation_history ORDER BY timestamp DESC")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT COUNT(*) FROM conversation_history WHERE sessionId = :sessionId")
    suspend fun getConversationCount(sessionId: String): Int
}

/**
 * 对话历史实体类
 */
@Entity(
    tableName = "conversation_history",
    indices = [Index(value = ["sessionId"], name = "idx_session_id")]
)
data class ConversationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val thought: String?,
    val action: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val taskCompleted: Boolean = false
)

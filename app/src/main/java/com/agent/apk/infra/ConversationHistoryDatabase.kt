// File: app/src/main/java/com/agent/apk/infra/ConversationHistoryDatabase.kt
package com.agent.apk.infra

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 对话历史数据库 - 持久化存储所有对话记录
 */
@Database(
    entities = [ConversationHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ConversationHistoryDatabase : RoomDatabase() {

    abstract fun conversationHistoryDao(): ConversationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: ConversationHistoryDatabase? = null

        fun getInstance(context: Context): ConversationHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConversationHistoryDatabase::class.java,
                    "conversation_history_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

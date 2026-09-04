package ai.deepcode.android.data.local

import android.content.Context
import androidx.room.*
import ai.deepcode.android.domain.model.ChatSession
import ai.deepcode.android.domain.model.Message
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val totalTokensInput: Long = 0L,
    val totalTokensOutput: Long = 0L,
    val totalCost: Double = 0.0,
    val compactionSummary: String? = null,
    val compactionTailMessageId: String? = null,
    val isCompacting: Boolean = false
) {
    fun toDomain() = ChatSession(id, title, createdAt, totalTokensInput, totalTokensOutput, totalCost, compactionSummary, compactionTailMessageId, isCompacting)
    companion object {
        fun fromDomain(domain: ChatSession) = SessionEntity(domain.id, domain.title, domain.createdAt, domain.totalTokensInput, domain.totalTokensOutput, domain.totalCost, domain.compactionSummary, domain.compactionTailMessageId, domain.isCompacting)
    }
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isToolCall: Boolean,
    val toolCallsJson: String?,
    val toolResultsJson: String?,
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0
) {
    fun toDomain() = Message(id, sessionId, role, content, timestamp, isToolCall, toolCallsJson, toolResultsJson, tokensInput, tokensOutput)
    companion object {
        fun fromDomain(domain: Message) = MessageEntity(
            domain.id, domain.sessionId, domain.role, domain.content, domain.timestamp,
            domain.isToolCall, domain.toolCallsJson, domain.toolResultsJson,
            domain.tokensInput, domain.tokensOutput
        )
    }
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE sessions SET title = :title WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSessionById(sessionId: String): Flow<SessionEntity?>

    @Query("UPDATE sessions SET totalTokensInput = totalTokensInput + :inputTokens, totalTokensOutput = totalTokensOutput + :outputTokens, totalCost = totalCost + :cost WHERE id = :sessionId")
    suspend fun accumulateSessionTokens(sessionId: String, inputTokens: Long, outputTokens: Long, cost: Double)

    @Query("UPDATE sessions SET compactionSummary = :summary, compactionTailMessageId = :tailMessageId, isCompacting = :compacting WHERE id = :sessionId")
    suspend fun updateCompactionState(sessionId: String, summary: String?, tailMessageId: String?, compacting: Boolean)

    @Query("UPDATE sessions SET isCompacting = :compacting WHERE id = :sessionId")
    suspend fun setCompacting(sessionId: String, compacting: Boolean)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesListForSession(sessionId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    fun getMessageCount(): kotlinx.coroutines.flow.Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND timestamp > :afterTimestamp")
    suspend fun deleteMessagesAfterTimestamp(sessionId: String, afterTimestamp: Long)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND (isToolCall = 1 OR role = 'tool')")
    suspend fun deleteToolMessagesForSession(sessionId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT * FROM messages WHERE role = 'assistant' ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentAssistantMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesList(): List<MessageEntity>
}

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ai.deepcode.android.ui.connections.IntegrationEntity::class,
        ai.deepcode.android.ui.automations.AutomationEntity::class,
        ai.deepcode.android.memory.MemoryChunk::class,
        ai.deepcode.android.ui.agents.AgentEntity::class,
        TokenUsageEntity::class,
        TokenEventEntity::class,
        PdfLayoutEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun integrationDao(): ai.deepcode.android.ui.connections.IntegrationDao
    abstract fun automationDao(): ai.deepcode.android.ui.automations.AutomationDao
    abstract fun memoryDao(): ai.deepcode.android.memory.MemoryDao
    abstract fun agentDao(): ai.deepcode.android.ui.agents.AgentDao
    abstract fun tokenUsageDao(): TokenUsageDao
    abstract fun tokenEventDao(): TokenEventDao
    abstract fun pdfLayoutDao(): PdfLayoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `integrations` (
                        `id` TEXT NOT NULL, 
                        `app_id` TEXT NOT NULL, 
                        `app_name` TEXT NOT NULL, 
                        `display_name` TEXT NOT NULL, 
                        `icon_url` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `access_token` TEXT NOT NULL, 
                        `refresh_token` TEXT NOT NULL, 
                        `scopes` TEXT NOT NULL, 
                        `connected_at` INTEGER NOT NULL, 
                        `last_synced_at` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `automations` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `is_enabled` INTEGER NOT NULL, 
                        `cron_expression` TEXT NOT NULL, 
                        `last_run_at` INTEGER NOT NULL, 
                        `next_run_at` INTEGER NOT NULL, 
                        `template_id` TEXT NOT NULL, 
                        `config_json` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `memory_chunks` USING fts4(
                        `id`, 
                        `title`, 
                        `content`, 
                        `source`, 
                        `tags`, 
                        `created_at`, 
                        `updated_at`, 
                        `embedding_hint`
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agents` (
                        `agent_id` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `agent_tier` TEXT NOT NULL DEFAULT 'worker',
                        `temperature` REAL NOT NULL DEFAULT 0.4,
                        `max_iterations` INTEGER NOT NULL DEFAULT 6,
                        `sandbox_mode` TEXT NOT NULL DEFAULT 'none',
                        `omit_identity` INTEGER NOT NULL DEFAULT 1,
                        `omit_memory_context` INTEGER NOT NULL DEFAULT 1,
                        `omit_safety_preamble` INTEGER NOT NULL DEFAULT 1,
                        `omit_skills_catalog` INTEGER NOT NULL DEFAULT 1,
                        `omit_profile` INTEGER NOT NULL DEFAULT 0,
                        `omit_memory_md` INTEGER NOT NULL DEFAULT 0,
                        `omit_skills_md` INTEGER NOT NULL DEFAULT 0,
                        `omit_safety_preamble` INTEGER NOT NULL DEFAULT 0,
                        `omit_memory_context` INTEGER NOT NULL DEFAULT 0,
                        `omit_identity` INTEGER NOT NULL DEFAULT 0,
                        `model_hint` TEXT NOT NULL DEFAULT 'agentic',
                        `delegate_name` TEXT,
                        `system_prompt` TEXT NOT NULL DEFAULT '',
                        `tools` TEXT NOT NULL DEFAULT '',
                        `subagents` TEXT NOT NULL DEFAULT '',
                        `is_builtin` INTEGER NOT NULL DEFAULT 1,
                        `is_enabled` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `last_run_at` INTEGER NOT NULL DEFAULT 0,
                        `run_count` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`agent_id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `token_usage` (
                        `session_id` TEXT NOT NULL,
                        `model_id` TEXT NOT NULL,
                        `provider_name` TEXT NOT NULL,
                        `tokens_input` INTEGER NOT NULL DEFAULT 0,
                        `tokens_output` INTEGER NOT NULL DEFAULT 0,
                        `tokens_reasoning` INTEGER NOT NULL DEFAULT 0,
                        `tokens_cache_read` INTEGER NOT NULL DEFAULT 0,
                        `tokens_cache_write` INTEGER NOT NULL DEFAULT 0,
                        `cost_usd` REAL NOT NULL DEFAULT 0.0,
                        `turn_count` INTEGER NOT NULL DEFAULT 0,
                        `time_created` INTEGER NOT NULL,
                        `time_updated` INTEGER NOT NULL,
                        PRIMARY KEY(`session_id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_usage_session_id` ON `token_usage` (`session_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_usage_time_updated` ON `token_usage` (`time_updated`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `token_event` (
                        `event_id` TEXT NOT NULL,
                        `session_id` TEXT NOT NULL,
                        `seq` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `model_id` TEXT NOT NULL,
                        `tokens_input` INTEGER NOT NULL DEFAULT 0,
                        `tokens_output` INTEGER NOT NULL DEFAULT 0,
                        `tokens_reasoning` INTEGER NOT NULL DEFAULT 0,
                        `tokens_cache_read` INTEGER NOT NULL DEFAULT 0,
                        `tokens_cache_write` INTEGER NOT NULL DEFAULT 0,
                        `cost_usd` REAL NOT NULL DEFAULT 0.0,
                        `prompt_bytes` INTEGER,
                        `time_created` INTEGER NOT NULL,
                        PRIMARY KEY(`event_id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_event_session_id` ON `token_event` (`session_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_event_session_seq` ON `token_event` (`session_id`, `seq`)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pdf_layouts` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `layoutJson` TEXT NOT NULL,
                        `isBuiltin` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN totalTokensInput INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN totalTokensOutput INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN totalCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN compactionSummary TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN compactionTailMessageId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN isCompacting INTEGER NOT NULL DEFAULT 0")
                
                db.execSQL("ALTER TABLE messages ADD COLUMN tokensInput INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN tokensOutput INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE automations ADD COLUMN chat_session_id TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deepcode_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build().also { INSTANCE = it }
            }
        }
    }
}

package com.iqsoo.qodirect.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rules") data class RuleEntity(@PrimaryKey val id: String, val json: String)

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val trigger: String,
    val action: String,
    val result: String,
    val code: String = "",
    val exitCode: Int? = null,
    val settings: Boolean? = null,
    val bypass: Boolean? = null,
)

@Dao
interface ChargeDao {
    @Query("SELECT * FROM rules ORDER BY id") fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY id") suspend fun rules(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(r: RuleEntity)

    @Query("DELETE FROM rules WHERE id=:id") suspend fun deleteRule(id: String)

    @Query("DELETE FROM rules") suspend fun clearRules()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(r: List<RuleEntity>)

    @Transaction
    suspend fun replaceRules(r: List<RuleEntity>) {
        clearRules()
        putRules(r)
    }

    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT 500") fun observeLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs ORDER BY id ASC") suspend fun logs(): List<LogEntry>

    @Insert suspend fun log(e: LogEntry)

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT 2000)")
    suspend fun prune()

    @Query("DELETE FROM logs") suspend fun clearLogs()
}

@Database(entities = [RuleEntity::class, LogEntry::class], version = 1, exportSchema = false)
abstract class ChargeDatabase : RoomDatabase() {
    abstract fun dao(): ChargeDao
}

package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uriString: String,
    val fileName: String,
    val extension: String,
    val mimeType: String,
    val categoryName: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
)

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val sourceFormat: String,
    val targetFormat: String,
    val outputName: String,
    val outputPath: String,
    val outputSizeBytes: Long,
    val succeeded: Boolean,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface MonoDocsDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT 50")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentFile(item: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uriString = :uriString")
    suspend fun deleteRecentFile(uriString: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    @Query("SELECT * FROM conversion_history ORDER BY createdAt DESC LIMIT 50")
    fun observeConversionHistory(): Flow<List<ConversionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversionHistory(item: ConversionHistoryEntity)

    @Query("DELETE FROM conversion_history")
    suspend fun clearConversionHistory()
}

@Database(
    entities = [RecentFileEntity::class, ConversionHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MonoDocsDatabase : RoomDatabase() {
    abstract fun monoDocsDao(): MonoDocsDao

    companion object {
        @Volatile
        private var INSTANCE: MonoDocsDatabase? = null

        fun getInstance(context: Context): MonoDocsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonoDocsDatabase::class.java,
                    "mono_docs.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class MonoDocsRepository(private val dao: MonoDocsDao) {
    val recentFiles: Flow<List<RecentFileEntity>> = dao.observeRecentFiles()
    val conversionHistory: Flow<List<ConversionHistoryEntity>> = dao.observeConversionHistory()

    suspend fun recordOpenedFile(item: RecentFileEntity) = dao.upsertRecentFile(item)
    suspend fun removeRecentFile(uriString: String) = dao.deleteRecentFile(uriString)
    suspend fun clearRecentFiles() = dao.clearRecentFiles()
    suspend fun recordConversion(item: ConversionHistoryEntity) = dao.insertConversionHistory(item)
    suspend fun clearConversionHistory() = dao.clearConversionHistory()
}

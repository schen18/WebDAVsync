package com.dissonance.webdav.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dissonance.webdav.data.dao.FileConflictDao
import com.dissonance.webdav.data.dao.SyncLogDao
import com.dissonance.webdav.data.dao.SyncPairDao
import com.dissonance.webdav.data.dao.SyncedFileRecordDao
import com.dissonance.webdav.data.dao.WebdavServerDao
import com.dissonance.webdav.data.model.ConflictPolicy
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.LogActionType
import com.dissonance.webdav.data.model.LogStatus
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncStatus
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.model.WebdavServer

class Converters {
  @TypeConverter
  fun fromSyncDirection(value: SyncDirection): String = value.name

  @TypeConverter
  fun toSyncDirection(value: String): SyncDirection = try {
    SyncDirection.valueOf(value)
  } catch (e: Exception) {
    SyncDirection.TWO_WAY
  }

  @TypeConverter
  fun fromConflictPolicy(value: ConflictPolicy): String = value.name

  @TypeConverter
  fun toConflictPolicy(value: String): ConflictPolicy = try {
    ConflictPolicy.valueOf(value)
  } catch (e: Exception) {
    ConflictPolicy.ASK_USER
  }

  @TypeConverter
  fun fromSyncStatus(value: SyncStatus): String = value.name

  @TypeConverter
  fun toSyncStatus(value: String): SyncStatus = try {
    SyncStatus.valueOf(value)
  } catch (e: Exception) {
    SyncStatus.IDLE
  }

  @TypeConverter
  fun fromLogActionType(value: LogActionType): String = value.name

  @TypeConverter
  fun toLogActionType(value: String): LogActionType = try {
    LogActionType.valueOf(value)
  } catch (e: Exception) {
    LogActionType.SCAN
  }

  @TypeConverter
  fun fromLogStatus(value: LogStatus): String = value.name

  @TypeConverter
  fun toLogStatus(value: String): LogStatus = try {
    LogStatus.valueOf(value)
  } catch (e: Exception) {
    LogStatus.SUCCESS
  }

  @TypeConverter
  fun fromConflictStatus(value: ConflictStatus): String = value.name

  @TypeConverter
  fun toConflictStatus(value: String): ConflictStatus = try {
    ConflictStatus.valueOf(value)
  } catch (e: Exception) {
    ConflictStatus.PENDING
  }

  @TypeConverter
  fun fromFileSyncState(value: FileSyncState): String = value.name

  @TypeConverter
  fun toFileSyncState(value: String): FileSyncState = try {
    FileSyncState.valueOf(value)
  } catch (e: Exception) {
    FileSyncState.SYNCED
  }
}

@Database(
  entities = [
    WebdavServer::class,
    SyncPair::class,
    SyncLog::class,
    FileConflict::class,
    SyncedFileRecord::class
  ],
  version = 4,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun webdavServerDao(): WebdavServerDao
  abstract fun syncPairDao(): SyncPairDao
  abstract fun syncLogDao(): SyncLogDao
  abstract fun fileConflictDao(): FileConflictDao
  abstract fun syncedFileRecordDao(): SyncedFileRecordDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    /**
     * v2 -> v3: sync pairs gained a persisted SAF tree Uri (localTreeUri).
     * File-record baselines reference the removed app-private vault layout and are
     * invalidated; wiping them (only) prevents stale baselines from propagating
     * phantom deletions to servers after the user re-links a folder.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_pairs ADD COLUMN localTreeUri TEXT NOT NULL DEFAULT ''")
        db.execSQL("DELETE FROM file_records")
      }
    }

    /**
     * v3 -> v4: the demo/simulation server flag was removed along with all mock
     * functionality. The table is recreated without the column; existing server
     * configurations are preserved.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS webdav_servers_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            url TEXT NOT NULL,
            username TEXT NOT NULL,
            password TEXT NOT NULL,
            trustSelfSigned INTEGER NOT NULL,
            lastConnectedAt INTEGER NOT NULL,
            createdAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "INSERT INTO webdav_servers_new (id, name, url, username, password, trustSelfSigned, lastConnectedAt, createdAt) " +
            "SELECT id, name, url, username, password, trustSelfSigned, lastConnectedAt, createdAt FROM webdav_servers"
        )
        db.execSQL("DROP TABLE webdav_servers")
        db.execSQL("ALTER TABLE webdav_servers_new RENAME TO webdav_servers")
      }
    }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "webdav_sync_db"
        )
          .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}

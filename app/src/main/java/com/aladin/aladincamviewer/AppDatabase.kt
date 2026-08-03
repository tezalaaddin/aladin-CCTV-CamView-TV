package com.aladin.aladincamviewer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CameraEntity::class, RecorderEntity::class, RecorderChannelEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao
    abstract fun recorderDao(): RecorderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cameras ADD COLUMN macAddress TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve the oldest record if an older development database
                // already contains exact duplicate IP entries.
                db.execSQL(
                    "DELETE FROM cameras WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM cameras GROUP BY ipAddress)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cameras_ipAddress " +
                        "ON cameras(ipAddress)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cameras ADD COLUMN onvifUsername TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cameras ADD COLUMN onvifPassword TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recorders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `ipAddress` TEXT NOT NULL, `httpPort` INTEGER NOT NULL, `rtspPort` INTEGER NOT NULL, `username` TEXT NOT NULL, `password` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, `protocol` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recorders_ipAddress_httpPort` ON `recorders` (`ipAddress`, `httpPort`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recorder_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recorderId` INTEGER NOT NULL, `channelNumber` INTEGER NOT NULL, `name` TEXT NOT NULL, `mainStreamUrl` TEXT NOT NULL, `subStreamUrl` TEXT NOT NULL, `enabled` INTEGER NOT NULL, FOREIGN KEY(`recorderId`) REFERENCES `recorders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recorder_channels_recorderId` ON `recorder_channels` (`recorderId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recorder_channels_recorderId_channelNumber` ON `recorder_channels` (`recorderId`, `channelNumber`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aladin_camera_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

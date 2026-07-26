package com.aladin.aladincamviewer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CameraEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aladin_camera_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

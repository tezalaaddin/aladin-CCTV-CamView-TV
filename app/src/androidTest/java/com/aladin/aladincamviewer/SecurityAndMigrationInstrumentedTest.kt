package com.aladin.aladincamviewer

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityAndMigrationInstrumentedTest {
    @Test fun credentialCryptoRoundTripDoesNotStorePlaintext() {
        val crypto = CredentialCrypto()
        val encrypted = crypto.encrypt("test-only-secret")
        assertNotEquals("test-only-secret", encrypted)
        assertTrue(encrypted.startsWith("enc:v1:"))
        assertEquals("test-only-secret", crypto.decrypt(encrypted))
    }

    @Test fun migrationThreeToFourAddsSeparateOnvifCredentials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        factory.create(configuration(context, databaseName, object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE cameras (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "ipAddress TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, " +
                        "mainStreamUrl TEXT NOT NULL, subStreamUrl TEXT NOT NULL, brand TEXT NOT NULL, " +
                        "ptzSupported INTEGER NOT NULL, displayOrder INTEGER NOT NULL, uuid TEXT NOT NULL, macAddress TEXT)"
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })).apply { writableDatabase; close() }

        val helper = factory.create(configuration(context, databaseName, object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                AppDatabase.MIGRATION_3_4.migrate(db)
            }
        }))
        val database = helper.writableDatabase
        val columns = mutableSetOf<String>()
        database.query("PRAGMA table_info(cameras)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
        }
        assertTrue("onvifUsername" in columns)
        assertTrue("onvifPassword" in columns)
        helper.close()
        context.deleteDatabase(databaseName)
    }

    private fun configuration(
        context: android.content.Context,
        name: String,
        callback: SupportSQLiteOpenHelper.Callback
    ) = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
}

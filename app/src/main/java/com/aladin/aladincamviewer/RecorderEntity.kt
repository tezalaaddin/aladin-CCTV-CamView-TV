package com.aladin.aladincamviewer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recorders",
    indices = [Index(value = ["ipAddress", "httpPort"], unique = true)]
)
data class RecorderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val httpPort: Int = 80,
    val rtspPort: Int = 554,
    val username: String,
    val password: String,
    val manufacturer: String = "Hikvision",
    val model: String = "",
    val serialNumber: String = "",
    val protocol: String = "HIKVISION_ISAPI",
    val createdAt: Long = System.currentTimeMillis()
)

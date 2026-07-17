package com.aladin.aladincamviewer

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "cameras")
@Serializable
data class CameraEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val username: String,
    val password: String,
    val mainStreamUrl: String,
    val subStreamUrl: String,
    val brand: String = "Custom",
    val ptzSupported: Boolean = false,
    val displayOrder: Int = 0,
    val uuid: String = "" // For DHCP IP auto-discovery
)

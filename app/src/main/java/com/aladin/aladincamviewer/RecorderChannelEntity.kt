package com.aladin.aladincamviewer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recorder_channels",
    foreignKeys = [ForeignKey(
        entity = RecorderEntity::class,
        parentColumns = ["id"],
        childColumns = ["recorderId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("recorderId"),
        Index(value = ["recorderId", "channelNumber"], unique = true)
    ]
)
data class RecorderChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recorderId: Long,
    val channelNumber: Int,
    val name: String,
    val mainStreamUrl: String,
    val subStreamUrl: String,
    val enabled: Boolean = true
)

data class RecorderChannelWithRecorder(
    @androidx.room.Embedded val channel: RecorderChannelEntity,
    @androidx.room.Relation(parentColumn = "recorderId", entityColumn = "id")
    val recorder: RecorderEntity
)

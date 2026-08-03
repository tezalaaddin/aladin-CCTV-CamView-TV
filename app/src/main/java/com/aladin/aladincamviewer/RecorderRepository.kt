package com.aladin.aladincamviewer

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map

class RecorderRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.recorderDao()
    private val crypto = CredentialCrypto()

    val recorders = dao.observeRecorders().map { it.map(::decryptRecorder) }
    val enabledChannels = dao.observeEnabledChannels().map { rows ->
        rows.map { it.copy(channel = decryptChannel(it.channel), recorder = decryptRecorder(it.recorder)) }
    }

    suspend fun save(recorder: RecorderEntity, channels: List<RecorderChannelEntity>): Long = db.withTransaction {
        require(recorder.serialNumber.isBlank() || dao.countBySerial(recorder.serialNumber, recorder.id) == 0) {
            "This recorder is already registered with the same serial number."
        }
        val encryptedRecorder = encryptRecorder(recorder)
        val id = if (recorder.id == 0L) dao.insertRecorder(encryptedRecorder) else {
            dao.updateRecorder(encryptedRecorder)
            recorder.id
        }
        dao.deleteChannels(id)
        dao.upsertChannels(channels.map { encryptChannel(it.copy(id = 0, recorderId = id)) })
        id
    }

    suspend fun getRecorder(id: Long) = dao.getRecorder(id)?.let(::decryptRecorder)
    suspend fun getChannels(id: Long) = dao.getChannels(id).map(::decryptChannel)
    suspend fun delete(recorder: RecorderEntity) = dao.deleteRecorder(encryptRecorder(recorder))

    private fun encryptRecorder(value: RecorderEntity) = value.copy(
        username = crypto.encrypt(value.username), password = crypto.encrypt(value.password)
    )
    private fun decryptRecorder(value: RecorderEntity) = value.copy(
        username = safeDecrypt(value.username), password = safeDecrypt(value.password)
    )
    private fun encryptChannel(value: RecorderChannelEntity) = value.copy(
        mainStreamUrl = crypto.encrypt(value.mainStreamUrl), subStreamUrl = crypto.encrypt(value.subStreamUrl)
    )
    private fun decryptChannel(value: RecorderChannelEntity) = value.copy(
        mainStreamUrl = safeDecrypt(value.mainStreamUrl), subStreamUrl = safeDecrypt(value.subStreamUrl)
    )
    private fun safeDecrypt(value: String) = runCatching { crypto.decrypt(value) }.getOrDefault("")
}

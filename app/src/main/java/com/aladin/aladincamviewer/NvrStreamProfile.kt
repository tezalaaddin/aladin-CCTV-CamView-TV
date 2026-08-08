package com.aladin.aladincamviewer

object NvrStreamProfile {
    const val HIKVISION = "Hikvision"
    const val TIANDY = "Tiandy"
    const val DAHUA = "Dahua"
    const val HANWHA = "Hanwha Wisenet"
    const val UNIVIEW = "Uniview (UNV)"
    const val XMEYE = "XMeye"

    fun protocol(manufacturer: String): String = when {
        manufacturer.equals(HIKVISION, true) -> "HIKVISION_ISAPI"
        else -> "${manufacturer.uppercase().replace(Regex("[^A-Z0-9]+"), "_")}_RTSP"
    }

    fun knownCapacity(manufacturer: String, model: String): Int? = when {
        manufacturer.equals(TIANDY, true) -> 20
        else -> HikvisionNvrProfile.knownCapacity(model)
    }

    fun livePath(
        manufacturer: String,
        channel: Int,
        subStream: Boolean,
        username: String = "",
        password: String = ""
    ): String {
        require(channel > 0) { "Channel must be positive" }
        return when {
            manufacturer.equals(TIANDY, true) -> "/$channel/${if (subStream) 2 else 1}"
            manufacturer.equals(DAHUA, true) -> "/cam/realmonitor?channel=$channel&subtype=${if (subStream) 1 else 0}"
            manufacturer.equals(HANWHA, true) -> "/${channel - 1}/profile${if (subStream) 2 else 1}/media.smp"
            manufacturer.equals(UNIVIEW, true) -> "/unicast/c$channel/s${if (subStream) 1 else 0}/live"
            manufacturer.equals(XMEYE, true) ->
                "/user=${encode(username)}&password=${encode(password)}&channel=$channel&stream=${if (subStream) 1 else 0}.sdp?real_stream"
            else -> "/Streaming/Channels/${HikvisionNvrProfile.streamId(channel, subStream)}"
        }
    }

    fun supportsRecordingSearch(manufacturer: String) = true

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

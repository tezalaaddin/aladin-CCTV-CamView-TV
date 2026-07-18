package com.aladin.aladincamviewer

object BrandMatcher {
    private val OUI_MAP = mapOf(
        // Hikvision
        "18:68:CB" to "Hikvision",
        "44:19:B6" to "Hikvision",
        "4C:BD:8F" to "Hikvision",
        "80:F5:AE" to "Hikvision",
        "8C:E7:48" to "Hikvision",
        "94:E1:AC" to "Hikvision",
        "98:9D:E5" to "Hikvision",
        "D0:03:DF" to "Hikvision",
        
        // Dahua
        "14:A7:8B" to "Dahua",
        "4C:11:BF" to "Dahua",
        "90:02:A9" to "Dahua",
        "3C:E3:6B" to "Dahua",
        "5C:F5:1A" to "Dahua",
        "BC:32:5F" to "Dahua",
        
        // Axis
        "00:40:8C" to "Axis",
        "AC:CC:8E" to "Axis",
        "B8:A4:4F" to "Axis",
        
        // Hanwha / Samsung
        "00:09:18" to "Hanwha",
        "44:B4:23" to "Hanwha",
        
        // Uniview
        "48:EA:63" to "Uniview",
        "6C:F1:7E" to "Uniview",
        "88:26:3F" to "Uniview",
        
        // Tiandy
        "00:0B:3F" to "Tiandy",
        "3C:DA:6D" to "Tiandy",
        
        // Bosch
        "00:07:5F" to "Bosch",
        "04:26:05" to "Bosch"
    )

    fun getBrandByMac(mac: String?): String {
        if (mac.isNullOrBlank()) return "Generic"
        val normalizedMac = mac.replace(":", "").replace("-", "").uppercase()
        if (normalizedMac.length < 6) return "Generic"
        
        val oui = normalizedMac.substring(0, 6)
        val formattedOui = "${oui.substring(0,2)}:${oui.substring(2,4)}:${oui.substring(4,6)}"
        
        return OUI_MAP[formattedOui] ?: "Generic"
    }

    fun detectFromResponse(response: String): String? {
        val r = response.lowercase()
        return when {
            r.contains("hikvision") -> "Hikvision"
            r.contains("dahua") || r.contains("ipc-") -> "Dahua"
            r.contains("tiandy") -> "Tiandy"
            r.contains("uniview") -> "Uniview"
            r.contains("hanwha") || r.contains("samsung") -> "Hanwha"
            r.contains("axis") -> "Axis"
            else -> null
        }
    }
}

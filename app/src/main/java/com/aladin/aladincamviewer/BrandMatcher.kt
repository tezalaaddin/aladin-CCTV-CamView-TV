package com.aladin.aladincamviewer

object BrandMatcher {
    private val OUI_MAP = mapOf(
        // Hikvision (Extensive)
        "18:68:CB" to "Hikvision", "44:19:B6" to "Hikvision", "4C:BD:8F" to "Hikvision",
        "80:F5:AE" to "Hikvision", "8C:E7:48" to "Hikvision", "94:E1:AC" to "Hikvision",
        "98:9D:E5" to "Hikvision", "D0:03:DF" to "Hikvision", "00:40:3C" to "Hikvision",
        "28:57:BE" to "Hikvision", "30:60:59" to "Hikvision", "34:6B:D3" to "Hikvision",
        "54:E4:BD" to "Hikvision", "68:6D:BC" to "Hikvision", "70:BD:BC" to "Hikvision",
        "7C:25:DA" to "Hikvision", "A4:14:37" to "Hikvision", "B0:AD:AA" to "Hikvision",
        "BC:AD:28" to "Hikvision", "C0:56:E3" to "Hikvision", "E4:F0:42" to "Hikvision",
        "F4:32:D9" to "Hikvision", "F4:CF:A2" to "Hikvision",

        // Dahua (Extensive)
        "14:A7:8B" to "Dahua", "4C:11:BF" to "Dahua", "90:02:A9" to "Dahua",
        "3C:E3:6B" to "Dahua", "5C:F5:1A" to "Dahua", "BC:32:5F" to "Dahua",
        "00:12:12" to "Dahua", "18:A6:F7" to "Dahua", "38:AF:29" to "Dahua",
        "44:19:B7" to "Dahua", "60:1D:0F" to "Dahua", "6C:02:E0" to "Dahua",
        "70:AF:24" to "Dahua", "80:2A:A8" to "Dahua", "A0:BD:1D" to "Dahua",
        "B0:D5:9D" to "Dahua", "BC:32:5E" to "Dahua", "E0:50:8B" to "Dahua",
        "F4:B3:81" to "Dahua",

        // Uniview (UNV)
        "48:EA:63" to "Uniview", "6C:F1:7E" to "Uniview", "88:26:3F" to "Uniview",
        "B0:E4:D5" to "Uniview", "00:E0:4C" to "Uniview",

        // Axis
        "00:40:8C" to "Axis", "AC:CC:8E" to "Axis", "B8:A4:4F" to "Axis",
        "00:07:4D" to "Axis", "E8:27:25" to "Axis",

        // Hanwha / Samsung
        "00:09:18" to "Hanwha", "44:B4:23" to "Hanwha", "00:16:6C" to "Hanwha",
        "00:07:D8" to "Hanwha",

        // Tiandy
        "00:0B:3F" to "Tiandy", "3C:DA:6D" to "Tiandy",

        // Bosch
        "00:07:5F" to "Bosch", "04:26:05" to "Bosch", "00:0F:19" to "Bosch",

        // XMeye / Xiongmai (Generic China)
        "00:12:13" to "XMeye", "00:3E:0B" to "XMeye", "F4:B3:81" to "XMeye",

        // TP-Link (Vigi/Tapo)
        "1C:3B:F3" to "TP-Link", "30:B5:C2" to "TP-Link", "98:25:4A" to "TP-Link",
        
        // Reolink
        "EC:71:DB" to "Reolink", "A4:D1:8C" to "Reolink",

        // Aselsan
        "00:03:73" to "Aselsan"
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
            r.contains("hikvision") || r.contains("ds-") || r.contains("hik-") -> "Hikvision"
            r.contains("dahua") || r.contains("ipc-") || r.contains("dh-") -> "Dahua"
            r.contains("tiandy") || r.contains("tc-") -> "Tiandy"
            r.contains("uniview") || r.contains("unv-") -> "Uniview"
            r.contains("ajcloud") -> "AJCloud"
            r.contains("hanwha") || r.contains("samsung") -> "Hanwha"
            r.contains("axis") -> "Axis"
            r.contains("xiongmai") || r.contains("xmeye") || r.contains("uc-") -> "XMeye"
            r.contains("tp-link") || r.contains("vigi") || r.contains("tapo") -> "TP-Link"
            r.contains("reolink") -> "Reolink"
            r.contains("mobotix") -> "Mobotix"
            r.contains("vivotek") -> "Vivotek"
            else -> null
        }
    }

    fun detectFromPort(port: Int): String? {
        return when (port) {
            8000 -> "Hikvision"
            37777, 37778 -> "Dahua"
            34567, 34599 -> "XMeye"
            3002 -> "Tiandy"
            9000 -> "Reolink"
            8200 -> "Uniview"
            4520, 4524 -> "Hanwha"
            else -> null
        }
    }
}

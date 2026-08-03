param(
    [string]$Device
)

$deviceArgs = if ($Device) { @("-s", $Device) } else { @() }
$tags = @(
    "ALADIN_VLC:V",
    "ALADIN_NETWORK:V",
    "ALADIN_NETWORK_TRACKER:V",
    "ALADIN_DISCOVERY:V",
    "ALADIN_DEBUG_ONVIF:V",
    "ALADIN_PTZ:V",
    "ALADIN_WATCHDOG:V",
    "ALADIN_DIAG:V",
    "ALADIN_NVR:V",
    "ALADIN_REPLAY:V",
    "AndroidRuntime:E",
    "*:S"
)

& adb @deviceArgs logcat -v color -s @tags

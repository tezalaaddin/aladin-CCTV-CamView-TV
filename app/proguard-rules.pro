# LibVLC ProGuard Rules
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.vlc.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

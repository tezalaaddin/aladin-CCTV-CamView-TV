# Jetpack Media3 ExoPlayer Rules
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.exoplayer.rtsp.** { *; }

# Keep our package classes from being stripped (Room, ViewModel, Models)
-keep class com.aladin.aladincamviewer.** { *; }

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Dao
-keep class * extends androidx.room.Entity

# Keep ViewModel constructors
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Keep everything required for Kotlin Parcelize
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ONVIF / XML Parsing logic preservation
-keepclassmembers class ** {
    @org.json.* *;
}

# Prevent obfuscation of some essential Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

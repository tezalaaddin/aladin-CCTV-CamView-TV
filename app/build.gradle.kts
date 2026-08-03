import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val isBundleBuild = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("bundle", ignoreCase = true)
}
val releaseKeystoreProperties = Properties()
val releaseKeystoreFile = rootProject.file("keystore.properties")
if (releaseKeystoreFile.isFile) {
    releaseKeystoreFile.inputStream().use(releaseKeystoreProperties::load)
}

android {
    namespace = "com.aladin.aladincamviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aladin.aladincamviewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🚀 OPTIMIZATION: Most TVs use ARM. Removing x86/x86_64 saves ~100MB
    }
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseKeystoreFile.isFile) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // LibVLC native binaries dominate APK size. Produce one APK per TV ABI
    // instead of shipping both 32-bit and 64-bit engines to every device.
    splits {
        abi {
            // Android Gradle Plugin cannot build an AAB while ABI APK splits are enabled.
            // AAB already lets Google Play generate device-specific APKs automatically.
            isEnable = !isBundleBuild
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 🚀 OPTIMIZATION: Enable shrinking and obfuscation
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystoreFile.isFile) signingConfig = signingConfigs.getByName("release")
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // LibVLC for robust RTSP (Main Engine)
    implementation("org.videolan.android:libvlc-all:3.6.5")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}

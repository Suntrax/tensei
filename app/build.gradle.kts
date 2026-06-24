import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
}

// 1. Load the local.properties file
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.blissless.tensei"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blissless.tensei"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.5"

        val anilistApiKey = localProperties.getProperty("CLIENT_ID_ANILIST")
        val tmdbApiKey = localProperties.getProperty("TMDB_API_KEY")
        val malClientId = localProperties.getProperty("MAL_CLIENT_ID") ?: ""

        buildConfigField("String", "CLIENT_ID_ANILIST", "\"$anilistApiKey\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")
        val malClientSecret = localProperties.getProperty("MAL_CLIENT_SECRET") ?: ""
        buildConfigField("String", "MAL_CLIENT_SECRET", "\"$malClientSecret\"")
    }

    // 1. ADD THIS: Configure your signing keys here
    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = localProperties.getProperty("KEY_ALIAS")
                keyPassword = localProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // 2. ADD THIS: Tell the release build to use the signing config above
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Generate separate APKs for each ABI
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

abstract class RenameApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDir: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun rename() {
        val dir = apkDir.get().asFile
        dir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
            val target = File(dir, when {
                apk.name.contains("arm64-v8a") -> "Tensei-arm64-v8a.apk"
                apk.name.contains("armeabi-v7a") -> "Tensei-armeabi-v7a.apk"
                else -> "Tensei-${versionName.get()}.apk"
            })
            apk.renameTo(target)
            if (!target.exists() && apk.exists()) {
                apk.copyTo(target, overwrite = true)
                apk.delete()
            }
        }
    }
}

val renameReleaseApk = tasks.register<RenameApkTask>("renameReleaseApk") {
    description = "renaming apks to tensei<arch>.apk"
    apkDir = layout.buildDirectory.dir("outputs/apk/release")
    versionName = android.defaultConfig.versionName
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(renameReleaseApk)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx.v2100)
    implementation(libs.rxjava)
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.jsoup.v1181)
    implementation(libs.androidx.preference.ktx)
}
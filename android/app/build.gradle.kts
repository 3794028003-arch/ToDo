import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val debugSyncBaseUrl = providers.gradleProperty("SYNC_BASE_URL").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: localProperties.getProperty("SYNC_BASE_URL")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    ?: "http://10.0.2.2:8080/"

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.localfirst.app"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        applicationId = "com.example.localfirst"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "SYNC_BASE_URL", debugSyncBaseUrl.asBuildConfigString())
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "SYNC_BASE_URL", "\"https://example.invalid/\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:work"))
    implementation(project(":feature:board"))

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime:2.11.2")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

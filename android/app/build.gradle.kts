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

val releaseSigningPropertiesFile = providers.gradleProperty("DOTI_SIGNING_PROPERTIES").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(rootProject::file)
    ?: localProperties.getProperty("DOTI_SIGNING_PROPERTIES")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)

val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile?.isFile == true) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

val isReleaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

if (isReleaseBuildRequested && releaseSigningPropertiesFile?.isFile != true) {
    error(
        "DoTi release signing is not configured. Set DOTI_SIGNING_PROPERTIES " +
            "in android/local.properties or as a Gradle property.",
    )
}

fun Properties.requiredSigningValue(name: String): String =
    getProperty(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("Missing '$name' in ${releaseSigningPropertiesFile?.absolutePath}")

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
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (releaseSigningPropertiesFile?.isFile == true) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.requiredSigningValue("storeFile"))
                storePassword = releaseSigningProperties.requiredSigningValue("storePassword")
                keyAlias = releaseSigningProperties.requiredSigningValue("keyAlias")
                keyPassword = releaseSigningProperties.requiredSigningValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "SYNC_BASE_URL", debugSyncBaseUrl.asBuildConfigString())
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "SYNC_BASE_URL", debugSyncBaseUrl.asBuildConfigString())
            manifestPlaceholders["usesCleartextTraffic"] = debugSyncBaseUrl.startsWith("http://").toString()
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
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

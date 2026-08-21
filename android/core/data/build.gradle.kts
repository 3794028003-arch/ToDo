plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:sync"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

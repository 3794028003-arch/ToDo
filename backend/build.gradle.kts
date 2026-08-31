import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    configurations.classpath {
        resolutionStrategy.force(
            "com.google.code.gson:gson:2.11.0",
            "com.google.errorprone:error_prone_annotations:2.30.0",
        )
    }
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        classpath("org.jetbrains.kotlin:kotlin-allopen:2.2.10")
        classpath("org.springframework.boot:spring-boot-gradle-plugin:4.1.0")
        classpath("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6")
    }
}

apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "org.springframework.boot")
apply(plugin = "dev.detekt")

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
}

dependencies {
    add("implementation", platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    add("implementation", "org.springframework.boot:spring-boot-starter-webmvc")
    add("implementation", "org.springframework.boot:spring-boot-starter-jdbc")
    add("implementation", "org.springframework.boot:spring-boot-starter-flyway")
    add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
    add("implementation", "org.springframework.security:spring-security-crypto")
    add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect")

    add("runtimeOnly", "org.postgresql:postgresql")
    add("runtimeOnly", "org.flywaydb:flyway-database-postgresql")

    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
    add("testImplementation", "org.springframework.boot:spring-boot-starter-webmvc-test")
    add("testImplementation", "org.springframework.boot:spring-boot-testcontainers")
    add("testImplementation", "org.testcontainers:testcontainers-postgresql")
    add("testImplementation", "org.testcontainers:testcontainers-junit-jupiter")
    add("testRuntimeOnly", "org.junit.vintage:junit-vintage-engine")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

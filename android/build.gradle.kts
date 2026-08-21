buildscript {
    configurations.classpath {
        resolutionStrategy.force(
            "com.google.code.gson:gson:2.11.0",
            "com.google.errorprone:error_prone_annotations:2.30.0",
        )
    }
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    }
}

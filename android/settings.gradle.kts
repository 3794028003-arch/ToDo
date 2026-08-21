pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "local-first-task-android"
include(":app")
include(":core:sync")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:work")
include(":feature:board")

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

rootProject.name = "HeartBeets"
include(":app")
include(":core")
include(":ble")
include(":data")
include(":service")
include(":driver-veepoo")
include(":driver-standard-hrs")

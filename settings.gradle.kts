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
include(":audio")
include(":service")
include(":driver-veepoo")
include(":driver-standard-hrs")
include(":driver-huami")
include(":driver-colmi")
include(":driver-huawei")
include(":driver-galaxy")
include(":sharing")

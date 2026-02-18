pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/gradle")
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?")
            }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            content {
                excludeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/jitpack") }
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url=uri("https://jitpack.io") }
    }
}

rootProject.name = "tachiyomiJ2K"
include(":app")

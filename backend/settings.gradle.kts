pluginManagement {
    val viaductVersion: String by settings

    repositories {
        gradlePluginPortal()
    }
    plugins {
        id("com.airbnb.viaduct.settings-gradle-plugin") version viaductVersion
    }
}

plugins {
    id("com.airbnb.viaduct.settings-gradle-plugin")
}

val viaductVersion: String by settings

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // This injects a dynamic value that your TOML can reference.
            version("viaduct", viaductVersion)
        }
    }
}

rootProject.name = "viaduct-backend"

includeViaductApplication {
    project(":")
    modulePackagePrefix("com.example")

    includeModule {
        project(":")
        modulePackageSuffix("resolvers")
    }
}

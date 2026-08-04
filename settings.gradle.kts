@file:Suppress("UnstableApiUsage")

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

rootProject.name = "repcounter"

include(
    ":app",
    ":core:model",
    ":core:dsp",
    ":pose:api",
    ":pose:mediapipe",
    ":pose:movenet",
    ":signals",
    ":analysis:api",
    ":analysis:jumprope",
    ":analysis:strength",
    ":capture",
    ":data",
    ":feature:workout",
    ":tools:replay",
)

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
        // ARSCLib is published through JitPack, which serves the bare jar without a usable
        // Gradle module metadata, so Gradle has to be told to read the pom and the artifact.
        maven("https://jitpack.io") {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}

rootProject.name = "AntiSplit-NG"
include(":app")

// ARSCLib as a checked-out sibling directory replaces the JitPack artifact, so a local
// ARSCLib patch can be built and tested without publishing anything.
val localArsclib = file("../ARSCLib")
if (localArsclib.isDirectory) {
    includeBuild(localArsclib) {
        dependencySubstitution {
            substitute(module("com.github.MorpheApp:ARSCLib")).using(project(":"))
        }
    }
}

// Add this annotation at the top of the file to acknowledge you are using incubating APIs.
// This will remove the warnings from your IDE.
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // This setting correctly centralizes repository management.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The explicit 'maven { setUrl(...) }' was removed as 'google()' does the same thing.
    }
}

// Define the project name and included modules once.
rootProject.name = "EE012"
include(":app")

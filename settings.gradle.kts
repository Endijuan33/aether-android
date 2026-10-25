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
        exclusiveContent {
            forRepository {
                ivy {
                    name = "ktfmtRelease"
                    url = uri("https://github.com/Kotlin/ktfmt/releases/download")
                    patternLayout { artifact("v[revision]/ktfmt-[revision]-with-dependencies.jar") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.facebook", "ktfmt") }
        }
    }
}
rootProject.name = "aether"
include(":app")

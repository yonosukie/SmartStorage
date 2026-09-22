pluginManagement { repositories {
    if (providers.gradleProperty("useMirror").orNull == "true") {
        maven("https://maven.aliyun.com/repository/google") { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") } }
        maven("https://maven.aliyun.com/repository/central")
    }
    google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") } }
    mavenCentral(); gradlePluginPortal()
} }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("useMirror").orNull == "true") {
            maven("https://maven.aliyun.com/repository/google") { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") } }
            maven("https://maven.aliyun.com/repository/central")
        }
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") } }
        mavenCentral()
    }
}
rootProject.name = "SmartStorage"
include(":app", ":core")

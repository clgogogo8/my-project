pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "multi-open"
include(":app", ":plugin-api", ":demo-plugin", ":test-hello", ":test-multi")

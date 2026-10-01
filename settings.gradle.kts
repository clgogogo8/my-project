pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "multi-open"
include(":app", ":plugin-api", ":demo-plugin", ":test-hello", ":test-multi")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Compatible from 2022.3 (223) with no upper bound. Built against 2025.2; the plugin
            // uses only long-stable platform APIs, so it isn't pinned to the build platform's
            // version. Run `./gradlew verifyPlugin` to confirm no newer-than-223 API is used.
            sinceBuild = "223"
            untilBuild = provider { null }
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Plugin versions are managed by the version catalog (gradle/libs.versions.toml).

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply true
}

spotless {
    kotlin {
        target(
            project.fileTree(mapOf("dir" to "app", "include" to listOf("src/**/*.kt"))),
        )
        ktlint().editorConfigOverride(mapOf(
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "max_line_length" to "120",
        ))
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Plugin versions are managed by the parent project (nars-android-maplibre) via version catalog

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

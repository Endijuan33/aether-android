// Top-level build file for AETHER — System Control.
//
// NOTE (AGP 9): Kotlin support is built into AGP itself — the legacy
// org.jetbrains.kotlin.android plugin must NOT be applied (it fails the
// build). The Compose compiler plugin (org.jetbrains.kotlin.plugin.compose)
// is still applied per-module in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

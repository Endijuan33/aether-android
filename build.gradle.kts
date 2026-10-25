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

val ktfmtTool = configurations.create("ktfmtTool")
dependencies {
    add(ktfmtTool.name, libs.ktfmt)
}

val kotlinSources = fileTree("app/src") { include("**/*.kt") }
val verifyKtfmt = tasks.register("verifyKtfmt") {
    inputs.files(ktfmtTool)
    doLast {
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(ktfmtTool.singleFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == "aba9753b6ab387926179ab7318ba8e0c33dad483ebc72f633e79522265b15214") {
            "ktfmt 0.65 checksum does not match its published release digest"
        }
    }
}
tasks.register<JavaExec>("ktfmtCheck") {
    dependsOn(verifyKtfmt)
    group = "verification"
    description = "Checks Kotlin formatting using the same formatter and style as CI."
    classpath = ktfmtTool
    mainClass.set(provider {
        java.util.jar.JarFile(ktfmtTool.singleFile).use { it.manifest.mainAttributes.getValue("Main-Class") }
    })
    inputs.files(kotlinSources)
    doFirst {
        args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed")
        args(kotlinSources.files.sorted().map { it.absolutePath })
    }
}

tasks.register<JavaExec>("ktfmtFormat") {
    dependsOn(verifyKtfmt)
    group = "formatting"
    description = "Formats specified -PktfmtFiles paths, or all Kotlin sources if unspecified."
    classpath = ktfmtTool
    mainClass.set(provider {
        java.util.jar.JarFile(ktfmtTool.singleFile).use { it.manifest.mainAttributes.getValue("Main-Class") }
    })
    doFirst {
        args("--kotlinlang-style")
        val selected = providers.gradleProperty("ktfmtFiles").orNull?.split(",")?.map { file(it) }
            ?: kotlinSources.files.sorted()
        args(selected.map { it.absolutePath })
    }
}

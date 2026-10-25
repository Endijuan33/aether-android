plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "id.mybase.aether"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "id.mybase.aether"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9 has built-in Kotlin: the `kotlin` extension is registered by AGP
// itself; do NOT apply the legacy org.jetbrains.kotlin.android plugin.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    // BouncyCastle provides JVM-only TLS fixtures; it is not bundled in the APK.
    testImplementation(libs.bcprov)
    testImplementation(libs.bctls)
    testImplementation(libs.bcutil)
    // Standalone Conscrypt exposes the public TLS exporter needed for pairing.
    implementation(libs.conscrypt.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

dependencyLocking { lockAllConfigurations() }

tasks.register("exportRuntimeDependencies") {
    group = "verification"
    description = "Exports resolved release dependencies for the advisory check."
    val destination = layout.buildDirectory.file("reports/release-dependencies.txt")
    outputs.file(destination)
    // Resolve on each invocation so dependency changes cannot leave a stale advisory input.
    outputs.upToDateWhen { false }
    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents.mapNotNull {
                it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
            }.map { "${it.group}:${it.module}:${it.version}" }.distinct().sorted()
        destination.get().asFile.apply {
            parentFile.mkdirs()
            writeText(components.joinToString("\n", postfix = "\n"))
        }
    }
}

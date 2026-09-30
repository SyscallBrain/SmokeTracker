plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/*
 * Release signing (R6/T9). The values come from Gradle properties kept outside the repository
 * (`~/.gradle/gradle.properties`, chmod 600, written by `tools/make-release-keystore.sh`).
 * Without them the release build has no signingConfig and the `checkReleaseSigning` task fails —
 * only for release tasks; debug builds and tests do not depend on these properties.
 * The debug key is never used for release.
 */
val releaseSigningProps = listOf(
    "SMOKETRACKER_STORE_FILE",
    "SMOKETRACKER_STORE_PASSWORD",
    "SMOKETRACKER_KEY_ALIAS",
    "SMOKETRACKER_KEY_PASSWORD",
).associateWith { providers.gradleProperty(it).orNull?.takeIf(String::isNotBlank) }
val releaseSigningMissing: List<String> = releaseSigningProps.filterValues { it == null }.keys.toList()

android {
    namespace = "pt.tiago.cinzeiro"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "pt.tiago.cinzeiro"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningMissing.isEmpty()) {
            create("release") {
                storeFile = file(releaseSigningProps.getValue("SMOKETRACKER_STORE_FILE")!!)
                storePassword = releaseSigningProps.getValue("SMOKETRACKER_STORE_PASSWORD")
                keyAlias = releaseSigningProps.getValue("SMOKETRACKER_KEY_ALIAS")
                keyPassword = releaseSigningProps.getValue("SMOKETRACKER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the properties this stays null (unsigned APK) and checkReleaseSigning stops the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 4.17 with SDK 36 accesses jdk.internal.access (ApplicationSharedMemory).
            it.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED")
            it.jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

/** Fails with a clear message if any signing property is missing (release tasks only). */
abstract class CheckReleaseSigning : DefaultTask() {
    @get:Input
    abstract val missing: ListProperty<String>

    @TaskAction
    fun check() {
        val m = missing.get()
        if (m.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured: missing Gradle properties ${m.joinToString(", ")}. " +
                    "Run tools/make-release-keystore.sh (it writes them to ~/.gradle/gradle.properties) or see the " +
                    "README, section \"Release build\". The release build is never signed with the debug key."
            )
        }
    }
}

val checkReleaseSigning = tasks.register<CheckReleaseSigning>("checkReleaseSigning") {
    missing.set(releaseSigningMissing)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

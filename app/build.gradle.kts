plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/*
 * Assinatura release (R6/T9). Os valores vêm de propriedades Gradle fora do repositório
 * (`~/.gradle/gradle.properties`, chmod 600, escritas por `tools/make-release-keystore.sh`).
 * Sem elas, o release fica sem signingConfig e a tarefa `checkReleaseSigning` falha — só nas
 * tarefas de release; debug e testes não dependem destas propriedades.
 * Nunca se usa a chave debug no release.
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
            // Sem as propriedades fica null (APK não assinado) e checkReleaseSigning trava o build.
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
            // Robolectric 4.17 com SDK 36 acede a jdk.internal.access (ApplicationSharedMemory).
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

/** Falha com mensagem clara se faltar alguma propriedade de assinatura (só em tarefas de release). */
abstract class CheckReleaseSigning : DefaultTask() {
    @get:Input
    abstract val missing: ListProperty<String>

    @TaskAction
    fun check() {
        val m = missing.get()
        if (m.isNotEmpty()) {
            throw GradleException(
                "Assinatura release não configurada: faltam as propriedades Gradle ${m.joinToString(", ")}. " +
                    "Corre tools/make-release-keystore.sh (escreve-as em ~/.gradle/gradle.properties) — ver README, secção Release. " +
                    "O release nunca é assinado com a chave debug."
            )
        }
    }
}

val checkReleaseSigning = tasks.register<CheckReleaseSigning>("checkReleaseSigning") {
    missing.set(releaseSigningMissing)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

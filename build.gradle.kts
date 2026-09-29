// AGP 9 tem Kotlin integrado (built-in Kotlin): não se aplica org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

fun KotlinNativeTarget.configureNWHelpersCinterop() {
    compilations["main"].cinterops {
        create("NWHelpers") {
            defFile("src/nativeInterop/cinterop/NWHelpers.def")
            includeDirs("src/nativeInterop/cinterop")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.configureNWHelpersCinterop()
        iosTarget.binaries.framework {
            baseName = "Libgemini"
            isStatic = true
        }
    }

    android {
        namespace = "ios.silv.gembrowse.libgemini"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            // Add KMP dependencies here
            implementation(project(":logcat"))

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network.tls)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.androidx.datastore.core)
            implementation(libs.androidx.datastore)

            implementation("com.ditchoom:buffer:6.30.8")
            implementation("com.ditchoom:buffer-flow:6.30.8")
            implementation("com.ditchoom:buffer-kotlinx-io:6.30.8")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


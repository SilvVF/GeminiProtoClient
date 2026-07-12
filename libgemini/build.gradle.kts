import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "ios.silv.gembrowse.shared"
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
            implementation(libs.compose.uiToolingPreview)
            implementation("io.ktor:ktor-client-android:${libs.versions.ktor.get()}")
        }
        commonMain.dependencies {
            implementation(project(":libgemini"))
            implementation(project(":logcat"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.androidx.datastore.core)
            implementation(libs.androidx.datastore)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)

            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.reorderable)
            implementation(libs.material.icons.extended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    kspAndroid(libs.androidx.room3.compiler)
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
}
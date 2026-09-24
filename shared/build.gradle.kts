plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(project(":source-api"))
            implementation(libs.coil.compose)
            implementation(libs.coil.networkOkhttp)

            // Extensions: DI they expect, APK reading, dex -> jar conversion
            implementation(libs.injekt.core)
            implementation(libs.apk.parser)
            implementation(libs.dex2jar.translator)
            implementation(libs.kotlinx.serializationProtobuf)

            // Libraries the Android app normally provides to extensions at runtime
            implementation(libs.kotlinx.serializationJsonOkio)
            implementation(libs.okhttpBrotli)
            implementation(libs.okhttpZstd)
            implementation(libs.orgJson)

        }
    }
}
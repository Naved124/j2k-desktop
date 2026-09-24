import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

// Release version: ./gradlew ... -PappVersion=1.2.0 (the GitHub release workflow passes the tag)
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.0.1"

compose.desktop {
    application {
        mainClass = "dev.naved.j2kdesktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            // Linux: .deb (and createDistributable = the app folder with its ELF launcher, used by the PKGBUILD)
            // Windows: .msi / .exe installers (built on Windows, see .github/workflows/release.yml)
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "j2k-desktop"
            packageVersion = appVersion
            description = "TachiyomiJ2K-style manga reader for desktop"
            vendor = "Naved"
            // Extensions are loaded at runtime and may use any part of Java: ship the whole runtime
            includeAllModules = true

            linux {
                iconFile.set(project.file("icons/icon.png"))
                shortcut = true
                menuGroup = "Graphics"
                appCategory = "Graphics"
                debMaintainer = "naved@users.noreply.github.com"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "J2K Desktop"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "3f1c6a52-8f2e-4d6b-9b1e-6c2a7d9e4b11"
            }
        }
    }
}

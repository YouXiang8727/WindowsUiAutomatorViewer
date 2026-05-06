import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
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
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.youxiang8727.windowsuiautomatorviewer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "WindowsUiAutomatorViewer"
            packageVersion = "1.0.0"
        }
    }
}

// Custom task to create the ZIP distribution required by the release workflow
tasks.register<Zip>("packageZip") {
    group = "compose desktop"
    description = "Packages the application as a ZIP file."
    val createDistributable = tasks.named("createDistributable")
    dependsOn(createDistributable)
    from(createDistributable) {
        into("WindowsUiAutomatorViewer")
    }
    archiveFileName.set("WindowsUiAutomatorViewer-1.0.0.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/zip"))
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

group = "dev.logcatgui"
version = "0.2.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.serialization.json)
    implementation(libs.r8)
    implementation(libs.jna)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// Renders key UI states offscreen to build/ui-snapshots/*.png (no device or window needed).
tasks.register<JavaExec>("uiSnapshots") {
    group = "verification"
    description = "Renders key UI states (popups, dialogs, window) to build/ui-snapshots."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.logcatgui.snapshots.UiSnapshotsKt")
    args(layout.buildDirectory.dir("ui-snapshots").get().asFile.absolutePath)
}

// Version info shown in the About dialog.
tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "kotlinVersion" to libs.versions.kotlin.get(),
        "composeVersion" to libs.versions.compose.get(),
    )
    inputs.properties(props)
    filesMatching("app-info.properties") { expand(props) }
}

compose.desktop {
    application {
        mainClass = "dev.logcatgui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            // From `./gradlew suggestRuntimeModules` (R8, JNA and serialization need these).
            modules("java.instrument", "java.sql", "jdk.management", "jdk.unsupported")
            packageName = "LogcatGUI"
            packageVersion = "1.0.0"
            description = "A standalone Logcat viewer inspired by Android Studio's Logcat panel."
            vendor = "Logcat-GUI Authors"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                menuGroup = "Logcat GUI"
                // Stable, randomly-generated GUID so MSI upgrades replace in place.
                upgradeUuid = "7E8B0E2C-4C2B-4F3A-9F1E-2A6B9C0D1E2F"
            }
        }
    }
}

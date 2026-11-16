import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
}

// The Android target needs an Android SDK on the machine. Without one (no
// ANDROID_HOME and no local.properties sdk.dir) we build desktop only, so the
// project still runs on a machine that only has a JDK.
val androidSdkPresent: Boolean =
    run {
        val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val localProps = rootProject.file("local.properties")
        val fromProps =
            if (localProps.exists()) {
                localProps.readLines().firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
            } else {
                null
            }
        (fromEnv ?: fromProps)?.let { file(it).isDirectory } ?: false
    }

if (androidSdkPresent) {
    apply(
        plugin =
            libs.plugins.androidApplication
                .get()
                .pluginId,
    )
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    if (androidSdkPresent) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        }
    }

    sourceSets {
        val desktopMain by getting
        // Code shared by every JVM-based target (desktop + Android): zip, zstd, java.io.
        val jvmShared by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.zstd.jni) }
        }
        desktopMain.dependsOn(jvmShared)
        if (androidSdkPresent) androidMain.get().dependsOn(jvmShared)

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.markdown.renderer)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        if (androidSdkPresent) {
            androidMain.dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.sqldelight.android.driver)
            }
        }
    }
}

if (androidSdkPresent) {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        namespace = "app.mnemo"
        compileSdk = 36
        defaultConfig {
            applicationId = "app.mnemo"
            minSdk = 26
            targetSdk = 36
            versionCode = 1
            versionName = "0.1.0"
        }
        packaging {
            resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        }
        buildTypes {
            getByName("release") { isMinifyEnabled = false }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}

sqldelight {
    databases {
        create("MnemoDatabase") {
            packageName.set("app.mnemo.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.mnemo.MainKt"
        // Run with the toolchain JDK (full AWT) rather than whatever `java` is on the host,
        // which may be a headless build.
        javaHome =
            javaToolchains
                .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
                .get()
                .metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mnemo"
            packageVersion = "0.1.0"
        }
    }
}

ktlint {
    android.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

// Tests write media/prefs into a scratch folder, never the user's data dir.
tasks.withType<Test>().configureEach {
    systemProperty(
        "mnemo.dataDir",
        layout.buildDirectory
            .dir("test-data")
            .get()
            .asFile.absolutePath,
    )
}

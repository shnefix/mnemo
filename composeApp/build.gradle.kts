import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    jvm("desktop")

    if (androidSdkPresent) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        }
    }

    sourceSets {
        val desktopMain by getting
        // Code shared by every JVM-based target (desktop + Android): zip, zstd, java.io.
        val jvmSharedMain by getting {
            dependencies { implementation(libs.zstd.jni) }
        }

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
            implementation(libs.markdown.renderer.code)
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
                implementation("androidx.profileinstaller:profileinstaller:1.4.1")
                // Account + sync (Google sign-in, Firestore).
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
                implementation(libs.kotlinx.coroutines.play.services)
                implementation(libs.androidx.credentials)
                implementation(libs.androidx.credentials.play)
                implementation(libs.googleid)
            }
            // On-device tests (`./gradlew :composeApp:connectedDebugAndroidTest` with a phone attached).
            val androidInstrumentedTest by getting {
                dependencies {
                    implementation(libs.kotlin.test)
                    implementation(libs.kotlinx.coroutines.test)
                    implementation(libs.androidx.test.runner)
                    implementation(libs.androidx.test.junit)
                    implementation(libs.androidx.compose.ui.test.junit4)
                }
            }
        }
    }
}

/** Commits on the current branch (1 in a shallow CI checkout); the Android version code. */
val gitCommitCount: Int =
    runCatching {
        providers
            .exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText
            .get()
            .trim()
            .toInt()
    }.getOrDefault(1)

if (androidSdkPresent) {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        namespace = "app.mnemo"
        compileSdk = 37
        defaultConfig {
            applicationId = "app.mnemo"
            minSdk = 26
            targetSdk = 36
            // One step per commit, so every build you install is newer than the last and the
            // installed build can be told apart (`adb shell dumpsys package app.mnemo`).
            versionCode = gitCommitCount
            versionName = "0.1.$gitCommitCount"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            manifestPlaceholders["appLabel"] = "Mnemo"
        }
        buildFeatures { resValues = true }
        packaging {
            resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        }
        // Release key: `keystore.properties` at the repo root (gitignored) points at the
        // keystore. Without it (CI, fresh clone), or with `-Pmnemo.signing=debug`, release uses
        // the debug key, which updates a phone whose copy was installed with that key.
        val forceDebugKey = providers.gradleProperty("mnemo.signing").orNull == "debug"
        val keystoreProps =
            rootProject.file("keystore.properties").takeIf { it.exists() && !forceDebugKey }?.let { f ->
                Properties().also { p -> f.inputStream().use { p.load(it) } }
            }
        signingConfigs {
            if (keystoreProps != null) {
                create("release") {
                    storeFile = file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
        buildTypes {
            // The debug build is what the on-device tests install (`make phone-test`). Its own
            // application id puts it next to the real app instead of over it, so it gets its own
            // database and prefs and the tests can never touch the user's decks.
            getByName("debug") {
                applicationIdSuffix = ".debug"
                manifestPlaceholders["appLabel"] = "Mnemo (test)"
            }
            getByName("release") {
                // Firebase settings from google-services.json (gitignored), read here instead of via
                // the google-services plugin. Without the file the app builds with sync turned off.
                // Release only: the debug build is the on-device test install, which must never
                // sign in to the real account and push its test decks over the synced collection.
                file("google-services.json").takeIf { it.exists() }?.let { f ->
                    @Suppress("UNCHECKED_CAST")
                    val root = groovy.json.JsonSlurper().parse(f) as Map<String, Any?>
                    val info = root["project_info"] as Map<String, Any?>
                    val client = (root["client"] as List<Map<String, Any?>>).first()
                    val appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String
                    val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String
                    val webClient =
                        (client["oauth_client"] as? List<Map<String, Any?>>).orEmpty().firstOrNull { it["client_type"] == 3 }?.get(
                            "client_id",
                        ) as String?
                    resValue("string", "google_app_id", appId)
                    resValue("string", "google_api_key", apiKey)
                    resValue("string", "project_id", info["project_id"] as String)
                    resValue("string", "gcm_defaultSenderId", info["project_number"] as String)
                    (info["storage_bucket"] as String?)?.let { resValue("string", "google_storage_bucket", it) }
                    webClient?.let { resValue("string", "default_web_client_id", it) }
                }
                // R8 + resource shrinking: a debug build of Compose runs several times slower.
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            }
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
            // Schema snapshots live next to the .sq files; every schema change needs a
            // migration (`<version>.sqm`) and a new snapshot (`generateCommonMainMnemoDatabaseSchema`).
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
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
            linux { iconFile.set(project.file("src/desktopMain/resources/icon.png")) }
            windows { iconFile.set(project.file("src/desktopMain/resources/icon.png")) }
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

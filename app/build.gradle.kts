plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Runs a git command, returning null rather than failing outside a checkout. */
fun git(vararg args: String): String? = providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { null }

/**
 * Version name from the release tag, e.g. "v1.2.0" becomes "1.2.0".
 *
 * Read from the environment first so a CI job can state it outright; otherwise from
 * the newest tag reachable from HEAD. A checkout without tags — which is what a
 * shallow clone gives you — falls back to a marker that is obviously not a release.
 */
val appVersionName: String =
    System.getenv("VERSION_NAME")
        ?: git("describe", "--tags", "--abbrev=0")?.removePrefix("v")
        ?: "0.0.0-dev"

/**
 * Version code from the number of commits.
 *
 * Android refuses an update whose code is not higher than the installed one, and
 * bumping it by hand is the step everyone forgets. Commit count only ever grows on
 * a branch that moves forward, so it cannot silently go backwards.
 */
val appVersionCode: Int =
    System.getenv("VERSION_CODE")?.toIntOrNull()
        ?: git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: 1

android {
    namespace = "agency.dynamicdata.steps"
    compileSdk = 36

    defaultConfig {
        applicationId = "agency.dynamicdata.steps"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    /**
     * Release signing, supplied by the environment.
     *
     * The keystore never lives in the repository: CI writes it to a temporary file
     * from a secret. Without those variables the config is simply absent and a local
     * release build comes out unsigned, which is what you want on a dev machine.
     */
    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                // Both schemes: v1 for Android 8, v2 for everything since.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")

            // Off deliberately. R8 strips anything it cannot see being used, and the
            // reflective entry points here — the Glance receiver, the WorkManager
            // worker — are exactly what it gets wrong. The rules in
            // proguard-rules.pro are meant to cover that, but no minified build has
            // been run on a device yet, and a broken widget arriving through an
            // automatic update is worse than a larger download. Turn this on once a
            // release build has been installed and checked.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // The loader logs through android.util.Log, which is a stub in a JVM test.
            // Returning defaults lets it run without pulling in Robolectric.
            isReturnDefaultValues = true
        }
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Health Connect: the on-device store every step provider writes into.
    implementation(libs.androidx.health.connect.client)

    // Glance: Compose-style authoring for the home-screen widget.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager: refreshes the widget on a schedule the system can batch.
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore: holds the step goal, readable from both the app and the widget.
    implementation(libs.androidx.datastore.preferences)

    // The in-app screen that explains the widget and requests permission.
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

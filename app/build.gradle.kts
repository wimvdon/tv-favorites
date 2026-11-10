import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// App identity: the ONE place to change the package / application ID.
// (Also move the Kotlin sources in app/src/*/kotlin/ to the matching folder.)
// ---------------------------------------------------------------------------
val appId = "com.example.template"

// ---------------------------------------------------------------------------
// Versioning
//  - Release builds: the release workflow passes -PappVersionName=X.Y.Z and
//    -PappVersionCode=N (N = major*10000 + minor*100 + patch).
//  - Every other build: version comes from `git describe --tags --long`, e.g.
//    1.2.0-3-gabc1234, so a dev build never looks like a release.
//    Falls back to 0.0.0-dev when there are no tags or no git.
// ---------------------------------------------------------------------------
val releaseVersionName: String? = providers.gradleProperty("appVersionName").orNull
val releaseVersionCode: String? = providers.gradleProperty("appVersionCode").orNull

fun gitDescribeVersion(): String {
    val fallback = "0.0.0-dev"
    return try {
        val result = providers.exec {
            commandLine("git", "describe", "--tags", "--long", "--dirty", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue != 0) {
            fallback
        } else {
            result.standardOutput.asText.get().trim().removePrefix("v").ifEmpty { fallback }
        }
    } catch (e: Exception) {
        // git is not installed or this is not a git checkout (e.g. a downloaded source zip).
        fallback
    }
}

data class AppVersion(val name: String, val code: Int)

val appVersion: AppVersion = if (releaseVersionName != null || releaseVersionCode != null) {
    require(releaseVersionName != null && releaseVersionCode != null) {
        "Pass both -PappVersionName and -PappVersionCode, or neither."
    }
    require(Regex("""\d+\.\d+\.\d+""").matches(releaseVersionName)) {
        "appVersionName must look like X.Y.Z, got '$releaseVersionName'."
    }
    val code = releaseVersionCode.toIntOrNull()?.takeIf { it > 0 }
        ?: error("appVersionCode must be a positive number, got '$releaseVersionCode'.")
    AppVersion(releaseVersionName, code)
} else {
    AppVersion(gitDescribeVersion(), 1)
}

// ---------------------------------------------------------------------------
// Release signing: only when keystore.properties exists. The release workflow
// writes it at build time and deletes it afterwards. Never commit it.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = appId
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        // Android 12. See docs/decisions.md for why.
        minSdk = 31
        targetSdk = 37
        versionCode = appVersion.code
        versionName = appVersion.name
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a pull request's debug APK be installed next to the released app.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Builds the per-app language list from the values-xx folders (see res/resources.properties).
        generateLocaleConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // Do not disable checks or add a baseline without recording why in docs/decisions.md.
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// Used by the workflows to name the APK <app>-<version>.apk: ./gradlew -q :app:printApkBaseName
tasks.register("printApkBaseName") {
    val baseName = "${rootProject.name}-${appVersion.name}"
    doLast { println(baseName) }
}

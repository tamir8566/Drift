plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Version. CI passes -PversionCode=<github run number>; locally it defaults to 1.
// versionName is derived so the two never drift apart.
// ---------------------------------------------------------------------------
val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName: String = (project.findProperty("versionName") as String?) ?: "1.0.$appVersionCode"

// ---------------------------------------------------------------------------
// Release signing from environment variables (CI decodes the keystore secret to a
// temp file and exports DRIFT_KEYSTORE_PATH). When any of them is missing the
// release build falls back to the debug key so local builds still work.
// ---------------------------------------------------------------------------
val keystorePath: String? = System.getenv("DRIFT_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val keystorePassword: String? = System.getenv("DRIFT_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val keyAliasEnv: String? = System.getenv("DRIFT_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPasswordEnv: String? = System.getenv("DRIFT_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseKeystore =
    keystorePath != null && keystorePassword != null && keyAliasEnv != null && keyPasswordEnv != null &&
        file(keystorePath).exists()

android {
    namespace = "com.tamir.drift"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tamir.drift"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://uabeicwtealqawiwejul.supabase.co/storage/v1/object/public/drift-releases/manifest.json\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // Same applicationId as release so a debug install is replaced by the
            // signed release from the self-updater (same package name is required).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

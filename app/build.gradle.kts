plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun propertyOrEnv(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val releaseStoreFile = propertyOrEnv("RELEASE_STORE_FILE")
val releaseStorePassword = propertyOrEnv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = propertyOrEnv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = propertyOrEnv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "me.kafuuneko.rpclient"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // 定制分支使用独立 applicationId，与官方版互不覆盖，可同时安装。
        applicationId = "me.kafuuneko.rpclient.uv"
        minSdk = 26
        targetSdk = 36

        // Single source of truth: gradle.properties -> versionCode,
        // versionName and BuildConfig.UV_VERSION all derive from it.
        // A hard-coded versionCode never increases, so Android treats every
        // build as the same version and updating in place fails.
        val uvVersion = providers.gradleProperty("uv.version").getOrElse("0.0")
        val uvNumeric = uvVersion.filter { it.isDigit() }.toIntOrNull() ?: 0
        versionCode = 20260301 + uvNumeric
        versionName = "2026.3.1-uv" + uvVersion
        buildConfigField("String", "UV_VERSION", "\"" + uvVersion + "\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // A pinned debug keystore. The auto-generated one lives in
            // ~/.android and gets rebuilt whenever the toolchain is reset;
            // a changed signature means the new APK cannot overwrite the old
            // one, so the only way forward is uninstall - and that wipes saves.
            // Debug keys are public knowledge, so keeping it in the repo is fine.
            val uvDebugStore = file("uv-debug.jks")
            if (uvDebugStore.exists()) {
                signingConfig = signingConfigs.create("uvDebug") {
                    storeFile = uvDebugStore
                    storePassword = "uvdebug"
                    keyAlias = "uvdebug"
                    keyPassword = "uvdebug"
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // UV_VERSION reaches Kotlin through BuildConfig so the About page
        // and the package metadata can never drift apart
        buildConfig = true
    }
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // kotpref
    implementation(libs.kotpref)

    // koin
    implementation(libs.koin.android)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    // okhttp
    implementation(libs.okhttp)

    // gson
    implementation(libs.gson)

    // model-aware local tokenization
    implementation(libs.jtokkit)

}

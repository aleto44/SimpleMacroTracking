import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

// Load signing credentials from local.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.simplemacrotracking"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alexg.simplemacrotracking"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Prefer local.properties; fall back to environment variables (used in CI/GitHub Actions)
            storeFile = file(
                localProps.getProperty("RELEASE_STORE_FILE")
                    ?: System.getenv("RELEASE_STORE_FILE")
                    ?: "release.keystore"
            )
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                ?: System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        checkDependencies = false
        ignoreWarnings = true
        disable += "NullSafeMutableLiveData"
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Room: export schema JSON so future migrations can be auto-generated
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

// Neuter the lintVital* tasks entirely.
// AGP registers lintVitalAnalyzeRelease as a Gradle *finalizer* task, which means
// neither `-x lintVitalAnalyzeRelease` nor `enabled = false` can prevent it from
// running.  The only reliable workaround is to clear its action list so it executes
// as a no-op and always succeeds.
// Root cause: IncompatibleClassChangeError in NonNullableMutableLiveDataDetector
// (androidx.lifecycle lint) caused by KaCallableMemberCall changing from a class to
// an interface in the Kotlin 2.x Analysis API.
tasks.configureEach {
    if (name.startsWith("lintVital")) {
        @Suppress("UNCHECKED_CAST")
        (this as org.gradle.api.internal.TaskInternal).let { t ->
            // Replace every action with a single no-op so the task completes successfully
            // without invoking AndroidLintWorkAction.
            t.setActions(listOf(Action<Task> {
                logger.lifecycle("Skipping $name: disabled due to lifecycle-lint / Kotlin 2.x incompatibility")
            }))
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ZXing (barcode)
    implementation(libs.zxing.android.embedded)
    // MPAndroidChart
    implementation(libs.mpandroidchart)
    // Jetpack Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)
    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
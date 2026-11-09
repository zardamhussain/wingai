import com.android.build.api.variant.ApplicationVariant

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.2.21"
    kotlin("plugin.compose") version "2.2.21"
    id("com.google.gms.google-services") version "4.5.0"
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wingai.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 3816
        versionName = "3.8-wingai"
        buildConfigField("String", "DEFAULT_BACKEND_URL", "\"https://wingai-backend.zardamhussain.workers.dev\"")
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    signingConfigs {
        create("releaseConfig") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && File(ksPath).exists()) {
                storeFile = File(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
            val ksPath = System.getenv("KEYSTORE_PATH")
            signingConfig = if (!ksPath.isNullOrBlank() && File(ksPath).exists()) {
                signingConfigs.getByName("releaseConfig")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("nouserlib") { // same as release, but does not allow the user to provide a library
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            isMinifyEnabled = false
            isJniDebuggable = false
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && File(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("releaseConfig")
            }
        }
        create("runTests") { // build variant for running tests on CI that skips tests known to fail
            isMinifyEnabled = false
            isJniDebuggable = false
        }
        create("debugNoMinify") { // for faster builds in IDE
            isDebuggable = true
            isMinifyEnabled = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        base.archivesBaseName = "WingAI-Board-" + defaultConfig.versionName
        // got a little too big for GitHub after some dependency upgrades, so we remove the largest dictionary
        androidComponents.onVariants { variant: ApplicationVariant ->
            if (variant.buildType == "debug") {
                variant.androidResources.ignoreAssetsPatterns = listOf("main_ro.dict")
                variant.proguardFiles = emptyList()
                //noinspection ProguardAndroidTxtUsage we intentionally use the "normal" file here
                variant.proguardFiles.add(project.layout.buildDirectory.file(getDefaultProguardFile("proguard-android.txt").absolutePath))
                variant.proguardFiles.add(project.layout.buildDirectory.file(project.buildFile.parent + "/proguard-rules.pro"))
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    // GNU Make cannot build under a path containing spaces. The optional local helper
    // builds these same JNI sources through a temporary path alias, never substitutes a library.
    val wingaiNativeLibs = providers.gradleProperty("wingaiNativeLibs").orNull
    if (wingaiNativeLibs == null) {
        externalNativeBuild {
            ndkBuild {
                path = File("src/main/jni/Android.mk")
            }
        }
    } else {
        require(File(wingaiNativeLibs, "arm64-v8a/libjni_latinime.so").isFile) {
            "Build the current JNI sources with tools/build-login-apk.sh first."
        }
        sourceSets.getByName("main").jniLibs.srcDir(wingaiNativeLibs)
    }
    ndkVersion = "27.1.12297006"

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "keyboard",
                "ui",
                "capture",
                "ai",
                "privacy",
                "settings",
                "data"
            )
        }
    }

    packaging {
        jniLibs {
            // shrinks APK by 3 MB, zipped size unchanged
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    // see https://github.com/Helium314/HeliBoard/issues/477
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "helium314.keyboard.latin"
    lint {
        abortOnError = true
    }
}

dependencies {
    // Optional account identity only: existing keyboard, Worker plans and quotas stay unchanged.
    // BoM 35 requires Kotlin 2.4 metadata; keep the existing Kotlin 2.2 toolchain intact.
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // Firebase Auth brings an old Fragment transitively; preserve ActivityResult/permission compatibility.
    implementation("androidx.fragment:fragment:1.8.9")
    // Explicit Google account chooser; Firebase still validates the returned ID token.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Bundled on-device OCR for Select & Search so raw screenshots never leave the device.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // androidx
    implementation("androidx.core:core-ktx:1.16.0") // 1.17 requires SDK 36
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // newer than 2025.11.01 contains androidx.compose.material:material-android:1.10.0, which requires minSdk 23
    // maybe it's possible to use tools:overrideLibrary="androidx.compose.material" as it's not used explicitly, but probably this is just going to crash
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("sh.calvin.reorderable:reorderable:2.4.3") // for easier re-ordering, todo: check 3.0.0
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:runner:1.6.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

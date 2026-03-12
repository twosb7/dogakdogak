import com.android.build.api.variant.ApplicationVariant
import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun readSecret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

fun requireSecret(name: String): String =
    readSecret(name) ?: error("$name not set in environment variables or local.properties")

val requestedTasks = gradle.startParameter.taskNames

fun isReleaseLikeTask(taskName: String): Boolean {
    val normalized = taskName.substringAfterLast(':')
    return normalized.equals("assemble", ignoreCase = true)
        || normalized.equals("build", ignoreCase = true)
        || normalized.equals("bundle", ignoreCase = true)
        || normalized.equals("package", ignoreCase = true)
        || normalized.equals("publish", ignoreCase = true)
        || normalized.contains("release", ignoreCase = true)
        || normalized.contains("nouserlib", ignoreCase = true)
}

val requiresProtectedSecrets = requestedTasks.any(::isReleaseLikeTask)

fun buildSecret(name: String): String =
    if (requiresProtectedSecrets) requireSecret(name) else readSecret(name).orEmpty()

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.2.21"
    kotlin("plugin.compose") version "2.2.21"
}

android {
    val releaseStorePassword = if (requiresProtectedSecrets) requireSecret("RELEASE_STORE_PASSWORD")
        else readSecret("RELEASE_STORE_PASSWORD")
    val releaseKeyPassword = if (requiresProtectedSecrets) requireSecret("RELEASE_KEY_PASSWORD")
        else readSecret("RELEASE_KEY_PASSWORD")
    val hasReleaseSigningSecrets = releaseStorePassword != null && releaseKeyPassword != null

    compileSdk = 35

    signingConfigs {
        if (hasReleaseSigningSecrets) {
            create("release") {
                storeFile = rootProject.file(readSecret("RELEASE_STORE_FILE") ?: "dogakdogak-release.jks")
                storePassword = releaseStorePassword
                keyAlias = "dogakdogak"
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.dogakdogak.keyboard"
        minSdk = 21
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.1"

        buildConfigField("String", "SUPABASE_URL", "\"${buildSecret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${buildSecret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${buildSecret("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("boolean", "ALLOW_USER_GESTURE_LIB_LOADING", "false")
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            if (hasReleaseSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("nouserlib") { // same as release, but does not allow the user to provide a library
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            // "normal" debug has minify for smaller APK to fit the GitHub 25 MB limit when zipped
            // and for better performance in case users want to install a debug APK
            isMinifyEnabled = true
            isJniDebuggable = false
            buildConfigField("boolean", "ALLOW_USER_GESTURE_LIB_LOADING", "true")
            if (hasReleaseSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
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
            buildConfigField("boolean", "ALLOW_USER_GESTURE_LIB_LOADING", "true")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        base.archivesBaseName = "Dogakdogak_" + defaultConfig.versionName
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

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }
    ndkVersion = "28.0.13004108"

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
        isCoreLibraryDesugaringEnabled = true
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
    // androidx
    implementation("androidx.core:core-ktx:1.16.0") // 1.17 requires SDK 36
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // Ktor (HTTP client for Supabase)
    implementation("io.ktor:ktor-client-okhttp:2.3.12")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Coil (아바타 이미지 로딩)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ExifInterface (이미지 회전 보정)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Chrome Custom Tabs (OAuth 브라우저)
    implementation("androidx.browser:browser:1.8.0")

    // WorkManager (백그라운드 동기화)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Konfetti (파티클 이펙트)
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // newer than 2025.11.01 contains androidx.compose.material:material-android:1.10.0, which requires minSdk 23
    // maybe it's possible to use tools:overrideLibrary="androidx.compose.material" as it's not used explicitly, but probably this is just going to crash
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("sh.calvin.reorderable:reorderable:2.4.3") // for easier re-ordering, todo: check 3.0.0
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:runner:1.6.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

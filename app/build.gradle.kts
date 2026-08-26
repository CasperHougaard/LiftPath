plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

// Load keystore properties from file if it exists, or from environment variables
val keystoreProperties = mutableMapOf<String, String>()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.readLines().forEach { line ->
        val trimmedLine = line.trim()
        if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
            val (key, value) = trimmedLine.split("=", limit = 2)
            keystoreProperties[key.trim()] = value.trim()
        }
    }
}

android {
    namespace = "com.liftpath"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.liftpath"
        minSdk = 35
        targetSdk = 36
        versionCode = 15
        versionName = "4.00.005"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = keystoreProperties["storeFile"] ?: "liftpath-release-key.jks"
            val keystorePassword = keystoreProperties["storePassword"] ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            val keyAlias = keystoreProperties["keyAlias"] ?: System.getenv("KEY_ALIAS") ?: ""
            val keyPassword = keystoreProperties["keyPassword"] ?: System.getenv("KEY_PASSWORD") ?: ""
            
            if (keystorePassword.isEmpty() || keyAlias.isEmpty() || keyPassword.isEmpty()) {
                throw GradleException(
                    "Release signing REQUIRED for Google Play Console.\n" +
                    "Please provide keystore credentials by either:\n" +
                    "  1. Creating a 'keystore.properties' file in the project root with:\n" +
                    "     storeFile=liftpath-release-key.jks\n" +
                    "     storePassword=your_keystore_password\n" +
                    "     keyAlias=your_key_alias\n" +
                    "     keyPassword=your_key_password\n" +
                    "  2. Or set environment variables: KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD\n" +
                    "See BUILD_AAB_GUIDE.md for detailed instructions."
                )
            }
            
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.startup.runtime)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // ProcessLifecycleOwner, for refreshing the zero-login cloud snapshot on backgrounding.
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // Spring physics for the home-screen entrance + press feedback. ~30KB; the
    // alternative is hand-tuned interpolators that never quite settle right.
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    // Android 12+ SplashScreen API compat — lets us hook a custom exit animation on the
    // system-drawn splash instead of accepting its default fade.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Backup & sync: SAF folder access, background scheduling
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Google Drive backup destination: Play Services' Authorization API only, no Drive client
    // library — DriveBackupHelper talks to the REST API directly.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // Connect IQ companion SDK — the transport behind WatchLink, for live logging from the
    // Fenix. On Maven Central since 2.x, so no `.aar` in app/libs/ and no flatDir repository
    // (settings.gradle sets FAIL_ON_PROJECT_REPOS, which would reject one).
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
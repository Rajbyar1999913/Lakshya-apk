plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)

    // Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.app"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.app"

        minSdk = 26
        targetSdk = 36

        // IMPORTANT:
        // Increase versionCode for every new release.
        versionCode = 11
        versionName = "1.10"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    /*
     * ============================================================
     * SIGNING CONFIGURATION
     * ============================================================
     *
     * Local Android Studio build:
     * - If lakshya.keystore exists, release uses it.
     * - Otherwise release falls back to the normal debug key.
     *
     * GitHub Actions:
     * - The workflow will create lakshya.keystore from
     *   LAKSHYA_KEYSTORE_BASE64.
     * - Passwords are taken from GitHub Secrets.
     */

    signingConfigs {
        create("lakshyaRelease") {

            val keystoreFile = file("lakshya.keystore")

            if (keystoreFile.exists()) {
                storeFile = keystoreFile

                storePassword =
                    System.getenv("LAKSHYA_KEYSTORE_PASSWORD") ?: ""

                keyAlias = "androiddebugkey"

                keyPassword =
                    System.getenv("LAKSHYA_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {

        release {

            isMinifyEnabled = false

            /*
             * If lakshya.keystore exists:
             *     use Lakshya signing configuration.
             *
             * If it does not exist:
             *     use normal Android debug signing.
             *
             * This keeps your local Android Studio build working.
             */
            signingConfig =
                if (file("lakshya.keystore").exists()) {
                    signingConfigs.getByName("lakshyaRelease")
                } else {
                    signingConfigs.getByName("debug")
                }

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {

        resources {

            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"

            excludes +=
                "META-INF/DEPENDENCIES"

            excludes +=
                "META-INF/LICENSE"

            excludes +=
                "META-INF/LICENSE.txt"

            excludes +=
                "META-INF/NOTICE"

            excludes +=
                "META-INF/NOTICE.txt"

            excludes +=
                "META-INF/INDEX.LIST"
        }
    }
}

dependencies {

    // ============================================================
    // FIREBASE BACKEND
    // ============================================================

    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.16.0"
        )
    )

    // Firebase Authentication
    implementation(
        "com.google.firebase:firebase-auth"
    )

    // Cloud Firestore Database
    implementation(
        "com.google.firebase:firebase-firestore"
    )

    // Firebase Cloud Messaging
    implementation(
        "com.google.firebase:firebase-messaging"
    )


    // ============================================================
    // JETPACK COMPOSE
    // ============================================================

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )


    // ============================================================
    // ROOM DATABASE
    // ============================================================

    implementation(
        libs.androidx.room.runtime
    )

    implementation(
        libs.androidx.room.ktx
    )

    ksp(
        libs.androidx.room.compiler
    )


    // ============================================================
    // EXCEL XLSX EXPORT
    // APACHE POI
    // ============================================================

    implementation(
        "org.apache.poi:poi:5.4.1"
    )

    implementation(
        "org.apache.poi:poi-ooxml:5.4.1"
    )


    // ============================================================
    // TEST
    // ============================================================

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}
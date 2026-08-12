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

            // Increment this for every release. Firebase uses it to require updates.
            versionCode = 7
            versionName = "1.6"

            testInstrumentationRunner =
                "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {

            release {

                isMinifyEnabled = false

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

        // =========================================
        // FIREBASE BACKEND
        // =========================================

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

        // Push notification when a compulsory app update is released.
        implementation(
            "com.google.firebase:firebase-messaging"
        )


        // =========================================
        // JETPACK COMPOSE
        // =========================================

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


        // =========================================
        // ROOM DATABASE
        // =========================================

        implementation(
            libs.androidx.room.runtime
        )

        implementation(
            libs.androidx.room.ktx
        )

        ksp(
            libs.androidx.room.compiler
        )


        // =========================================
        // EXCEL XLSX EXPORT
        // APACHE POI
        // =========================================

        implementation(
            "org.apache.poi:poi:5.4.1"
        )

        implementation(
            "org.apache.poi:poi-ooxml:5.4.1"
        )


        // =========================================
        // TEST
        // =========================================

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

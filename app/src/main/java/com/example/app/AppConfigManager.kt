package com.example.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class AppConfig(

    val latestVersionCode: Long = 1L,
    val minimumVersionCode: Long = 1L,

    val forceUpdate: Boolean = false,

    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String =
        "Lakshya is currently under maintenance. Please try again later.",

    val updateMessage: String =
        "A new version of Lakshya is available.",

    // Public HTTPS URL on the official distribution site for the latest APK.
    // This must be a direct-download endpoint, not a Google Drive sharing link.
    // It is kept in Firestore so every installed app sees the latest release.
    val updateUrl: String = "",

    val appEnabled: Boolean = true,
    val loginEnabled: Boolean = true,
    val printEnabled: Boolean = true
)


object AppConfigManager {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }


    // =====================================================
    // FIRESTORE LOCATION
    //
    // app_config
    //      └── main
    // =====================================================

    private fun configDocument() =
        firestore
            .collection("app_config")
            .document("main")


    // =====================================================
    // ONE-TIME CONFIG LOAD
    // Existing functions ke liye rakha gaya hai.
    // =====================================================

    fun getAppConfig(

        onSuccess: (AppConfig) -> Unit,

        onError: (String) -> Unit

    ) {

        configDocument()
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    onError(
                        "App configuration not found"
                    )

                    return@addOnSuccessListener
                }


                try {

                    val config =
                        document.toObject(
                            AppConfig::class.java
                        )


                    if (config == null) {

                        onError(
                            "Invalid app configuration"
                        )

                        return@addOnSuccessListener
                    }


                    onSuccess(config)

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "App configuration load failed"
                    )
                }
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "App configuration load failed"
                )
            }
    }


    // =====================================================
    // REAL-TIME APP CONFIG LISTENER
    //
    // Firebase me config change hote hi running app ko
    // automatically update milega.
    // =====================================================

    fun listenAppConfig(

        onUpdate: (AppConfig) -> Unit,

        onError: (String) -> Unit

    ): ListenerRegistration {

        return configDocument()
            .addSnapshotListener { document, error ->

                if (error != null) {

                    onError(
                        error.message
                            ?: "App configuration sync failed"
                    )

                    return@addSnapshotListener
                }


                if (
                    document == null ||
                    !document.exists()
                ) {

                    onError(
                        "App configuration not found"
                    )

                    return@addSnapshotListener
                }


                try {

                    val config =
                        document.toObject(
                            AppConfig::class.java
                        )


                    if (config == null) {

                        onError(
                            "Invalid app configuration"
                        )

                        return@addSnapshotListener
                    }


                    onUpdate(config)

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "App configuration sync failed"
                    )
                }
            }
    }


    // =====================================================
    // CREATE DEFAULT CONFIG
    // =====================================================

    fun createDefaultConfig(

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        val document =
            configDocument()


        document
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.exists()) {

                    onSuccess()

                    return@addOnSuccessListener
                }


                document
                    .set(AppConfig())
                    .addOnSuccessListener {

                        onSuccess()
                    }

                    .addOnFailureListener { error ->

                        onError(
                            error.message
                                ?: "Default configuration creation failed"
                        )
                    }
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Configuration check failed"
                )
            }
    }


    // =====================================================
    // UPDATE COMPLETE CONFIG
    // =====================================================

    fun updateAppConfig(

        config: AppConfig,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        configDocument()
            .set(config)
            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "App configuration update failed"
                )
            }
    }


    // =====================================================
    // MAINTENANCE MODE
    // =====================================================

    fun setMaintenanceMode(

        enabled: Boolean,

        message: String? = null,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        val updates =
            mutableMapOf<String, Any>(
                "maintenanceMode" to enabled
            )


        if (!message.isNullOrBlank()) {

            updates["maintenanceMessage"] =
                message.trim()
        }


        configDocument()
            .update(updates)
            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Maintenance mode update failed"
                )
            }
    }


    // =====================================================
    // FORCE UPDATE
    // =====================================================

    fun setForceUpdate(

        enabled: Boolean,

        latestVersionCode: Long,

        minimumVersionCode: Long,

        message: String,

        downloadUrl: String = "",

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        val updates =
            hashMapOf<String, Any>(

                "forceUpdate" to enabled,

                "latestVersionCode" to
                        latestVersionCode,

                "minimumVersionCode" to
                        minimumVersionCode,

                "updateMessage" to
                        message.trim(),

                "updateUrl" to
                        downloadUrl.trim()
            )


        configDocument()
            .update(updates)
            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Update configuration failed"
                )
            }
    }


    // =====================================================
    // ENABLE / DISABLE COMPLETE APP
    // =====================================================

    fun setAppEnabled(

        enabled: Boolean,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        configDocument()
            .update(
                "appEnabled",
                enabled
            )

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "App status update failed"
                )
            }
    }


    // =====================================================
    // ENABLE / DISABLE LOGIN
    // =====================================================

    fun setLoginEnabled(

        enabled: Boolean,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        configDocument()
            .update(
                "loginEnabled",
                enabled
            )

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Login status update failed"
                )
            }
    }


    // =====================================================
    // ENABLE / DISABLE PRINT
    // =====================================================

    fun setPrintEnabled(

        enabled: Boolean,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        configDocument()
            .update(
                "printEnabled",
                enabled
            )

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Print status update failed"
                )
            }
    }


    // =====================================================
    // CHECK MANDATORY UPDATE
    // =====================================================

    fun isUpdateRequired(

        currentVersionCode: Long,

        config: AppConfig

    ): Boolean {

        return currentVersionCode <
                config.minimumVersionCode
    }


    // =====================================================
    // CHECK NEW VERSION AVAILABLE
    // =====================================================

    fun isNewVersionAvailable(

        currentVersionCode: Long,

        config: AppConfig

    ): Boolean {

        return currentVersionCode <
                config.latestVersionCode
    }
}

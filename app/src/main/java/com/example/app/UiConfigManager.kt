package com.example.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class UiConfig(

    // APP / DASHBOARD
    val appTitle: String = "Lakshya",
    val dashboardTitle: String = "Today's Dashboard",
    val announcementText: String = "",
    val showAnnouncement: Boolean = false,

    // THEME
    val primaryColor: String = "#1565C0",
    val secondaryColor: String = "#1976D2",
    val buttonColor: String = "#1565C0",

    // BUTTON TEXT
    val newEntryButtonText: String = "NEW ENTRY",
    val historyButtonText: String = "HISTORY",
    val resultButtonText: String = "RESULT",
    val backupButtonText: String = "OLD DAY / BACKUP",

    // SHOW / HIDE
    val showNewEntry: Boolean = true,
    val showHistory: Boolean = true,
    val showResult: Boolean = true,
    val showBackup: Boolean = true,

    // ENABLE / DISABLE
    val enableNewEntry: Boolean = true,
    val enableHistory: Boolean = true,
    val enableResult: Boolean = true,
    val enableBackup: Boolean = true,

    // FUTURE USE
    val configVersion: Long = 1L
)


object UiConfigManager {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    // Firestore:
    //
    // ui_config
    //      └── main

    private fun configDocument() =
        firestore
            .collection("ui_config")
            .document("main")


    // =============================================
    // REAL-TIME LISTENER
    // =============================================

    fun listenUiConfig(
        onUpdate: (UiConfig) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return configDocument()
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    onError(
                        error.message
                            ?: "UI configuration sync failed"
                    )

                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {

                    // Config na mile to app crash/block nahi hoga.
                    // Default UI chalega.
                    onUpdate(UiConfig())

                    return@addSnapshotListener
                }

                try {

                    val config =
                        snapshot.toObject(
                            UiConfig::class.java
                        )

                    onUpdate(
                        config ?: UiConfig()
                    )

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "Invalid UI configuration"
                    )
                }
            }
    }


    // =============================================
    // ONE-TIME LOAD
    // =============================================

    fun getUiConfig(
        onSuccess: (UiConfig) -> Unit,
        onError: (String) -> Unit
    ) {

        configDocument()
            .get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {

                    onSuccess(UiConfig())

                    return@addOnSuccessListener
                }

                val config =
                    snapshot.toObject(
                        UiConfig::class.java
                    )

                onSuccess(
                    config ?: UiConfig()
                )
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "UI configuration load failed"
                )
            }
    }


    // =============================================
    // CREATE DEFAULT CONFIG
    // =============================================

    fun createDefaultConfig(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        configDocument()
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.exists()) {

                    onSuccess()

                    return@addOnSuccessListener
                }

                configDocument()
                    .set(UiConfig())
                    .addOnSuccessListener {

                        onSuccess()
                    }
                    .addOnFailureListener { error ->

                        onError(
                            error.message
                                ?: "UI configuration creation failed"
                        )
                    }
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "UI configuration check failed"
                )
            }
    }
}
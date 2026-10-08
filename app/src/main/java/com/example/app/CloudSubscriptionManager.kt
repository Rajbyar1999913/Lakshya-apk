package com.example.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class CloudSubscriptionData(

    val startDate: Long = 0L,

    val expiryDate: Long = 0L,

    val employeeLimit: Int = 5,

    val monthlyPrice: Int = 10000,

    val isActive: Boolean = false,

    val updatedAt: Long = 0L

) {

    fun isCurrentlyActive(): Boolean {

        return isActive &&
                expiryDate > System.currentTimeMillis()
    }
}


object CloudSubscriptionManager {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }


    // =====================================================
    // SUBSCRIPTION DOCUMENT
    // masters/{masterUid}/subscription/current
    // =====================================================

    private fun subscriptionDocument(masterUid: String) =

        firestore
            .collection("masters")
            .document(masterUid)
            .collection("subscription")
            .document("current")

    private fun readSubscription(
        document: com.google.firebase.firestore.DocumentSnapshot
    ): CloudSubscriptionData = CloudSubscriptionData(
        startDate = document.getLong("startDate") ?: 0L,
        expiryDate = document.getLong("expiryDate") ?: 0L,
        employeeLimit = (document.getLong("employeeLimit") ?: 5L).toInt(),
        // Keep plan pricing derived from the limit for legacy accounts too.
        monthlyPrice = monthlyPlanPriceFor(
            (document.getLong("employeeLimit") ?: 5L).toInt()
        ),
        isActive = document.getBoolean("isActive") ?: false,
        updatedAt = document.getLong("updatedAt") ?: 0L
    )

    /** Keeps an already-open app in sync with backend plan changes. */
    fun listenSubscription(
        masterUid: String,
        onSuccess: (CloudSubscriptionData) -> Unit,
        onError: (String) -> Unit = {}
    ): ListenerRegistration? {
        if (masterUid.isBlank()) return null

        return subscriptionDocument(masterUid)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    onError(error.message ?: "Subscription sync failed")
                    return@addSnapshotListener
                }
                if (document == null || !document.exists()) {
                    onError("Subscription not found")
                    return@addSnapshotListener
                }
                onSuccess(readSubscription(document))
            }
    }


    // =====================================================
    // GET SUBSCRIPTION
    // =====================================================

    fun getSubscription(

        masterUid: String,

        onSuccess: (CloudSubscriptionData) -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError("Master UID missing")

            return
        }


        subscriptionDocument(masterUid)

            .get()

            .addOnSuccessListener { document ->


                // -----------------------------------------
                // DOCUMENT NOT FOUND
                // -----------------------------------------

                if (!document.exists()) {

                    onError("Subscription not found")

                    return@addOnSuccessListener
                }


                // -----------------------------------------
                // IMPORTANT
                //
                // Firestore fields direct read kar rahe hain.
                // toObject() use nahi karenge.
                // -----------------------------------------

                try {
                    onSuccess(readSubscription(document))

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "Invalid subscription data"
                    )
                }
            }

            .addOnFailureListener { error ->

                onError(

                    error.message
                        ?: "Subscription check failed"
                )
            }
    }


    // =====================================================
    // CREATE DEFAULT SUBSCRIPTION
    //
    // New Master account ke liye subscription document
    // create hoga.
    //
    // Payment connected nahi hai isliye default INACTIVE.
    // =====================================================

    fun createDefaultSubscription(

        masterUid: String,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError("Master UID missing")

            return
        }


        val document =
            subscriptionDocument(masterUid)


        document
            .get()

            .addOnSuccessListener { snapshot ->


                // Subscription already exists
                if (snapshot.exists()) {

                    onSuccess()

                    return@addOnSuccessListener
                }


                // -----------------------------------------
                // DEFAULT SUBSCRIPTION
                // -----------------------------------------

                val data =
                    CloudSubscriptionData(

                        startDate = 0L,

                        expiryDate = 0L,

                        employeeLimit = 5,

                        monthlyPrice = 10000,

                        isActive = false,

                        updatedAt =
                            System.currentTimeMillis()
                    )


                document

                    .set(data)

                    .addOnSuccessListener {

                        onSuccess()
                    }

                    .addOnFailureListener { error ->

                        onError(

                            error.message
                                ?: "Subscription creation failed"
                        )
                    }
            }

            .addOnFailureListener { error ->

                onError(

                    error.message
                        ?: "Subscription check failed"
                )
            }
    }


    // =====================================================
    // UPDATE SUBSCRIPTION
    //
    // NOTE:
    // Current Firestore Rules client subscription writes
    // block karte hain.
    //
    // Future me payment/backend verification ke baad
    // subscription update backend se hoga.
    // =====================================================

    fun updateSubscription(

        masterUid: String,

        data: CloudSubscriptionData,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError("Master UID missing")

            return
        }


        val updatedData =
            data.copy(

                updatedAt =
                    System.currentTimeMillis()
            )


        subscriptionDocument(masterUid)

            .set(updatedData)

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(

                    error.message
                        ?: "Subscription update failed"
                )
            }
    }
}

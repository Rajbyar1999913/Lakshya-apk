package com.example.app

import com.google.firebase.firestore.FirebaseFirestore

data class CloudSubscriptionData(

    val startDate: Long = 0L,

    val expiryDate: Long = 0L,

    val employeeLimit: Int = 5,

    val monthlyPrice: Int = 5000,

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

                    val startDate =
                        document.getLong("startDate")
                            ?: 0L


                    val expiryDate =
                        document.getLong("expiryDate")
                            ?: 0L


                    val employeeLimit =
                        (
                                document.getLong("employeeLimit")
                                    ?: 5L
                                ).toInt()


                    val monthlyPrice =
                        (
                                document.getLong("monthlyPrice")
                                    ?: 5000L
                                ).toInt()


                    val isActive =
                        document.getBoolean("isActive")
                            ?: false


                    val updatedAt =
                        document.getLong("updatedAt")
                            ?: 0L


                    val data =
                        CloudSubscriptionData(

                            startDate = startDate,

                            expiryDate = expiryDate,

                            employeeLimit = employeeLimit,

                            monthlyPrice = monthlyPrice,

                            isActive = isActive,

                            updatedAt = updatedAt
                        )


                    onSuccess(data)

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

                        monthlyPrice = 5000,

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
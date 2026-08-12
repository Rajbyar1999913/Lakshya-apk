package com.example.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

// =====================================================
// CLOUD BILL ENTRY
// =====================================================

data class CloudNumberAmountEntry(
    val number: String = "",
    val amount: Int = 0,
    val entryType: String = "",
    val actualAmount: Double = amount.toDouble()
)


// =====================================================
// CLOUD BILL
// =====================================================

data class CloudBill(

    // Room/local bill ID
    val localBillId: Int = 0,

    // Firebase document ID
    val cloudBillId: String = "",

    // Owner
    val masterUid: String = "",

    // Bill data
    val customerName: String = "",
    val games: List<String> = emptyList(),

    val entries: List<CloudNumberAmountEntry> =
        emptyList(),

    val perGameTotal: Int = 0,
    val grandTotal: Int = 0,

    // Original save time from device
    val savedTime: Long = 0L,

    // ACTIVE / CANCELLED
    val status: String = "ACTIVE",

    // Audit
    val createdBy: String = "",
    val createdAt: Long = 0L,

    val cancelledBy: String = "",
    val cancelledTime: Long? = null,

    val isEdited: Boolean = false,
    val lastEditedBy: String = "",
    val lastEditedTime: Long? = null,

    val isPrinted: Boolean = false,
    val printedBy: String = "",
    val printedTime: Long? = null,
    val printCount: Int = 0,

    val isDayLocked: Boolean = false,
    val dayLockedBy: String = "",
    val dayLockedTime: Long? = null,

    // Cloud bookkeeping
    val updatedAt: Long = 0L
)


// =====================================================
// CLOUD BILL MANAGER
// =====================================================

object CloudBillManager {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }


    // =====================================================
    // CURRENT FIREBASE USER
    // =====================================================

    private fun currentFirebaseUid(): String? {
        return auth.currentUser?.uid
    }


    // =====================================================
    // MASTER BILL COLLECTION
    //
    // masters
    //   └── MASTER_UID
    //        └── bills
    //             └── BILL_DOCUMENT
    // =====================================================

    private fun billCollection(
        masterUid: String
    ) =
        firestore
            .collection("masters")
            .document(masterUid)
            .collection("bills")


    // =====================================================
    // CREATE CLOUD DOCUMENT ID
    //
    // Example:
    // BILL_25_1780000000000
    //
    // Local ID + original save time use karne se same
    // local bill ko identify karna easy rahega.
    // =====================================================

    fun createCloudBillId(
        localBillId: Int,
        savedTime: Long
    ): String {

        return "BILL_${localBillId}_${savedTime}"
    }


    // =====================================================
    // CONVERT SAVED ENTRY -> CLOUD BILL
    // =====================================================

    private fun toCloudBill(

        entry: SavedEntry,

        masterUid: String,

        cloudBillId: String

    ): CloudBill {

        return CloudBill(

            localBillId = entry.id,

            cloudBillId = cloudBillId,

            masterUid = masterUid,

            customerName =
                entry.customerName.trim(),

            games =
                entry.games,

            entries =
                entry.entries.map {

                    CloudNumberAmountEntry(
                        number = it.number,
                        amount = it.amount,
                        entryType = it.entryType,
                        actualAmount = it.actualAmount
                    )
                },

            perGameTotal =
                entry.perGameTotal,

            grandTotal =
                entry.grandTotal,

            savedTime =
                entry.savedTime,

            status =
                entry.status,

            createdBy =
                entry.createdBy,

            createdAt =
                entry.savedTime,

            cancelledBy =
                entry.cancelledBy,

            cancelledTime =
                entry.cancelledTime,

            isEdited =
                entry.isEdited,

            lastEditedBy =
                entry.lastEditedBy,

            lastEditedTime =
                entry.lastEditedTime,

            isPrinted =
                entry.isPrinted,

            printedBy =
                entry.printedBy,

            printedTime =
                entry.printedTime,

            printCount = entry.printCount,

            isDayLocked =
                entry.isDayLocked,

            dayLockedBy =
                entry.dayLockedBy,

            dayLockedTime =
                entry.dayLockedTime,

            updatedAt =
                System.currentTimeMillis()
        )
    }


    // =====================================================
    // SAVE NEW BILL TO CLOUD
    // =====================================================

    fun saveBill(

        entry: SavedEntry,

        masterUid: String,

        onSuccess: (String) -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        if (entry.id <= 0) {

            onError(
                "Local Bill ID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId = entry.id,
                savedTime = entry.savedTime
            )


        val cloudBill =
            toCloudBill(
                entry = entry,
                masterUid = masterUid,
                cloudBillId = cloudBillId
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .set(cloudBill)

            .addOnSuccessListener {

                onSuccess(
                    cloudBillId
                )
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud bill save failed"
                )
            }
    }


    // =====================================================
    // UPDATE COMPLETE BILL
    //
    // Edit ke baad isi function ko use karenge.
    // =====================================================

    fun updateBill(

        entry: SavedEntry,

        masterUid: String,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        if (entry.id <= 0) {

            onError(
                "Local Bill ID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId = entry.id,
                savedTime = entry.savedTime
            )


        val cloudBill =
            toCloudBill(
                entry = entry,
                masterUid = masterUid,
                cloudBillId = cloudBillId
            )


        billCollection(masterUid)
            .document(cloudBillId)

            .set(
                cloudBill,
                SetOptions.merge()
            )

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud bill update failed"
                )
            }
    }


    // =====================================================
    // MARK BILL CANCELLED
    // =====================================================

    fun cancelBill(

        masterUid: String,

        localBillId: Int,

        savedTime: Long,

        cancelledBy: String,

        cancelledTime: Long =
            System.currentTimeMillis(),

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId,
                savedTime
            )


        val updates =
            hashMapOf<String, Any>(

                "status" to
                        "CANCELLED",

                "cancelledBy" to
                        cancelledBy,

                "cancelledTime" to
                        cancelledTime,

                "updatedAt" to
                        System.currentTimeMillis()
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .update(updates)

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud bill cancellation failed"
                )
            }
    }


    // =====================================================
    // MARK BILL PRINTED
    // =====================================================

    fun markBillPrinted(

        masterUid: String,

        localBillId: Int,

        savedTime: Long,

        printedBy: String,

        printedTime: Long =
            System.currentTimeMillis(),

        printCount: Int,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId,
                savedTime
            )


        val updates =
            hashMapOf<String, Any>(

                "isPrinted" to
                        true,

                "printedBy" to
                        printedBy,

                "printedTime" to
                        printedTime,

                "printCount" to printCount,

                "updatedAt" to
                        System.currentTimeMillis()
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .update(updates)

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud print status update failed"
                )
            }
    }


    // =====================================================
    // MARK DAY LOCKED
    // =====================================================

    fun lockBill(

        masterUid: String,

        localBillId: Int,

        savedTime: Long,

        lockedBy: String,

        lockedTime: Long =
            System.currentTimeMillis(),

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId,
                savedTime
            )


        val updates =
            hashMapOf<String, Any>(

                "dayLocked" to
                        true,

                "dayLockedBy" to
                        lockedBy,

                "dayLockedTime" to
                        lockedTime,

                "updatedAt" to
                        System.currentTimeMillis()
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .update(updates)

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud day lock failed"
                )
            }
    }


    // =====================================================
    // GET ALL CLOUD BILLS
    // =====================================================

    fun getBills(

        masterUid: String,

        onSuccess: (List<CloudBill>) -> Unit,

        onError: (String) -> Unit

    ): com.google.firebase.firestore.ListenerRegistration? {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return null
        }


        // REAL-TIME MASTER-WISE BILL SYNC
        //
        // This listens only to:
        // masters/{masterUid}/bills
        //
        // If the same Master ID is open on another phone,
        // new/edited/cancelled bills are pushed to this listener.
        return billCollection(masterUid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    onError(
                        error.message
                            ?: "Cloud bills sync failed"
                    )

                    return@addSnapshotListener
                }


                if (snapshot == null) {

                    onSuccess(
                        emptyList()
                    )

                    return@addSnapshotListener
                }


                val bills =
                    snapshot.documents
                        .mapNotNull { document ->

                            try {

                                document
                                    .toObject(
                                        CloudBill::class.java
                                    )
                                    ?.copy(
                                        // Older app versions saved this as
                                        // "printed". Read both fields so a
                                        // cloud refresh cannot remove a
                                        // legitimate print/chukara status.
                                        isPrinted =
                                            document.getBoolean("isPrinted")
                                                ?: document.getBoolean("printed")
                                                ?: false,
                                        cloudBillId =
                                            document.id
                                    )

                            } catch (_: Exception) {

                                null
                            }
                        }

                        .filter {
                            it.masterUid.isBlank() ||
                                    it.masterUid == masterUid
                        }

                        .sortedByDescending {
                            it.savedTime
                        }


                onSuccess(
                    bills
                )
            }
    }


    // =====================================================
    // GET SINGLE BILL
    // =====================================================

    fun getBill(

        masterUid: String,

        localBillId: Int,

        savedTime: Long,

        onSuccess: (CloudBill?) -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId,
                savedTime
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .get()

            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    onSuccess(null)

                    return@addOnSuccessListener
                }


                try {

                    val bill =
                        document
                            .toObject(
                                CloudBill::class.java
                            )
                            ?.copy(
                                cloudBillId =
                                    document.id
                            )


                    onSuccess(
                        bill
                    )

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "Cloud bill read failed"
                    )
                }
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud bill read failed"
                )
            }
    }


    // =====================================================
    // DELETE CLOUD BILL
    //
    // Normally business bill DELETE nahi karenge.
    // CANCEL status use karenge.
    //
    // Ye helper sirf development/testing ke liye hai.
    // =====================================================

    fun deleteBill(

        masterUid: String,

        localBillId: Int,

        savedTime: Long,

        onSuccess: () -> Unit,

        onError: (String) -> Unit

    ) {

        if (masterUid.isBlank()) {

            onError(
                "Master UID missing"
            )

            return
        }


        val cloudBillId =
            createCloudBillId(
                localBillId,
                savedTime
            )


        billCollection(masterUid)
            .document(cloudBillId)
            .delete()

            .addOnSuccessListener {

                onSuccess()
            }

            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Cloud bill delete failed"
                )
            }
    }


    // =====================================================
    // FIREBASE LOGIN UID HELPER
    // =====================================================

    fun loggedInFirebaseUid(): String? {

        return currentFirebaseUid()
    }
}




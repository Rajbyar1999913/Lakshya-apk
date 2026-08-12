package com.example.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest

data class CloudDayState(
    val currentDayStart: Long = 0L,
    val lastClosedDayStart: Long = 0L,
    val lastClosedDayEnd: Long = 0L,
    val updatedAt: Long = 0L
)

data class CloudPaidItem(
    val paymentKey: String = "",
    val paidBy: String = "",
    val paidTime: Long = 0L,
    val amount: Int = 0
)

data class CloudResultHistoryItem(
    val game: String = "",
    val result: String = "",
    val savedTime: Long = 0L
)

object CloudAccountSyncManager {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private fun masterRef(masterUid: String) =
        firestore.collection("masters").document(masterUid.trim())

    private fun stateRef(masterUid: String) =
        masterRef(masterUid).collection("account_state").document("current")

    private fun liveResultsRef(masterUid: String) =
        masterRef(masterUid).collection("live_state").document("results")

    private fun paidCollection(masterUid: String) =
        masterRef(masterUid).collection("chukara_paid")

    fun listenDayState(
        masterUid: String,
        onUpdate: (CloudDayState) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = masterUid.trim()
        if (uid.isBlank()) return null

        return stateRef(uid).addSnapshotListener { document, error ->
            if (error != null) {
                onError(error.message ?: "Day state sync failed")
                return@addSnapshotListener
            }

            if (document == null || !document.exists()) {
                return@addSnapshotListener
            }

            onUpdate(
                CloudDayState(
                    currentDayStart = document.getLong("currentDayStart") ?: 0L,
                    lastClosedDayStart = document.getLong("lastClosedDayStart") ?: 0L,
                    lastClosedDayEnd = document.getLong("lastClosedDayEnd") ?: 0L,
                    updatedAt = document.getLong("updatedAt") ?: 0L
                )
            )
        }
    }

    fun closeDay(
        masterUid: String,
        dayStart: Long,
        dayEnd: Long,
        nextDayStart: Long,
        closedBy: String,
        liveResults: Map<String, String>,
        liveResultTimes: Map<String, Long>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = masterUid.trim()
        if (uid.isBlank()) {
            onError("Master UID missing")
            return
        }

        val archiveId = dayEnd.toString()
        val now = System.currentTimeMillis()

        val archiveRef =
            masterRef(uid)
                .collection("day_closings")
                .document(archiveId)

        val archiveData = hashMapOf<String, Any>(
            "masterUid" to uid,
            "dayStart" to dayStart,
            "dayEnd" to dayEnd,
            "nextDayStart" to nextDayStart,
            "closedBy" to closedBy,
            "closedAt" to dayEnd,
            "liveResults" to liveResults,
            "liveResultTimes" to liveResultTimes,
            "updatedAt" to now
        )

        val stateData = hashMapOf<String, Any>(
            "currentDayStart" to nextDayStart,
            "lastClosedDayStart" to dayStart,
            "lastClosedDayEnd" to dayEnd,
            "lastClosedArchiveId" to archiveId,
            "updatedAt" to now
        )

        val clearedLive = hashMapOf<String, Any>(
            "results" to emptyMap<String, String>(),
            "times" to emptyMap<String, Long>(),
            "updatedAt" to now
        )

        firestore.runBatch { batch ->
            batch.set(archiveRef, archiveData, SetOptions.merge())
            batch.set(stateRef(uid), stateData, SetOptions.merge())
            batch.set(liveResultsRef(uid), clearedLive)
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                onError(it.message ?: "Day close cloud sync failed")
            }
    }

    fun undoLastDay(
        masterUid: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = masterUid.trim()
        if (uid.isBlank()) {
            onError("Master UID missing")
            return
        }

        stateRef(uid).get()
            .addOnSuccessListener { state ->
                val archiveId =
                    state.getString("lastClosedArchiveId").orEmpty()

                val lastStart =
                    state.getLong("lastClosedDayStart") ?: 0L

                if (archiveId.isBlank() || lastStart <= 0L) {
                    onError("No cloud closed day found")
                    return@addOnSuccessListener
                }

                val archiveRef =
                    masterRef(uid)
                        .collection("day_closings")
                        .document(archiveId)

                archiveRef.get()
                    .addOnSuccessListener { archive ->
                        val rawResults =
                            archive.get("liveResults") as? Map<*, *>

                        val rawTimes =
                            archive.get("liveResultTimes") as? Map<*, *>

                        val restoredResults =
                            rawResults.orEmpty()
                                .mapNotNull { (k, v) ->
                                    val key = k?.toString().orEmpty()
                                    val value = v?.toString().orEmpty()
                                    if (key.isBlank()) null else key to value
                                }.toMap()

                        val restoredTimes =
                            rawTimes.orEmpty()
                                .mapNotNull { (k, v) ->
                                    val key = k?.toString().orEmpty()
                                    val value = (v as? Number)?.toLong()
                                    if (key.isBlank() || value == null) null
                                    else key to value
                                }.toMap()

                        val now = System.currentTimeMillis()

                        val stateData = hashMapOf<String, Any>(
                            "currentDayStart" to lastStart,
                            "lastClosedDayStart" to 0L,
                            "lastClosedDayEnd" to 0L,
                            "lastClosedArchiveId" to "",
                            "updatedAt" to now
                        )

                        val liveData = hashMapOf<String, Any>(
                            "results" to restoredResults,
                            "times" to restoredTimes,
                            "updatedAt" to now
                        )

                        firestore.runBatch { batch ->
                            batch.set(stateRef(uid), stateData, SetOptions.merge())
                            batch.set(liveResultsRef(uid), liveData)
                        }
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener {
                                onError(it.message ?: "Day undo cloud sync failed")
                            }
                    }
                    .addOnFailureListener {
                        onError(it.message ?: "Closed day archive load failed")
                    }
            }
            .addOnFailureListener {
                onError(it.message ?: "Day state load failed")
            }
    }

    fun saveLiveResult(
        masterUid: String,
        game: String,
        result: String,
        savedTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = masterUid.trim()
        val cleanGame = game.trim().uppercase()

        if (uid.isBlank() || cleanGame.isBlank()) {
            onError("Master UID / game missing")
            return
        }

        val data = mapOf(
            "results" to mapOf(cleanGame to result.trim()),
            "times" to mapOf(cleanGame to savedTime),
            "updatedAt" to System.currentTimeMillis()
        )

        liveResultsRef(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                onError(it.message ?: "Live result sync failed")
            }
    }

    fun deleteLiveResult(
        masterUid: String,
        game: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (masterUid.isBlank()) return
        liveResultsRef(masterUid)
            .update(
                mapOf(
                    "results.$game" to com.google.firebase.firestore.FieldValue.delete(),
                    "times.$game" to com.google.firebase.firestore.FieldValue.delete()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Live result delete failed") }
    }

    fun listenLiveResults(
        masterUid: String,
        onUpdate: (Map<String, String>, Map<String, Long>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = masterUid.trim()
        if (uid.isBlank()) return null

        return liveResultsRef(uid)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    onError(error.message ?: "Live result sync failed")
                    return@addSnapshotListener
                }

                if (document == null || !document.exists()) {
                    onUpdate(emptyMap(), emptyMap())
                    return@addSnapshotListener
                }

                val rawResults =
                    document.get("results") as? Map<*, *>

                val rawTimes =
                    document.get("times") as? Map<*, *>

                val results =
                    rawResults.orEmpty()
                        .mapNotNull { (k, v) ->
                            val key = k?.toString()?.trim()?.uppercase().orEmpty()
                            val value = v?.toString().orEmpty()
                            if (key.isBlank()) null else key to value
                        }.toMap()

                val times =
                    rawTimes.orEmpty()
                        .mapNotNull { (k, v) ->
                            val key = k?.toString()?.trim()?.uppercase().orEmpty()
                            val value = (v as? Number)?.toLong()
                            if (key.isBlank() || value == null) null
                            else key to value
                        }.toMap()

                onUpdate(results, times)
            }
    }

    fun listenResultHistory(
        masterUid: String,
        onUpdate: (Map<String, List<CloudResultHistoryItem>>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = masterUid.trim()
        if (uid.isBlank()) return null

        return masterRef(uid)
            .collection("results")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Result history sync failed")
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onUpdate(emptyMap())
                    return@addSnapshotListener
                }

                val items =
                    snapshot.documents.mapNotNull { document ->
                        val game =
                            document.getString("game")
                                .orEmpty()
                                .trim()
                                .uppercase()

                        val result =
                            document.getString("result")
                                .orEmpty()
                                .trim()

                        val savedTime =
                            document.getLong("savedTime") ?: 0L

                        if (game.isBlank() || result.isBlank() || savedTime <= 0L) {
                            null
                        } else {
                            CloudResultHistoryItem(
                                game = game,
                                result = result,
                                savedTime = savedTime
                            )
                        }
                    }

                onUpdate(
                    items.groupBy { it.game }
                )
            }
    }

    fun markChukaraPaid(
        masterUid: String,
        paymentKey: String,
        paidBy: String,
        paidTime: Long,
        amount: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = masterUid.trim()
        if (uid.isBlank() || paymentKey.isBlank()) return

        val documentId = sha256(paymentKey)

        val data = hashMapOf<String, Any>(
            "masterUid" to uid,
            "paymentKey" to paymentKey,
            "paid" to true,
            "paidBy" to paidBy,
            "paidTime" to paidTime,
            "amount" to amount,
            "updatedAt" to System.currentTimeMillis()
        )

        paidCollection(uid)
            .document(documentId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                onError(it.message ?: "Chukara paid sync failed")
            }
    }

    fun listenChukaraPaid(
        masterUid: String,
        onUpdate: (List<CloudPaidItem>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = masterUid.trim()
        if (uid.isBlank()) return null

        return paidCollection(uid)
            .whereEqualTo("paid", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Chukara paid sync failed")
                    return@addSnapshotListener
                }

                val items =
                    snapshot?.documents.orEmpty()
                        .mapNotNull { document ->
                            val paymentKey =
                                document.getString("paymentKey").orEmpty()

                            if (paymentKey.isBlank()) {
                                null
                            } else {
                                CloudPaidItem(
                                    paymentKey = paymentKey,
                                    paidBy = document.getString("paidBy").orEmpty(),
                                    paidTime = document.getLong("paidTime") ?: 0L,
                                    amount = (document.getLong("amount") ?: 0L).toInt()
                                )
                            }
                        }

                onUpdate(items)
            }
    }

    private fun sha256(value: String): String {
        val bytes =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }
}

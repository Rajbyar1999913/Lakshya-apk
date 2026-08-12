package com.example.app

import com.google.firebase.firestore.FirebaseFirestore

/** Cloud state that must survive reinstall / second-device login. */
object CloudBackendManager {
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun master(masterUid: String) = db.collection("masters").document(masterUid)

    fun saveSubscription(masterUid: String, data: SubscriptionData, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (masterUid.isBlank()) { onError("Master UID missing"); return }
        master(masterUid).collection("system").document("subscription")
            .set(mapOf("startDate" to data.startDate, "expiryDate" to data.expiryDate,
                "employeeLimit" to data.employeeLimit, "monthlyPrice" to data.monthlyPrice,
                "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Subscription sync failed") }
    }

    fun loadSubscription(masterUid: String, onSuccess: (SubscriptionData?) -> Unit, onError: (String) -> Unit) {
        if (masterUid.isBlank()) { onError("Master UID missing"); return }
        master(masterUid).collection("system").document("subscription").get()
            .addOnSuccessListener { d ->
                if (!d.exists()) onSuccess(null) else onSuccess(SubscriptionData(
                    startDate = d.getLong("startDate") ?: 0L,
                    expiryDate = d.getLong("expiryDate") ?: 0L,
                    employeeLimit = (d.getLong("employeeLimit") ?: 5L).toInt(),
                    monthlyPrice = (d.getLong("monthlyPrice") ?: 5000L).toInt()))
            }.addOnFailureListener { onError(it.message ?: "Subscription load failed") }
    }

    fun savePermission(masterUid: String, employeeUid: String, featureKey: String, allowed: Boolean,
                       onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (masterUid.isBlank() || employeeUid.isBlank()) { onError("Employee cloud identity missing"); return }
        master(masterUid).collection("employees").document(employeeUid)
            .collection("permissions").document(featureKey)
            .set(mapOf("featureKey" to featureKey, "isAllowed" to allowed, "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Permission sync failed") }
    }

    fun saveResult(masterUid: String, game: String, result: String, savedBy: String, savedTime: Long = System.currentTimeMillis(),
                   onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (masterUid.isBlank()) { onError("Master UID missing"); return }
        val cleanGame = game.trim().uppercase()
        val payload = mapOf("game" to cleanGame, "result" to result.trim(), "savedBy" to savedBy,
            "savedTime" to savedTime, "updatedAt" to System.currentTimeMillis())
        master(masterUid).collection("results").document(cleanGame).set(payload)
            .addOnSuccessListener {
                master(masterUid).collection("result_history").document("${cleanGame}_$savedTime").set(payload)
                onSuccess()
            }.addOnFailureListener { onError(it.message ?: "Result sync failed") }
    }

    fun lockDay(masterUid: String, bills: List<SavedEntry>, lockedBy: String, lockedTime: Long,
                onComplete: (Int, Int) -> Unit) {
        if (masterUid.isBlank()) { onComplete(0, bills.size); return }
        if (bills.isEmpty()) { onComplete(0, 0); return }
        var ok = 0; var done = 0
        bills.forEach { bill ->
            CloudBillManager.lockBill(masterUid, bill.id, bill.savedTime, lockedBy, lockedTime,
                onSuccess = { ok++; done++; if (done == bills.size) onComplete(ok, bills.size-ok) },
                onError = { done++; if (done == bills.size) onComplete(ok, bills.size-ok) })
        }
    }

    fun unlockDay(masterUid: String, bills: List<SavedEntry>, onComplete: (Int, Int) -> Unit) {
        if (masterUid.isBlank()) { onComplete(0, bills.size); return }
        if (bills.isEmpty()) { onComplete(0, 0); return }
        var ok=0; var done=0
        bills.forEach { bill ->
            val id = CloudBillManager.createCloudBillId(bill.id, bill.savedTime)
            master(masterUid).collection("bills").document(id)
                .update(mapOf("dayLocked" to false, "dayLockedBy" to "", "dayLockedTime" to null,
                    "updatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener { ok++; done++; if(done==bills.size) onComplete(ok,bills.size-ok) }
                .addOnFailureListener { done++; if(done==bills.size) onComplete(ok,bills.size-ok) }
        }
    }
}

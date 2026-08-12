package com.example.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth

/**
 * Firebase-backed employee feature permissions.
 *
 * Firestore path:
 * masters/{masterUid}/employee_permissions/{EMPLOYEE_USER_ID}
 *
 * Real-time permission sync.
 */
object CloudPermissionManager {

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private fun permissionDocument(
        masterUid: String,
        employeeUserId: String
    ) = firestore
        .collection("masters")
        .document(masterUid.trim())
        .collection("employee_permissions")
        .document(employeeUserId.trim().uppercase())

    fun savePermissions(
        masterUid: String,
        employeeUserId: String,
        permissions: Map<String, Boolean>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val cleanMasterUid = masterUid.trim()
        val cleanUserId = employeeUserId.trim().uppercase()

        if (cleanMasterUid.isBlank()) {
            onError("Master UID missing")
            return
        }

        if (cleanUserId.isBlank()) {
            onError("Employee User ID missing")
            return
        }

        val cleanPermissions = permissions.mapKeys {
            it.key.trim().uppercase()
        }

        val data = hashMapOf<String, Any>(
            "masterUid" to cleanMasterUid,
            "employeeUserId" to cleanUserId,
            "permissions" to cleanPermissions,
            "updatedAt" to System.currentTimeMillis()
        )

        permissionDocument(
            cleanMasterUid,
            cleanUserId
        )
            .set(data)
            .addOnSuccessListener {
                // An employee is allowed to read only their own lookup document.
                // Keep a copy here as well, so permissions work on a different phone
                // without granting the employee broad access to the master's data.
                firestore
                    .collection("masters")
                    .document(cleanMasterUid)
                    .collection("employees")
                    .whereEqualTo("userId", cleanUserId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { employees ->
                        val employeeUid = employees.documents.firstOrNull()?.id

                        if (employeeUid.isNullOrBlank()) {
                            onError("Employee profile not found for permission sync")
                            return@addOnSuccessListener
                        }

                        firestore
                            .collection("employee_lookup")
                            .document(employeeUid)
                            .set(
                                mapOf(
                                    "permissions" to cleanPermissions,
                                    "permissionsUpdatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener {
                                onError(it.message ?: "Employee permission sync failed")
                            }
                    }
                    .addOnFailureListener {
                        onError(it.message ?: "Employee profile lookup failed")
                    }
            }
            .addOnFailureListener {
                onError(
                    it.message ?: "Permission cloud save failed"
                )
            }
    }

    fun getPermissions(
        masterUid: String,
        employeeUserId: String,
        onSuccess: (Map<String, Boolean>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {

        val cleanMasterUid = masterUid.trim()
        val cleanUserId = employeeUserId.trim().uppercase()

        if (cleanMasterUid.isBlank()) {
            onError("Master UID missing")
            return null
        }

        if (cleanUserId.isBlank()) {
            onError("Employee User ID missing")
            return null
        }

        return permissionDocument(
            cleanMasterUid,
            cleanUserId
        ).addSnapshotListener { document, error ->

            if (error != null) {
                onError(
                    error.message ?: "Permission cloud sync failed"
                )
                return@addSnapshotListener
            }

            if (document == null || !document.exists()) {
                onSuccess(emptyMap())
                return@addSnapshotListener
            }

            val raw =
                document.get("permissions")
                        as? Map<*, *>
                    ?: emptyMap<Any, Any>()

            val result =
                HashMap<String, Boolean>()

            raw.forEach { (key, value) ->

                val feature =
                    key?.toString()
                        ?.trim()
                        ?.uppercase()

                if (
                    feature != null &&
                    value is Boolean
                ) {
                    result[feature] = value
                }
            }

            onSuccess(result)
        }
    }

    /**
     * Employee-side listener. employee_lookup/{uid} is already used during
     * login and is readable only by that authenticated employee in Firestore
     * rules, unlike a master-owned subcollection on another device.
     */
    fun getPermissionsForSignedInEmployee(
        onSuccess: (Map<String, Boolean>) -> Unit,
        onError: (String) -> Unit,
        onAccessRevoked: () -> Unit
    ): ListenerRegistration? {
        val employeeUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        if (employeeUid.isBlank()) {
            onError("Employee login missing")
            return null
        }

        return firestore
            .collection("employee_lookup")
            .document(employeeUid)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    onError(error.message ?: "Employee permission sync failed")
                    return@addSnapshotListener
                }

                // The master updates this same document whenever an employee
                // is activated/deactivated.  A missing or inactive document
                // must revoke an already logged-in employee as well.
                val employeeIsActive =
                    document?.getBoolean("isActive")
                        ?: document?.getBoolean("active")
                        ?: false
                if (document == null || !document.exists() || !employeeIsActive) {
                    onAccessRevoked()
                    return@addSnapshotListener
                }

                val raw = document?.get("permissions") as? Map<*, *>
                    ?: emptyMap<Any, Any>()
                val result = HashMap<String, Boolean>()

                raw.forEach { (key, value) ->
                    val feature = key?.toString()?.trim()?.uppercase()
                    if (feature != null && value is Boolean) {
                        result[feature] = value
                    }
                }
                onSuccess(result)
            }
    }
}

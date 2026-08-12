package com.example.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object FirebaseManager {

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }


    // =====================================================
    // CURRENT MASTER
    // =====================================================

    fun currentMasterUid(): String? {
        return auth.currentUser?.uid
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logout() {
        auth.signOut()
    }


    // =====================================================
    // MASTER LOGIN
    // =====================================================

    fun loginMaster(
        email: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val cleanEmail =
            email.trim().lowercase()

        if (cleanEmail.isBlank()) {
            onError("Email required")
            return
        }

        if (password.isBlank()) {
            onError("Password required")
            return
        }

        auth.signInWithEmailAndPassword(
            cleanEmail,
            password
        )
            .addOnSuccessListener { result ->

                val uid =
                    result.user?.uid

                if (uid.isNullOrBlank()) {

                    auth.signOut()

                    onError(
                        "Firebase UID not found"
                    )

                    return@addOnSuccessListener
                }

                firestore
                    .collection("masters")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { document ->

                        if (!document.exists()) {

                            auth.signOut()

                            onError(
                                "Master Admin record not found"
                            )

                            return@addOnSuccessListener
                        }

                        val role =
                            document.getString("role")
                                ?: ""

                        val active =
                            document.getBoolean("isActive")
                                ?: false

                        if (role != "ADMIN") {

                            auth.signOut()

                            onError(
                                "This account is not a Master Admin"
                            )

                            return@addOnSuccessListener
                        }

                        if (!active) {

                            auth.signOut()

                            onError(
                                "Master Admin account is inactive"
                            )

                            return@addOnSuccessListener
                        }

                        onSuccess(uid)
                    }
                    .addOnFailureListener { error ->

                        auth.signOut()

                        onError(
                            error.message
                                ?: "Master verification failed"
                        )
                    }
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Login failed"
                )
            }
    }


    // =====================================================
    // EMPLOYEE COLLECTION
    // =====================================================

    private fun employeeCollection() =
        currentMasterUid()?.let { masterUid ->

            firestore
                .collection("masters")
                .document(masterUid)
                .collection("employees")
        }


    // =====================================================
    // SAVE AUTHENTICATED EMPLOYEE TO FIRESTORE
    // =====================================================

    fun saveEmployee(
        employeeUid: String,
        employeeName: String,
        userId: String,
        authEmail: String,
        onSuccess: (CloudEmployee) -> Unit,
        onError: (String) -> Unit
    ) {

        val masterUid =
            currentMasterUid()

        if (masterUid.isNullOrBlank()) {

            onError(
                "Master Admin login required"
            )

            return
        }

        val cleanName =
            employeeName.trim()

        val cleanUserId =
            userId.trim().uppercase()

        if (employeeUid.isBlank()) {

            onError(
                "Employee UID missing"
            )

            return
        }

        if (cleanName.isBlank()) {

            onError(
                "Employee name required"
            )

            return
        }

        if (cleanUserId.isBlank()) {

            onError(
                "Employee User ID required"
            )

            return
        }

        val collection =
            employeeCollection()

        if (collection == null) {

            onError(
                "Employee database unavailable"
            )

            return
        }

        collection
            .whereEqualTo(
                "userId",
                cleanUserId
            )
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                if (!result.isEmpty) {

                    onError(
                        "Employee User ID already exists"
                    )

                    return@addOnSuccessListener
                }

                /*
                 * IMPORTANT:
                 * Firestore document ID = Employee Firebase UID
                 *
                 * Isse future security rules aur employee login
                 * bahut simple aur secure rahenge.
                 */

                val employee =
                    CloudEmployee(
                        id = employeeUid,
                        masterUid = masterUid,
                        employeeUid = employeeUid,
                        employeeName = cleanName,
                        userId = cleanUserId,
                        authEmail = authEmail,
                        role = "EMPLOYEE",
                        isActive = true,
                        createdAt =
                            System.currentTimeMillis()
                    )

                val employeeRef =
                    collection.document(employeeUid)

                val lookupRef =
                    firestore
                        .collection("employee_lookup")
                        .document(employeeUid)

                val lookupData =
                    hashMapOf<String, Any>(
                        "employeeUid" to employeeUid,
                        "masterUid" to masterUid,
                        "userId" to cleanUserId,
                        "employeeName" to cleanName,
                        "authEmail" to authEmail.trim().lowercase(),
                        "role" to "EMPLOYEE",
                        "isActive" to true,
                        "createdAt" to employee.createdAt,
                        "updatedAt" to System.currentTimeMillis()
                    )

                /*
                 * IMPORTANT:
                 * Employee profile + employee lookup are written together.
                 * If either write fails, neither write is committed.
                 *
                 * Employee login first reads:
                 * employee_lookup/{employeeUid}
                 *
                 * From there it gets masterUid and then reads:
                 * masters/{masterUid}/employees/{employeeUid}
                 */
                firestore
                    .runBatch { batch ->
                        batch.set(
                            employeeRef,
                            employee
                        )

                        batch.set(
                            lookupRef,
                            lookupData
                        )
                    }
                    .addOnSuccessListener {

                        onSuccess(employee)
                    }
                    .addOnFailureListener { error ->

                        onError(
                            error.message
                                ?: "Employee cloud save failed"
                        )
                    }
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Employee check failed"
                )
            }
    }


    // =====================================================
    // GET EMPLOYEES
    // =====================================================

    fun getEmployees(
        onSuccess: (List<CloudEmployee>) -> Unit,
        onError: (String) -> Unit
    ) {

        val collection =
            employeeCollection()

        if (collection == null) {

            onError(
                "Master Admin login required"
            )

            return
        }

        collection
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { result ->

                val employees =
                    result.documents.mapNotNull { document ->

                        try {

                            document
                                .toObject(
                                    CloudEmployee::class.java
                                )
                                ?.copy(
                                    id = document.id
                                )

                        } catch (_: Exception) {

                            null
                        }
                    }

                onSuccess(employees)
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Employee list load failed"
                )
            }
    }


    // =====================================================
    // ACTIVATE / DEACTIVATE CLOUD EMPLOYEE
    // =====================================================

    fun setEmployeeActive(
        employeeUid: String,
        active: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val collection =
            employeeCollection()

        if (collection == null) {

            onError(
                "Master Admin login required"
            )

            return
        }

        if (employeeUid.isBlank()) {

            onError(
                "Employee UID missing"
            )

            return
        }

        val cleanEmployeeUid = employeeUid.trim()
        val employeeRef = collection.document(cleanEmployeeUid)
        val lookupRef = firestore
            .collection("employee_lookup")
            .document(cleanEmployeeUid)

        // The employee app listens to its own lookup document.  Keep this
        // document in sync with the master-owned employee profile so an
        // active session loses access immediately when the master disables it.
        firestore
            .runBatch { batch ->
                batch.update(
                    employeeRef,
                    mapOf(
                        "isActive" to active,
                        // Older employee documents used `active`. Keep it
                        // synchronized during the migration to `isActive`.
                        "active" to active,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                batch.update(
                    lookupRef,
                    mapOf(
                        "isActive" to active,
                        "active" to active,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            }
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Employee status update failed"
                )
            }
    }


    // =====================================================
    // DELETE EMPLOYEE ACCESS
    // =====================================================

    /** Removes employee access records from this Master Admin account. */
    fun deleteEmployee(
        employeeUid: String,
        employeeUserId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUid = employeeUid.trim()
        val cleanUserId = employeeUserId.trim().uppercase()

        if (currentMasterUid().isNullOrBlank()) {
            onError("Master Admin login required")
            return
        }
        if (cleanUid.isBlank() || cleanUserId.isBlank()) {
            onError("Employee identity missing")
            return
        }

        val masterUid = currentMasterUid()
        if (masterUid.isNullOrBlank()) {
            onError("Master Admin login required")
            return
        }

        val employeeRef = firestore.collection("masters").document(masterUid)
            .collection("employees").document(cleanUid)
        val lookupRef = firestore.collection("employee_lookup").document(cleanUid)
        val permissionRef = firestore.collection("masters").document(masterUid)
            .collection("employee_permissions").document(cleanUserId)

        firestore.runBatch { batch ->
            batch.delete(employeeRef)
            batch.delete(lookupRef)
            batch.delete(permissionRef)
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.message ?: "Employee delete failed")
            }
    }


    // =====================================================
    // FIND EMPLOYEE
    // =====================================================

    fun findEmployee(
        userId: String,
        onSuccess: (CloudEmployee?) -> Unit,
        onError: (String) -> Unit
    ) {

        val collection =
            employeeCollection()

        if (collection == null) {

            onError(
                "Master Admin login required"
            )

            return
        }

        val cleanUserId =
            userId.trim().uppercase()

        collection
            .whereEqualTo(
                "userId",
                cleanUserId
            )
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                val document =
                    result.documents.firstOrNull()

                if (document == null) {

                    onSuccess(null)

                } else {

                    val employee =
                        document
                            .toObject(
                                CloudEmployee::class.java
                            )
                            ?.copy(
                                id = document.id
                            )

                    onSuccess(employee)
                }
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Employee search failed"
                )
            }
    }


    // =====================================================
    // CONNECTION TEST
    // =====================================================

    fun testAuthenticatedConnection(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val uid =
            currentMasterUid()

        if (uid == null) {

            onError(
                "Master Admin login required"
            )

            return
        }

        val testData =
            hashMapOf(
                "uid" to uid,
                "status" to "CONNECTED",
                "appName" to "Lakshya",
                "updatedAt" to
                        System.currentTimeMillis()
            )

        firestore
            .collection("masters")
            .document(uid)
            .collection("system")
            .document("connection")
            .set(testData)
            .addOnSuccessListener {

                onSuccess()
            }
            .addOnFailureListener { error ->

                onError(
                    error.message
                        ?: "Firestore connection failed"
                )
            }
    }
}

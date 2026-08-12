package com.example.app

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

object EmployeeAuthManager {

    /** Returns a user-facing message when a password is not strong enough. */
    fun strongPasswordError(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (password.any { it.isWhitespace() }) {
            return "Password must not contain spaces"
        }
        if (password.none { it.isUpperCase() }) {
            return "Password must include an uppercase letter"
        }
        if (password.none { it.isLowerCase() }) {
            return "Password must include a lowercase letter"
        }
        if (password.none { it.isDigit() }) {
            return "Password must include a number"
        }
        if (password.all { it.isLetterOrDigit() }) {
            return "Password must include a special character"
        }
        return null
    }

    private const val SECONDARY_APP_NAME =
        "LakshyaEmployeeCreator"


    // =====================================================
    // SECONDARY FIREBASE AUTH
    //
    // Employee account create karne ke liye alag Firebase
    // Auth instance use hota hai.
    //
    // Isse currently logged-in Master Admin ka Firebase
    // session disturb nahi hota.
    // =====================================================

    private fun getSecondaryAuth(): FirebaseAuth {

        val defaultApp =
            FirebaseApp.getInstance()

        val applicationContext =
            defaultApp.applicationContext

        val existingApp =
            FirebaseApp
                .getApps(applicationContext)
                .firstOrNull {
                    it.name == SECONDARY_APP_NAME
                }

        val secondaryApp =
            existingApp
                ?: FirebaseApp.initializeApp(
                    applicationContext,
                    FirebaseOptions.Builder()

                        .setApplicationId(
                            defaultApp.options.applicationId
                        )

                        .setApiKey(
                            defaultApp.options.apiKey
                        )

                        .setProjectId(
                            defaultApp.options.projectId
                        )

                        .apply {

                            val senderId =
                                defaultApp.options.gcmSenderId

                            if (!senderId.isNullOrBlank()) {
                                setGcmSenderId(senderId)
                            }


                            val storageBucket =
                                defaultApp.options.storageBucket

                            if (!storageBucket.isNullOrBlank()) {
                                setStorageBucket(storageBucket)
                            }


                            val databaseUrl =
                                defaultApp.options.databaseUrl

                            if (!databaseUrl.isNullOrBlank()) {
                                setDatabaseUrl(databaseUrl)
                            }
                        }

                        .build(),

                    SECONDARY_APP_NAME
                )

        return FirebaseAuth.getInstance(
            secondaryApp
        )
    }


    // =====================================================
    // EMPLOYEE USER ID -> INTERNAL FIREBASE EMAIL
    //
    // Example:
    //
    // User ID:
    // NIKHIL
    //
    // Firebase internal email:
    // nikhil@lakshya.app
    //
    // Employee ko ye email enter karne ki zarurat nahi.
    // App automatically User ID se email banayega.
    // =====================================================

    fun employeeEmail(
        userId: String
    ): String {

        val cleanUserId =
            userId
                .trim()
                .lowercase()
                .replace(
                    Regex("[^a-z0-9._-]"),
                    ""
                )

        return "$cleanUserId@lakshya.app"
    }


    // =====================================================
    // CREATE EMPLOYEE FIREBASE AUTH ACCOUNT
    //
    // IMPORTANT:
    //
    // Employee Firebase Auth account secondary Firebase
    // instance se create hota hai.
    //
    // Isliye Master Admin ka current login safe rehta hai.
    // =====================================================

    fun createEmployeeAccount(

        userId: String,

        password: String,

        onSuccess: (
            employeeUid: String,
            employeeEmail: String
        ) -> Unit,

        onError: (
            String
        ) -> Unit

    ) {

        val cleanUserId =
            userId
                .trim()
                .uppercase()


        val cleanPassword =
            password.trim()


        // -------------------------------------------------
        // USER ID VALIDATION
        // -------------------------------------------------

        if (cleanUserId.isBlank()) {

            onError(
                "Employee User ID required"
            )

            return
        }


        if (cleanUserId == "ADMIN") {

            onError(
                "ADMIN User ID is reserved"
            )

            return
        }


        // Employee User ID me kam se kam ek valid
        // Firebase-email character hona chahiye.

        val email =
            employeeEmail(
                cleanUserId
            )


        val emailLocalPart =
            email.substringBefore("@")


        if (emailLocalPart.isBlank()) {

            onError(
                "Invalid Employee User ID"
            )

            return
        }


        // -------------------------------------------------
        // PASSWORD VALIDATION
        // -------------------------------------------------

        if (cleanPassword.isBlank()) {

            onError(
                "Employee Password required"
            )

            return
        }


        val passwordError = strongPasswordError(cleanPassword)

        if (passwordError != null) {

            onError(
                passwordError
            )

            return
        }


        // -------------------------------------------------
        // GET SECONDARY FIREBASE AUTH
        // -------------------------------------------------

        val secondaryAuth =
            try {

                getSecondaryAuth()

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "Employee authentication setup failed"
                )

                return
            }


        // -------------------------------------------------
        // CLEAN SECONDARY SESSION
        // -------------------------------------------------

        try {

            secondaryAuth.signOut()

        } catch (_: Exception) {

            // Ignore.
            // Account creation still continue karega.
        }


        // -------------------------------------------------
        // CREATE FIREBASE AUTH USER
        // -------------------------------------------------

        secondaryAuth
            .createUserWithEmailAndPassword(
                email,
                cleanPassword
            )

            .addOnSuccessListener { result ->

                val employeeUid =
                    result.user?.uid


                if (employeeUid.isNullOrBlank()) {

                    try {

                        secondaryAuth.signOut()

                    } catch (_: Exception) {
                    }


                    onError(
                        "Employee Firebase UID not found"
                    )

                    return@addOnSuccessListener
                }


                /*
                 * IMPORTANT
                 *
                 * Employee Firebase Auth account successfully
                 * create ho gaya.
                 *
                 * Secondary Firebase Auth se immediately logout
                 * kar rahe hain.
                 *
                 * Main/default FirebaseAuth me Master Admin ka
                 * session untouched rahega.
                 */

                try {

                    secondaryAuth.signOut()

                } catch (_: Exception) {
                }


                onSuccess(
                    employeeUid,
                    email
                )
            }

            .addOnFailureListener { error ->

                try {

                    secondaryAuth.signOut()

                } catch (_: Exception) {
                }


                // -----------------------------------------
                // USER FRIENDLY FIREBASE ERROR
                // -----------------------------------------

                val rawMessage =
                    error.message
                        ?: ""


                val message =
                    when {

                        rawMessage.contains(
                            "email address is already",
                            ignoreCase = true
                        ) -> {

                            "Employee User ID already exists"
                        }


                        rawMessage.contains(
                            "email-already-in-use",
                            ignoreCase = true
                        ) -> {

                            "Employee User ID already exists"
                        }


                        rawMessage.contains(
                            "already in use",
                            ignoreCase = true
                        ) -> {

                            "Employee User ID already exists"
                        }


                        rawMessage.contains(
                            "badly formatted",
                            ignoreCase = true
                        ) -> {

                            "Invalid Employee User ID"
                        }


                        rawMessage.contains(
                            "invalid email",
                            ignoreCase = true
                        ) -> {

                            "Invalid Employee User ID"
                        }


                        rawMessage.contains(
                            "password",
                            ignoreCase = true
                        ) -> {

                            if (rawMessage.isNotBlank()) {

                                rawMessage

                            } else {

                                "Invalid employee password"
                            }
                        }


                        rawMessage.contains(
                            "network",
                            ignoreCase = true
                        ) -> {

                            "Internet connection check karo"
                        }


                        rawMessage.contains(
                            "blocked",
                            ignoreCase = true
                        ) -> {

                            "Firebase ne request temporarily block ki hai. Thodi der baad try karo."
                        }


                        rawMessage.isNotBlank() -> {

                            rawMessage
                        }


                        else -> {

                            "Employee account creation failed"
                        }
                    }


                onError(
                    message
                )
            }
    }


    // =====================================================
    // EMPLOYEE INTERNAL EMAIL VALIDATION
    // =====================================================

    fun isValidEmployeeUserId(
        userId: String
    ): Boolean {

        val cleanUserId =
            userId
                .trim()
                .uppercase()


        if (cleanUserId.isBlank()) {
            return false
        }


        if (cleanUserId == "ADMIN") {
            return false
        }


        val generatedEmail =
            employeeEmail(
                cleanUserId
            )


        val localPart =
            generatedEmail
                .substringBefore("@")
                .trim()


        return localPart.isNotBlank()
    }


    // =====================================================
    // GET SECONDARY AUTH CURRENT USER
    //
    // Normally null hona chahiye because employee create
    // hone ke baad secondary session logout kar dete hain.
    // =====================================================

    fun secondaryCurrentUserUid(): String? {

        return try {

            getSecondaryAuth()
                .currentUser
                ?.uid

        } catch (_: Exception) {

            null
        }
    }


    // =====================================================
    // FORCE SECONDARY AUTH LOGOUT
    //
    // Safety/helper function.
    // Master Admin ke main Firebase session ko affect nahi
    // karega.
    // =====================================================

    fun logoutSecondaryAuth() {

        try {

            getSecondaryAuth()
                .signOut()

        } catch (_: Exception) {

            // Ignore logout failure.
        }
    }
}

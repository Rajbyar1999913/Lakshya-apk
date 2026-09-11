package com.example.app

data class CloudEmployee(
    val id: String = "", val masterUid: String = "", val employeeUid: String = "",
    val employeeName: String = "", val userId: String = "", val authEmail: String = "",
    // Stored in international form so Firebase Phone Auth and recovery lookup
    // always compare the same value.
    val mobileE164: String = "",
    val role: String = "EMPLOYEE", val isActive: Boolean = true, val createdAt: Long = 0L
)

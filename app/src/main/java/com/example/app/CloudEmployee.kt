package com.example.app

data class CloudEmployee(
    val id: String = "", val masterUid: String = "", val employeeUid: String = "",
    val employeeName: String = "", val userId: String = "", val authEmail: String = "",
    val role: String = "EMPLOYEE", val isActive: Boolean = true, val createdAt: Long = 0L
)

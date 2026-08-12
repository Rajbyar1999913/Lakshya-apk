package com.example.app

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employee_permissions",
    indices = [
        Index(
            value = ["employeeUserId", "featureKey"],
            unique = true
        )
    ]
)
data class EmployeePermissionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val employeeUserId: String,

    val featureKey: String,

    val isAllowed: Boolean = true
)

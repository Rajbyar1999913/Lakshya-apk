package com.example.app

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [
        Index(
            value = ["userId"],
            unique = true
        )
    ]
)
data class EmployeeEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val employeeName: String,

    val userId: String,

    val role: String = "EMPLOYEE",

    val isActive: Boolean = true,

    val createdTime: Long =
        System.currentTimeMillis()
)

package com.example.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class BillEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Every bill belongs to exactly one Master account.
    val masterUid: String = "",

    val customerName: String,
    val games: String,
    val entries: String,
    val perGameTotal: Int,
    val grandTotal: Int,
    val savedTime: Long,

    val status: String = "ACTIVE",
    val createdBy: String = "",

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
    val dayLockedTime: Long? = null
)

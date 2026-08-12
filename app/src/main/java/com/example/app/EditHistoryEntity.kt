package com.example.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edit_history")
data class EditHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Kis Bill/Entry ko edit kiya gaya
    val billId: Int,

    // =========================
    // OLD DATA
    // =========================

    // Edit se pehle Customer Name
    val oldCustomerName: String,

    // Edit se pehle Games
    val oldGames: String,

    // Edit se pehle Number/Amount Entries
    val oldEntries: String,

    // Edit se pehle Per Game Total
    val oldPerGameTotal: Int,

    // Edit se pehle Grand Total
    val oldGrandTotal: Int,


    // =========================
    // NEW DATA
    // =========================

    // Edit ke baad Customer Name
    val newCustomerName: String = "",

    // Edit ke baad Games
    val newGames: String = "",

    // Edit ke baad Number/Amount Entries
    val newEntries: String = "",

    // Edit ke baad Per Game Total
    val newPerGameTotal: Int = 0,

    // Edit ke baad Grand Total
    val newGrandTotal: Int = 0,


    // =========================
    // EDIT INFORMATION
    // =========================

    // Kis User/Employee ne edit kiya
    val editedBy: String,

    // Edit kab hua
    val editedTime: Long =
        System.currentTimeMillis()
)
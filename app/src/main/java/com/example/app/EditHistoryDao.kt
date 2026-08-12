package com.example.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EditHistoryDao {

    // =========================================
    // SAVE EDIT HISTORY
    // =========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(
        history: EditHistoryEntity
    )


    // =========================================
    // GET COMPLETE EDIT HISTORY
    // =========================================

    @Query(
        """
        SELECT * FROM edit_history
        ORDER BY editedTime DESC
        """
    )
    suspend fun getAllHistory():
            List<EditHistoryEntity>


    // =========================================
    // GET HISTORY OF ONE BILL
    // =========================================

    @Query(
        """
        SELECT * FROM edit_history
        WHERE billId = :billId
        ORDER BY editedTime DESC
        """
    )
    suspend fun getHistoryForBill(
        billId: Int
    ): List<EditHistoryEntity>


    // =========================================
    // DELETE HISTORY OF ONE BILL
    // =========================================

    @Query(
        """
        DELETE FROM edit_history
        WHERE billId = :billId
        """
    )
    suspend fun deleteHistoryForBill(
        billId: Int
    )
}
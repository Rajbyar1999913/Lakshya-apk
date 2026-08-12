package com.example.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillAndGetId(bill: BillEntity): Long

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Query("""
        SELECT * FROM bills
        WHERE masterUid = :masterUid
        ORDER BY savedTime DESC
    """)
    suspend fun getAllBills(masterUid: String): List<BillEntity>

    @Query("""
        SELECT * FROM bills
        WHERE masterUid = :masterUid
        AND status = 'ACTIVE'
        ORDER BY savedTime DESC
    """)
    suspend fun getActiveBills(masterUid: String): List<BillEntity>

    @Query("""
        SELECT * FROM bills
        WHERE masterUid = :masterUid
        AND status = 'ACTIVE'
        AND isPrinted = 1
        ORDER BY savedTime DESC
    """)
    suspend fun getActivePrintedBills(masterUid: String): List<BillEntity>

    @Query("""
        SELECT * FROM bills
        WHERE id = :billId
        AND masterUid = :masterUid
        LIMIT 1
    """)
    suspend fun getBillById(billId: Int, masterUid: String): BillEntity?

    @Query("""
        UPDATE bills
        SET isPrinted = 1,
            printedBy = :printedBy,
            printedTime = :printedTime,
            printCount = printCount + 1
        WHERE id = :billId
        AND masterUid = :masterUid
        AND isPrinted = 0
        AND status = 'ACTIVE'
        AND isDayLocked = 0
    """)
    suspend fun markBillPrinted(
        billId: Int,
        masterUid: String,
        printedBy: String,
        printedTime: Long
    ): Int

    // A print slot is reserved before data is sent to the printer.  This keeps
    // two fast taps (or two screens) from producing two copies of one slip.
    @Query("""
        UPDATE bills
        SET isPrinted = 0,
            printedBy = '',
            printedTime = NULL,
            printCount = CASE WHEN printCount > 0 THEN printCount - 1 ELSE 0 END
        WHERE id = :billId
        AND masterUid = :masterUid
        AND isPrinted = 1
        AND printedBy = :printedBy
        AND printedTime = :printedTime
    """)
    suspend fun releasePrintReservation(
        billId: Int,
        masterUid: String,
        printedBy: String,
        printedTime: Long
    ): Int

    @Query("""
        SELECT isPrinted FROM bills
        WHERE id = :billId AND masterUid = :masterUid
        LIMIT 1
    """)
    suspend fun isBillPrinted(billId: Int, masterUid: String): Boolean?

    @Query("""
        SELECT printedTime FROM bills
        WHERE id = :billId AND masterUid = :masterUid
        LIMIT 1
    """)
    suspend fun getPrintedTime(billId: Int, masterUid: String): Long?

    @Query("""
        SELECT printedBy FROM bills
        WHERE id = :billId AND masterUid = :masterUid
        LIMIT 1
    """)
    suspend fun getPrintedBy(billId: Int, masterUid: String): String?

    @Query("""
        SELECT COUNT(*) FROM bills
        WHERE id = :billId
        AND masterUid = :masterUid
        AND status = 'ACTIVE'
    """)
    suspend fun isBillActive(billId: Int, masterUid: String): Int

    @Query("""
        SELECT isDayLocked FROM bills
        WHERE id = :billId AND masterUid = :masterUid
        LIMIT 1
    """)
    suspend fun isBillDayLocked(billId: Int, masterUid: String): Boolean?

    @Query("""
        UPDATE bills
        SET status = 'CANCELLED',
            cancelledBy = :cancelledBy,
            cancelledTime = :cancelledTime
        WHERE id = :billId
        AND masterUid = :masterUid
        AND status = 'ACTIVE'
        AND isDayLocked = 0
    """)
    suspend fun cancelBill(
        billId: Int,
        masterUid: String,
        cancelledBy: String,
        cancelledTime: Long
    ): Int

    @Query("""
        UPDATE bills
        SET isDayLocked = 1,
            dayLockedBy = :lockedBy,
            dayLockedTime = :lockedTime
        WHERE masterUid = :masterUid
        AND savedTime >= :startTime
        AND savedTime < :endTime
        AND isDayLocked = 0
    """)
    suspend fun lockDayBills(
        masterUid: String,
        startTime: Long,
        endTime: Long,
        lockedBy: String,
        lockedTime: Long
    ): Int

    @Query("""
        SELECT COUNT(*) FROM bills
        WHERE masterUid = :masterUid
        AND savedTime >= :startTime
        AND savedTime < :endTime
        AND isDayLocked = 1
    """)
    suspend fun getLockedBillCountForDay(
        masterUid: String,
        startTime: Long,
        endTime: Long
    ): Int

    @Delete
    suspend fun deleteBill(bill: BillEntity)

    @Query("DELETE FROM bills WHERE masterUid = :masterUid")
    suspend fun deleteAllBills(masterUid: String)
}

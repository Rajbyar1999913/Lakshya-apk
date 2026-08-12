package com.example.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmployeeDao {

    // NEW EMPLOYEE CREATE
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmployee(
        employee: EmployeeEntity
    )

    // CHECK USER ID ALREADY EXISTS
    @Query(
        """
        SELECT COUNT(*) FROM employees
        WHERE userId = :userId
        """
    )
    suspend fun userIdExists(
        userId: String
    ): Int

    // GET ALL EMPLOYEES
    @Query(
        """
        SELECT * FROM employees
        ORDER BY createdTime DESC
        """
    )
    suspend fun getAllEmployees():
            List<EmployeeEntity>

    // DEACTIVATE EMPLOYEE
    @Query(
        """
        UPDATE employees
        SET isActive = 0
        WHERE id = :employeeId
        """
    )
    suspend fun deactivateEmployee(
        employeeId: Int
    )

    // ACTIVATE EMPLOYEE
    @Query(
        """
        UPDATE employees
        SET isActive = 1
        WHERE id = :employeeId
        """
    )
    suspend fun activateEmployee(
        employeeId: Int
    )

    @Query(
        """
        DELETE FROM employees
        WHERE id = :employeeId
        """
    )
    suspend fun deleteEmployee(
        employeeId: Int
    )
}


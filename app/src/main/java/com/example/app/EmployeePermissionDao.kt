package com.example.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmployeePermissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePermission(
        permission: EmployeePermissionEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePermissions(
        permissions: List<EmployeePermissionEntity>
    )

    @Query(
        """
        SELECT * FROM employee_permissions
        WHERE employeeUserId = :userId
        """
    )
    suspend fun getPermissions(
        userId: String
    ): List<EmployeePermissionEntity>

    @Query(
        """
        SELECT isAllowed FROM employee_permissions
        WHERE employeeUserId = :userId
        AND featureKey = :featureKey
        LIMIT 1
        """
    )
    suspend fun isAllowed(
        userId: String,
        featureKey: String
    ): Boolean?

    @Query(
        """
        DELETE FROM employee_permissions
        WHERE employeeUserId = :userId
        """
    )
    suspend fun deleteEmployeePermissions(
        userId: String
    )
}

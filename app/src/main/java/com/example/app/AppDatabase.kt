package com.example.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BillEntity::class,
        EmployeeEntity::class,
        EditHistoryEntity::class,
        EmployeePermissionEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao

    abstract fun employeeDao(): EmployeeDao

    abstract fun editHistoryDao(): EditHistoryDao

    abstract fun employeePermissionDao(): EmployeePermissionDao


    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null


        // =========================================
        // DATABASE VERSION 1 TO 2
        // =========================================

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN status TEXT
                        NOT NULL DEFAULT 'ACTIVE'
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN createdBy TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN cancelledBy TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN cancelledTime INTEGER
                        DEFAULT NULL
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 2 TO 3
        // EMPLOYEE TABLE
        // =========================================

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS employees (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            employeeName TEXT NOT NULL,
                            userId TEXT NOT NULL,
                            password TEXT NOT NULL,
                            role TEXT NOT NULL,
                            isActive INTEGER NOT NULL,
                            createdTime INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS
                        index_employees_userId
                        ON employees(userId)
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 3 TO 4
        // EDIT HISTORY TABLE
        // =========================================

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS edit_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            billId INTEGER NOT NULL,
                            oldCustomerName TEXT NOT NULL,
                            oldGames TEXT NOT NULL,
                            oldEntries TEXT NOT NULL,
                            oldPerGameTotal INTEGER NOT NULL,
                            oldGrandTotal INTEGER NOT NULL,
                            editedBy TEXT NOT NULL,
                            editedTime INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 4 TO 5
        // EDITED ENTRY TRACKING
        // =========================================

        private val MIGRATION_4_5 =
            object : Migration(4, 5) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN isEdited INTEGER
                        NOT NULL DEFAULT 0
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN lastEditedBy TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN lastEditedTime INTEGER
                        DEFAULT NULL
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 5 TO 6
        // OLD + NEW EDIT HISTORY DATA
        // =========================================

        private val MIGRATION_5_6 =
            object : Migration(5, 6) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE edit_history
                        ADD COLUMN newCustomerName TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE edit_history
                        ADD COLUMN newGames TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE edit_history
                        ADD COLUMN newEntries TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE edit_history
                        ADD COLUMN newPerGameTotal INTEGER
                        NOT NULL DEFAULT 0
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE edit_history
                        ADD COLUMN newGrandTotal INTEGER
                        NOT NULL DEFAULT 0
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 6 TO 7
        // PRINT LOCK SECURITY
        // =========================================

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN isPrinted INTEGER
                        NOT NULL DEFAULT 0
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN printedBy TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN printedTime INTEGER
                        DEFAULT NULL
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 7 TO 8
        // DAY CLOSING / DAY LOCK
        // =========================================

        private val MIGRATION_7_8 =
            object : Migration(7, 8) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN isDayLocked INTEGER
                        NOT NULL DEFAULT 0
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN dayLockedBy TEXT
                        NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE bills
                        ADD COLUMN dayLockedTime INTEGER
                        DEFAULT NULL
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 8 TO 9
        // EMPLOYEE FEATURE PERMISSIONS
        // =========================================

        private val MIGRATION_8_9 =
            object : Migration(8, 9) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS employee_permissions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            employeeUserId TEXT NOT NULL,
                            featureKey TEXT NOT NULL,
                            isAllowed INTEGER NOT NULL DEFAULT 1
                        )
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS
                        index_employee_permissions_employeeUserId_featureKey
                        ON employee_permissions(
                            employeeUserId,
                            featureKey
                        )
                        """.trimIndent()
                    )
                }
            }


        // =========================================
        // DATABASE VERSION 9 TO 10
        // MASTER-WISE BILL ISOLATION
        //
        // SAFE MIGRATION
        // =========================================

        private val MIGRATION_9_10 =
            object : Migration(9, 10) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    // -----------------------------------------
                    // CHECK masterUid ALREADY EXISTS OR NOT
                    // -----------------------------------------

                    var masterUidExists = false

                    val cursor =
                        database.query(
                            "PRAGMA table_info(bills)"
                        )

                    cursor.use {

                        val nameIndex =
                            it.getColumnIndex("name")

                        while (it.moveToNext()) {

                            if (
                                nameIndex >= 0 &&
                                it.getString(nameIndex) == "masterUid"
                            ) {

                                masterUidExists = true
                                break
                            }
                        }
                    }


                    // -----------------------------------------
                    // ADD masterUid ONLY IF MISSING
                    // -----------------------------------------

                    if (!masterUidExists) {

                        database.execSQL(
                            """
                            ALTER TABLE bills
                            ADD COLUMN masterUid TEXT
                            NOT NULL DEFAULT ''
                            """.trimIndent()
                        )
                    }


                    // -----------------------------------------
                    // IMPORTANT
                    //
                    // BillEntity currently masterUid index
                    // expect nahi karta.
                    //
                    // Previous migration se agar index create
                    // hua hai to remove kar do.
                    // -----------------------------------------

                    database.execSQL(
                        """
                        DROP INDEX IF EXISTS
                        index_bills_masterUid
                        """.trimIndent()
                    )
                }
            }

        // =========================================
        // DATABASE VERSION 10 TO 11
        // REMOVE LEGACY PLAINTEXT EMPLOYEE PASSWORDS
        // =========================================

        private val MIGRATION_10_11 =
            object : Migration(10, 11) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                        CREATE TABLE employees_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            employeeName TEXT NOT NULL,
                            userId TEXT NOT NULL,
                            role TEXT NOT NULL,
                            isActive INTEGER NOT NULL,
                            createdTime INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        INSERT INTO employees_new (
                            id, employeeName, userId, role, isActive, createdTime
                        )
                        SELECT id, employeeName, userId, role, isActive, createdTime
                        FROM employees
                        """.trimIndent()
                    )

                    database.execSQL("DROP TABLE employees")
                    database.execSQL("ALTER TABLE employees_new RENAME TO employees")
                    database.execSQL(
                        """
                        CREATE UNIQUE INDEX index_employees_userId
                        ON employees(userId)
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bills ADD COLUMN printCount INTEGER NOT NULL DEFAULT 0")
            }
        }


        // =========================================
        // GET DATABASE
        // =========================================

        fun getDatabase(
            context: Context
        ): AppDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "lakshya_database"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11
                                , MIGRATION_11_12
                            )
                            .build()

                    INSTANCE = instance

                    instance
                }
        }
    }
}

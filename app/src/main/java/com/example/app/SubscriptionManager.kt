package com.example.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SubscriptionManager(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "lakshya_subscription",
            Context.MODE_PRIVATE
        )

    companion object {

        private const val KEY_START_DATE =
            "subscription_start_date"

        private const val KEY_EXPIRY_DATE =
            "subscription_expiry_date"

        private const val KEY_EMPLOYEE_LIMIT =
            "employee_limit"

        private const val KEY_MONTHLY_PRICE =
            "monthly_price"

        const val DEFAULT_EMPLOYEE_LIMIT = 5

        const val DEFAULT_MONTHLY_PRICE = 5000
    }


    // GET COMPLETE SUBSCRIPTION DATA

    fun getSubscription(): SubscriptionData {

        return SubscriptionData(

            startDate =
                preferences.getLong(
                    KEY_START_DATE,
                    0L
                ),

            expiryDate =
                preferences.getLong(
                    KEY_EXPIRY_DATE,
                    0L
                ),

            employeeLimit =
                preferences.getInt(
                    KEY_EMPLOYEE_LIMIT,
                    DEFAULT_EMPLOYEE_LIMIT
                ),

            monthlyPrice =
                preferences.getInt(
                    KEY_MONTHLY_PRICE,
                    DEFAULT_MONTHLY_PRICE
                )
        )
    }


    // CHECK ACTIVE / EXPIRED

    fun isSubscriptionActive(): Boolean {

        val expiryDate =
            preferences.getLong(
                KEY_EXPIRY_DATE,
                0L
            )

        return expiryDate >
                System.currentTimeMillis()
    }


    // GET EXPIRY DATE

    fun getExpiryDateFormatted(): String {

        val expiryDate =
            preferences.getLong(
                KEY_EXPIRY_DATE,
                0L
            )

        if (expiryDate == 0L) {

            return "Not Active"
        }

        val formatter =
            SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
            )

        return formatter.format(
            Date(expiryDate)
        )
    }


    // ACTIVATE / RENEW FOR 1 MONTH
    //
    // IMPORTANT:
    // Later this should run only after
    // successful payment verification.

    fun activateOrRenewSubscription() {

        val currentTime =
            System.currentTimeMillis()

        val oldExpiryDate =
            preferences.getLong(
                KEY_EXPIRY_DATE,
                0L
            )


        // If already active:
        // add 1 month after current expiry.
        //
        // If expired:
        // start from today.

        val renewalStartDate =

            if (oldExpiryDate > currentTime) {

                oldExpiryDate

            } else {

                currentTime
            }


        val calendar =
            Calendar.getInstance()

        calendar.timeInMillis =
            renewalStartDate


        calendar.add(
            Calendar.MONTH,
            1
        )


        val newExpiryDate =
            calendar.timeInMillis


        preferences
            .edit()

            .putLong(
                KEY_START_DATE,
                currentTime
            )

            .putLong(
                KEY_EXPIRY_DATE,
                newExpiryDate
            )

            .apply()
    }


    // GET EMPLOYEE LIMIT

    fun getEmployeeLimit(): Int {

        return preferences.getInt(
            KEY_EMPLOYEE_LIMIT,
            DEFAULT_EMPLOYEE_LIMIT
        )
    }


    // CHANGE EMPLOYEE LIMIT

    fun setEmployeeLimit(
        limit: Int
    ) {

        preferences
            .edit()

            .putInt(
                KEY_EMPLOYEE_LIMIT,
                limit
            )

            .apply()
    }


    // GET MONTHLY PRICE

    fun getMonthlyPrice(): Int {

        return preferences.getInt(
            KEY_MONTHLY_PRICE,
            DEFAULT_MONTHLY_PRICE
        )
    }


    // CHANGE MONTHLY PRICE
    // Useful later from backend/admin.

    fun setMonthlyPrice(
        price: Int
    ) {

        preferences
            .edit()

            .putInt(
                KEY_MONTHLY_PRICE,
                price
            )

            .apply()
    }
}
package com.example.app

/** The monthly plan is ₹2,000 per employee, with a minimum of 5 employees. */
fun monthlyPlanPriceFor(employeeLimit: Int): Int =
    employeeLimit.coerceIn(5, 10) * 2_000

data class SubscriptionData(
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val employeeLimit: Int = 5,
    val monthlyPrice: Int = 10000
) {
    fun isActive(): Boolean = expiryDate > System.currentTimeMillis()
}

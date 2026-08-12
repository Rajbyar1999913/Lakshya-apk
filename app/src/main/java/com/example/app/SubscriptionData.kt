package com.example.app

data class SubscriptionData(
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val employeeLimit: Int = 5,
    val monthlyPrice: Int = 5000
) {
    fun isActive(): Boolean = expiryDate > System.currentTimeMillis()
}

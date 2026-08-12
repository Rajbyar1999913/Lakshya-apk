package com.example.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubscriptionScreen(
    isActive: Boolean,
    startDate: String,
    expiryDate: String,
    daysRemaining: Long,
    employeeLimit: Int = 5,
    monthlyPrice: Int = 5000,
    onPayRenewClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onBackClick: () -> Unit
) {

    val backgroundColor = Color(0xFFF4F6FA)
    val primaryColor = Color(0xFF163A5F)
    val activeColor = Color(0xFF168447)
    val expiredColor = Color(0xFFC62828)
    val warningColor = Color(0xFFE58A00)
    val cardColor = Color.White

    val safeDaysRemaining =
        if (daysRemaining < 0) 0 else daysRemaining

    val isExpiringSoon =
        isActive && safeDaysRemaining <= 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {

        Spacer(modifier = Modifier.height(18.dp))

        // =================================================
        // TOP BAR
        // =================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBackClick
            ) {
                Text(
                    text = "← Back",
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "MY PLAN",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )

            Spacer(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.width(65.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))


        // =================================================
        // MAIN PLAN STATUS CARD
        // =================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 5.dp
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // STATUS

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color =
                        if (isActive)
                            activeColor.copy(alpha = 0.12f)
                        else
                            expiredColor.copy(alpha = 0.12f)
                ) {

                    Text(
                        text =
                            if (isActive)
                                "●  PLAN ACTIVE"
                            else
                                "●  PLAN EXPIRED",
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 9.dp
                        ),
                        color =
                            if (isActive)
                                activeColor
                            else
                                expiredColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text =
                        if (isActive)
                            "Your Lakshya plan is active"
                        else
                            "Your plan has expired",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text =
                        if (isActive)
                            "You can continue using all features included in your current plan."
                        else
                            "Please renew your plan to continue using Lakshya.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                HorizontalDivider(
                    color = Color(0xFFE7EAF0)
                )

                Spacer(modifier = Modifier.height(20.dp))


                // PRICE

                Text(
                    text = "CURRENT MONTHLY PLAN",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "₹$monthlyPrice",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryColor
                )

                Text(
                    text = "per month",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(22.dp))


                // EMPLOYEE LIMIT

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF4F7FB)
                ) {

                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Employee Limit",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )

                            Spacer(
                                modifier = Modifier.height(3.dp)
                            )

                            Text(
                                text = "$employeeLimit Employee IDs",
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        Text(
                            text = "$employeeLimit / 10",
                            color = primaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }


        // =================================================
        // EXPIRY WARNING
        // =================================================

        if (isExpiringSoon) {

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        warningColor.copy(alpha = 0.10f)
                )
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "PLAN EXPIRING SOON",
                        color = warningColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text =
                            if (safeDaysRemaining == 0L)
                                "Your plan expires today. Renew now to avoid interruption."
                            else
                                "Your plan expires in $safeDaysRemaining day${if (safeDaysRemaining == 1L) "" else "s"}. Renew now to avoid interruption.",
                        color = Color.DarkGray,
                        fontSize = 13.sp
                    )
                }
            }
        }


        // =================================================
        // PLAN VALIDITY
        // =================================================

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 3.dp
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text = "PLAN VALIDITY",
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                PlanDetailRow(
                    title = "Start Date",
                    value = startDate
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = Color(0xFFEEEEEE)
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title =
                        if (isActive)
                            "Valid Until"
                        else
                            "Expired On",
                    value = expiryDate,
                    valueColor =
                        if (isActive)
                            primaryColor
                        else
                            expiredColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = Color(0xFFEEEEEE)
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title =
                        if (isActive)
                            "Days Remaining"
                        else
                            "Plan Status",
                    value =
                        if (isActive)
                            "$safeDaysRemaining Days"
                        else
                            "Expired",
                    valueColor =
                        when {
                            !isActive ->
                                expiredColor

                            safeDaysRemaining <= 7 ->
                                warningColor

                            else ->
                                activeColor
                        }
                )
            }
        }


        // =================================================
        // PLAN DETAILS
        // =================================================

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 3.dp
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text = "PLAN DETAILS",
                    color = primaryColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))

                PlanDetailRow(
                    title = "Monthly Price",
                    value = "₹$monthlyPrice / Month"
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title = "Employee IDs",
                    value = "$employeeLimit Employees"
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title = "Validity",
                    value = "1 Month"
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title = "Additional Employee",
                    value = "₹1,000 / Month"
                )

                Spacer(modifier = Modifier.height(14.dp))

                PlanDetailRow(
                    title = "Maximum Employees",
                    value = "10"
                )
            }
        }


        // =================================================
        // UPGRADE PLAN
        // =================================================

        if (employeeLimit < 10) {

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        primaryColor.copy(alpha = 0.06f)
                )
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "Need more employees?",
                        color = primaryColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text =
                            "Increase your employee limit up to 10. Each additional employee adds ₹1,000/month to your plan.",
                        color = Color.DarkGray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    OutlinedButton(
                        onClick = onUpgradeClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        Text(
                            text = "UPGRADE PLAN",
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            }
        }


        // =================================================
        // RENEW
        // =================================================

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onPayRenewClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (isActive)
                        primaryColor
                    else
                        expiredColor
            )
        ) {

            Text(
                text =
                    if (isActive)
                        "RENEW PLAN  •  ₹$monthlyPrice"
                    else
                        "RENEW NOW  •  ₹$monthlyPrice",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text =
                "Your plan will be extended for 1 month after successful payment.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}


// =====================================================
// PLAN DETAIL ROW
// =====================================================

@Composable
private fun PlanDetailRow(
    title: String,
    value: String,
    valueColor: Color = Color(0xFF163A5F)
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color.Gray,
            fontSize = 13.sp
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}
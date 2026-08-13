package com.example.app

import android.os.Build

import androidx.compose.runtime.rememberCoroutineScope
import android.os.Bundle
import android.widget.Toast
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import android.net.Uri
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.util.UUID
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.app.ui.theme.LakshyaTheme
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Every installation receives announcements for future required updates.
        UpdateNotifications.subscribeToUpdateAnnouncements()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        setContent {
            LakshyaTheme {
                val lakshyaColors = darkColorScheme(
                    primary = Color(0xFFD4AF37),
                    onPrimary = Color(0xFF071426),
                    primaryContainer = Color(0xFF6F5715),
                    onPrimaryContainer = Color(0xFFFFE9A6),
                    secondary = Color(0xFFFFD86B),
                    onSecondary = Color(0xFF071426),
                    secondaryContainer = Color(0xFF263A55),
                    onSecondaryContainer = Color.White,
                    background = Color(0xFF071426),
                    onBackground = Color(0xFFF4F7FB),
                    surface = Color(0xFF0C1D33),
                    onSurface = Color(0xFFF4F7FB),
                    surfaceVariant = Color(0xFF132844),
                    onSurfaceVariant = Color(0xFFC6D1DF),
                    outline = Color(0xFF64758A),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005)
                )

                MaterialTheme(colorScheme = lakshyaColors) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LakshyaBackendGate()
                    }
                }
            }
        }
    }
}


@Composable
fun LakshyaBackendGate() {

    val context = LocalContext.current

    var config by remember { mutableStateOf<AppConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentVersionCode = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    DisposableEffect(Unit) {
        val listener = AppConfigManager.listenAppConfig(
            onUpdate = { updatedConfig ->
                config = updatedConfig
                isLoading = false
                errorMessage = null
            },
            onError = { message ->
                errorMessage = message
                isLoading = false
            }
        )

        onDispose {
            listener.remove()
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to Lakshya...")
            }
        }
        return
    }

    val currentConfig = config

    if (currentConfig == null) {
        BackendBlockedScreen(
            title = "Connection Error",
            message = errorMessage ?: "Unable to connect to Lakshya server."
        )
        return
    }

    when {
        !currentConfig.appEnabled -> BackendBlockedScreen(
            title = "Lakshya Temporarily Unavailable",
            message = "Lakshya is currently unavailable. Please try again later."
        )

        currentConfig.maintenanceMode -> BackendBlockedScreen(
            title = "Maintenance",
            message = currentConfig.maintenanceMessage
        )

        currentConfig.forceUpdate &&
                AppConfigManager.isUpdateRequired(currentVersionCode, currentConfig) -> BackendBlockedScreen(
            title = "Update Required",
            message = currentConfig.updateMessage,
            actionLabel = "UPDATE NOW",
            onAction = if (!AppDistribution.isOfficialDownloadUrl(currentConfig.updateUrl)) {
                null
            } else {
                {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(currentConfig.updateUrl))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "Update link could not be opened. Please contact support.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        !currentConfig.loginEnabled -> BackendBlockedScreen(
            title = "Login Temporarily Disabled",
            message = "Login is currently unavailable. Please try again later."
        )

        else -> LakshyaApp()
    }
}

@Composable
fun BackendBlockedScreen(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF071426)).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1D33))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LAKSHYA", color = Color(0xFFD4AF37), fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Text(message, color = Color(0xFFC6D1DF), fontSize = 15.sp, textAlign = TextAlign.Center)
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

// =====================================================
// FINAL 14 GAMES
// =====================================================

val gameList = listOf(
    "MO",
    "NO",
    "RDO",
    "KO",
    "MC",
    "NC",
    "RDC",
    "KC",
    "KNO",
    "RO",
    "MBO",
    "KNC",
    "RC",
    "MBC"
)


// =====================================================
// DATA CLASSES
// =====================================================

data class NumberAmountEntry(
    val number: String,
    val amount: Int,
    val entryType: String,
    // Keeps half-rupee entries exact while amount continues to support
    // existing limit/chukara calculations that use whole rupees.
    val actualAmount: Double = amount.toDouble()
)


data class SavedEntry(
    val id: Int = 0,
    val customerName: String,
    val games: List<String>,
    val entries: List<NumberAmountEntry>,
    val perGameTotal: Int,
    val grandTotal: Int,
    val savedTime: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val createdBy: String = "",
    val cancelledBy: String = "",
    val cancelledTime: Long? = null,
    val isEdited: Boolean = false,
    val lastEditedBy: String = "",
    val lastEditedTime: Long? = null,
    val isPrinted: Boolean = false,
    val printedBy: String = "",
    val printedTime: Long? = null,
    val printCount: Int = 0,
    val isDayLocked: Boolean = false,
    val dayLockedBy: String = "",
    val dayLockedTime: Long? = null
)


data class MixedParseResult(
    val entries: List<NumberAmountEntry>,
    val invalidNumbers: List<String>,
    val invalidEntryTypes: List<String>
)


data class PrintPreviewData(
    val billId: Int = 0,
    val customerName: String,
    val selectedGames: List<String>,
    val parsedEntries: List<NumberAmountEntry>,
    val grandTotal: Int,
    val date: String,
    val time: String
)


// =====================================================
// SUPER MASTER + CLOUD DIRECTORY
// =====================================================

const val LAKSHYA_SUPER_MASTER_UID = "dJQ1iVUP10R8TDhdUNwOx1iq1Xk2"

data class SuperMasterCustomer(
    val masterUid: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val mobile: String = "",
    val email: String = "",
    val accountStatus: String = "",
    val masterAccessActive: Boolean = false,
    val selectedEmployeeLimit: Int = 5,
    val selectedMonthlyPrice: Int = 5000,
    val createdAt: Long = 0L,
    val subscription: CloudSubscriptionData? = null
)

object SuperMasterCloudManager {

    private val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    data class CustomerPage(
        val customers: List<SuperMasterCustomer>,
        val lastDocument:
        com.google.firebase.firestore.DocumentSnapshot?,
        val hasMore: Boolean
    )

    fun getCustomersPage(
        pageSize: Long = 25L,
        lastDocument:
        com.google.firebase.firestore.DocumentSnapshot? = null,
        onSuccess: (CustomerPage) -> Unit,
        onError: (String) -> Unit
    ) {
        val authUid =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                .orEmpty()

        if (authUid != LAKSHYA_SUPER_MASTER_UID) {
            onError("Super Master access denied")
            return
        }

        var query =
            firestore
                .collection("masters")
                .orderBy(
                    "createdAt",
                    com.google.firebase.firestore.Query.Direction.DESCENDING
                )
                .limit(pageSize)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        query.get()
            .addOnSuccessListener { snapshot ->

                val customers =
                    snapshot.documents.map { document ->

                        // Subscription summary is read directly from the
                        // Master document. No N+1 subscription reads.
                        val summaryActive =
                            document.getBoolean("subscriptionActive")
                                ?: false

                        val summaryExpiry =
                            document.getLong("subscriptionExpiryDate")
                                ?: 0L

                        val summaryLimit =
                            (
                                    document.getLong("subscriptionEmployeeLimit")
                                        ?: document.getLong(
                                            "selectedEmployeeLimit"
                                        )
                                        ?: 5L
                                    ).toInt()

                        val summaryPrice =
                            (
                                    document.getLong("subscriptionMonthlyPrice")
                                        ?: document.getLong(
                                            "selectedMonthlyPrice"
                                        )
                                        ?: 5000L
                                    ).toInt()

                        val summarySubscription =
                            CloudSubscriptionData(
                                startDate =
                                    document.getLong(
                                        "subscriptionStartDate"
                                    ) ?: 0L,
                                expiryDate = summaryExpiry,
                                employeeLimit = summaryLimit,
                                monthlyPrice = summaryPrice,
                                isActive = summaryActive,
                                updatedAt =
                                    document.getLong(
                                        "subscriptionUpdatedAt"
                                    ) ?: 0L
                            )

                        SuperMasterCustomer(
                            masterUid = document.id,
                            businessName =
                                document.getString(
                                    "businessName"
                                ).orEmpty(),
                            ownerName =
                                document.getString(
                                    "ownerName"
                                ).orEmpty(),
                            mobile =
                                document.getString(
                                    "mobile"
                                ).orEmpty(),
                            email =
                                document.getString(
                                    "email"
                                ).orEmpty(),
                            accountStatus =
                                document.getString(
                                    "accountStatus"
                                ).orEmpty(),
                            masterAccessActive =
                                document.getBoolean("isActive") == true,
                            selectedEmployeeLimit =
                                (
                                        document.getLong(
                                            "selectedEmployeeLimit"
                                        ) ?: summaryLimit.toLong()
                                        ).toInt(),
                            selectedMonthlyPrice =
                                (
                                        document.getLong(
                                            "selectedMonthlyPrice"
                                        ) ?: summaryPrice.toLong()
                                        ).toInt(),
                            createdAt =
                                document.getLong(
                                    "createdAt"
                                ) ?: 0L,
                            subscription =
                                summarySubscription
                        )
                    }

                onSuccess(
                    CustomerPage(
                        customers = customers,
                        lastDocument =
                            snapshot.documents.lastOrNull(),
                        hasMore =
                            snapshot.documents.size >= pageSize
                    )
                )
            }
            .addOnFailureListener { error ->
                onError(
                    error.message
                        ?: "Unable to load customers"
                )
            }
    }

    fun deactivatePlan(
        masterUid: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authUid =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                .orEmpty()

        if (authUid != LAKSHYA_SUPER_MASTER_UID) {
            onError("Super Master access denied")
            return
        }

        // Permanent protection: Super Master can never deactivate itself.
        if (masterUid == LAKSHYA_SUPER_MASTER_UID) {
            onError("Super Master account is protected and cannot be deactivated")
            return
        }

        if (masterUid.isBlank()) {
            onError("Master UID missing")
            return
        }

        val now = System.currentTimeMillis()

        val masterRef =
            firestore
                .collection("masters")
                .document(masterUid)

        val subscriptionRef =
            masterRef
                .collection("subscription")
                .document("current")

        val masterData =
            hashMapOf<String, Any>(
                "accountStatus" to "INACTIVE",
                "isActive" to false,
                "active" to false,
                "adminStatus" to "INACTIVE",
                "accessEnabled" to false,
                "subscriptionActive" to false,
                "subscriptionUpdatedAt" to now,
                "lastDeactivationType" to "MANUAL_ADMIN",
                "deactivatedByUid" to authUid,
                "deactivatedAt" to now,
                "updatedAt" to now
            )

        val subscriptionData =
            hashMapOf<String, Any>(
                "isActive" to false,
                "deactivationType" to "MANUAL_ADMIN",
                "deactivatedByUid" to authUid,
                "deactivatedAt" to now,
                "updatedAt" to now
            )

        firestore.runBatch { batch ->
            batch.set(
                masterRef,
                masterData,
                com.google.firebase.firestore.SetOptions.merge()
            )

            batch.set(
                subscriptionRef,
                subscriptionData,
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(
                    error.message ?: "Plan deactivation failed"
                )
            }
    }


    fun activateManuallyWithoutPayment(
        masterUid: String,
        employeeLimit: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authUid =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser
                ?.uid
                .orEmpty()

        if (authUid != LAKSHYA_SUPER_MASTER_UID) {
            onError("Super Master access denied")
            return
        }

        val safeLimit = employeeLimit.coerceIn(5, 10)
        val monthlyPrice = safeLimit * 1000
        val startDate = System.currentTimeMillis()

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = startDate
            add(java.util.Calendar.MONTH, 1)
        }
        val expiryDate = calendar.timeInMillis

        val subscription = CloudSubscriptionData(
            startDate = startDate,
            expiryDate = expiryDate,
            employeeLimit = safeLimit,
            monthlyPrice = monthlyPrice,
            isActive = true,
            updatedAt = startDate
        )

        val subscriptionData = hashMapOf<String, Any>(
            "startDate" to startDate,
            "expiryDate" to expiryDate,
            "employeeLimit" to safeLimit,
            "monthlyPrice" to monthlyPrice,
            "isActive" to true,
            "activationType" to "MANUAL_ADMIN",
            "paymentStatus" to "NO_PAYMENT",
            "activatedByUid" to authUid,
            "activatedAt" to startDate,
            "updatedAt" to startDate
        )

        val masterSummary = hashMapOf<String, Any>(
            // Master/Admin account access status
            "accountStatus" to "ACTIVE",
            "isActive" to true,
            "active" to true,
            "adminStatus" to "ACTIVE",
            "accessEnabled" to true,

            "selectedEmployeeLimit" to safeLimit,
            "selectedMonthlyPrice" to monthlyPrice,
            "subscriptionActive" to true,
            "subscriptionStartDate" to startDate,
            "subscriptionExpiryDate" to expiryDate,
            "subscriptionEmployeeLimit" to safeLimit,
            "subscriptionMonthlyPrice" to monthlyPrice,
            "subscriptionUpdatedAt" to startDate,
            "lastActivationType" to "MANUAL_ADMIN",
            "lastPaymentStatus" to "NO_PAYMENT",
            "deactivatedAt" to 0L,
            "deactivatedByUid" to "",
            "lastDeactivationType" to ""
        )

        val masterRef =
            firestore.collection("masters").document(masterUid)

        firestore.runBatch { batch ->
            batch.set(
                masterRef.collection("subscription").document("current"),
                subscriptionData,
                com.google.firebase.firestore.SetOptions.merge()
            )
            batch.set(
                masterRef,
                masterSummary,
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(
                    error.message ?: "Manual activation failed"
                )
            }
    }


    fun syncSubscriptionSummary(
        masterUid: String,
        subscription: CloudSubscriptionData
    ) {
        if (masterUid.isBlank()) return

        val summary =
            mapOf<String, Any>(
                "subscriptionActive" to
                        subscription.isCurrentlyActive(),
                "subscriptionStartDate" to
                        subscription.startDate,
                "subscriptionExpiryDate" to
                        subscription.expiryDate,
                "subscriptionEmployeeLimit" to
                        subscription.employeeLimit,
                "subscriptionMonthlyPrice" to
                        subscription.monthlyPrice,
                "subscriptionUpdatedAt" to
                        System.currentTimeMillis()
            )

        firestore
            .collection("masters")
            .document(masterUid)
            .set(
                summary,
                com.google.firebase.firestore.SetOptions.merge()
            )
    }
}

fun displayAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()

object CloudResultManager {

    private val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    fun saveResult(
        masterUid: String,
        game: String,
        result: String,
        savedTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (masterUid.isBlank()) return

        val dateKey =
            java.text.SimpleDateFormat(
                "yyyy-MM-dd",
                java.util.Locale.getDefault()
            ).format(java.util.Date(savedTime))

        val data = hashMapOf<String, Any>(
            "masterUid" to masterUid,
            "game" to game,
            "result" to result,
            "savedTime" to savedTime,
            "dateKey" to dateKey,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore
            .collection("masters")
            .document(masterUid)
            .collection("results")
            .document("${dateKey}_${game}")
            .set(data)
            .addOnSuccessListener {
                CloudAccountSyncManager.saveLiveResult(
                    masterUid = masterUid,
                    game = game,
                    result = result,
                    savedTime = savedTime,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Result cloud sync failed")
            }
    }

    fun deleteResult(
        masterUid: String,
        game: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (masterUid.isBlank()) return
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        firestore.collection("masters").document(masterUid)
            .collection("results").document("${dateKey}_${game}").delete()
            .addOnSuccessListener {
                CloudAccountSyncManager.deleteLiveResult(masterUid, game, onSuccess, onError)
            }
            .addOnFailureListener { onError(it.message ?: "Result delete failed") }
    }
}


// =====================================================
// CLOUD EDIT HISTORY / AUDIT
// =====================================================

data class CloudEditHistoryRecord(
    val billId: Int = 0,
    val oldCustomerName: String = "",
    val oldGames: String = "",
    val oldEntries: String = "",
    val oldPerGameTotal: Int = 0,
    val oldGrandTotal: Int = 0,
    val newCustomerName: String = "",
    val newGames: String = "",
    val newEntries: String = "",
    val newPerGameTotal: Int = 0,
    val newGrandTotal: Int = 0,
    val editedBy: String = "",
    val editedTime: Long = 0L
)

object CloudEditHistoryManager {
    private val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    fun saveHistory(
        masterUid: String,
        record: CloudEditHistoryRecord,
        onError: (String) -> Unit = {}
    ) {
        if (masterUid.isBlank()) return

        val data = hashMapOf<String, Any>(
            "masterUid" to masterUid,
            "billId" to record.billId,
            "oldCustomerName" to record.oldCustomerName,
            "oldGames" to record.oldGames,
            "oldEntries" to record.oldEntries,
            "oldPerGameTotal" to record.oldPerGameTotal,
            "oldGrandTotal" to record.oldGrandTotal,
            "newCustomerName" to record.newCustomerName,
            "newGames" to record.newGames,
            "newEntries" to record.newEntries,
            "newPerGameTotal" to record.newPerGameTotal,
            "newGrandTotal" to record.newGrandTotal,
            "editedBy" to record.editedBy,
            "editedTime" to record.editedTime,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("masters")
            .document(masterUid)
            .collection("edit_history")
            .document()
            .set(data)
            .addOnFailureListener { error ->
                onError(error.message ?: "Edit history cloud sync failed")
            }
    }

    fun listenHistory(
        masterUid: String,
        onUpdate: (List<CloudEditHistoryRecord>) -> Unit,
        onError: (String) -> Unit = {}
    ): com.google.firebase.firestore.ListenerRegistration? {
        if (masterUid.isBlank()) {
            onUpdate(emptyList())
            return null
        }

        return firestore.collection("masters")
            .document(masterUid)
            .collection("edit_history")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Edit history cloud load failed")
                    return@addSnapshotListener
                }

                val rows = snapshot?.documents.orEmpty().map { document ->
                    CloudEditHistoryRecord(
                        billId = (document.getLong("billId") ?: 0L).toInt(),
                        oldCustomerName = document.getString("oldCustomerName").orEmpty(),
                        oldGames = document.getString("oldGames").orEmpty(),
                        oldEntries = document.getString("oldEntries").orEmpty(),
                        oldPerGameTotal = (document.getLong("oldPerGameTotal") ?: 0L).toInt(),
                        oldGrandTotal = (document.getLong("oldGrandTotal") ?: 0L).toInt(),
                        newCustomerName = document.getString("newCustomerName").orEmpty(),
                        newGames = document.getString("newGames").orEmpty(),
                        newEntries = document.getString("newEntries").orEmpty(),
                        newPerGameTotal = (document.getLong("newPerGameTotal") ?: 0L).toInt(),
                        newGrandTotal = (document.getLong("newGrandTotal") ?: 0L).toInt(),
                        editedBy = document.getString("editedBy").orEmpty(),
                        editedTime = document.getLong("editedTime") ?: 0L
                    )
                }.sortedByDescending { it.editedTime }

                onUpdate(rows)
            }
    }
}

// =====================================================
// PAYMENT FLOW
// =====================================================

enum class PaymentType { NEW_PLAN, RENEWAL, EMPLOYEE_UPGRADE }

data class PaymentRequestData(
    val masterUid: String,
    val paymentType: PaymentType,
    val currentEmployeeLimit: Int,
    val selectedEmployeeLimit: Int,
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

object MasterAccessManager {

    private val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    fun verifyMasterAccess(
        masterUid: String,
        onAllowed: (CloudSubscriptionData) -> Unit,
        onBlocked: (CloudSubscriptionData?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (masterUid.isBlank()) {
            onError("Master UID missing")
            return
        }

        // Super Master is a protected system account.
        // Never apply normal Master/Admin active/subscription blocking to it.
        if (masterUid == LAKSHYA_SUPER_MASTER_UID) {
            onAllowed(
                CloudSubscriptionData(
                    startDate = 0L,
                    expiryDate = Long.MAX_VALUE,
                    employeeLimit = 10,
                    monthlyPrice = 0,
                    isActive = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val masterRef =
            firestore.collection("masters").document(masterUid)

        val subscriptionRef =
            masterRef.collection("subscription").document("current")

        // Read both exact documents used by Super Master activation.
        firestore.runTransaction { transaction ->
            val masterDoc = transaction.get(masterRef)
            val subscriptionDoc = transaction.get(subscriptionRef)

            // CONFIRMED FIRESTORE STRUCTURE:
            // masters/{uid}.isActive = Master/Admin ID access
            // masters/{uid}/subscription/current.isActive = plan access
            //
            // Do not use any "admins" collection.
            // Do not let old accessEnabled/accountStatus override this field.
            val accessEnabled =
                masterDoc.getBoolean("isActive") == true

            // Read Firestore fields DIRECTLY.
            // Do not use toObject() here because Kotlin/Firebase boolean
            // property mapping can turn Firestore "isActive" into the
            // default false value for an `isActive` property.
            val subscription =
                if (subscriptionDoc.exists()) {
                    CloudSubscriptionData(
                        startDate =
                            subscriptionDoc.getLong("startDate") ?: 0L,
                        expiryDate =
                            subscriptionDoc.getLong("expiryDate") ?: 0L,
                        employeeLimit =
                            (subscriptionDoc.getLong("employeeLimit")
                                ?: 5L).toInt(),
                        monthlyPrice =
                            (subscriptionDoc.getLong("monthlyPrice")
                                ?: 5000L).toInt(),
                        isActive =
                            subscriptionDoc.getBoolean("isActive") == true,
                        updatedAt =
                            subscriptionDoc.getLong("updatedAt") ?: 0L
                    )
                } else {
                    null
                }

            Pair(accessEnabled, subscription)
        }
            .addOnSuccessListener { result ->
                val accessEnabled = result.first
                val subscription = result.second
                val now = System.currentTimeMillis()

                if (
                    accessEnabled &&
                    subscription?.isCurrentlyActive() == true
                ) {
                    onAllowed(subscription)
                } else {
                    onBlocked(subscription)
                }
            }
            .addOnFailureListener { error ->
                onError(
                    error.message ?: "Unable to verify Master access"
                )
            }
    }
}


object CloudPaymentManager {
    private val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    fun createPendingPayment(
        request: PaymentRequestData,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val doc = firestore.collection("masters")
            .document(request.masterUid)
            .collection("payments")
            .document()

        val data = hashMapOf<String, Any>(
            "paymentId" to doc.id,
            "masterUid" to request.masterUid,
            "paymentType" to request.paymentType.name,
            "currentEmployeeLimit" to request.currentEmployeeLimit,
            "selectedEmployeeLimit" to request.selectedEmployeeLimit,
            "amount" to request.amount,
            "currency" to "INR",
            "status" to "PENDING",
            "createdAt" to request.createdAt,
            "updatedAt" to System.currentTimeMillis(),
            "gatewayOrderId" to "",
            "gatewayPaymentId" to "",
            "verifiedByBackend" to false
        )

        doc.set(data)
            .addOnSuccessListener { onSuccess(doc.id) }
            .addOnFailureListener {
                onError(it.message ?: "Payment request failed")
            }
    }
}

// =====================================================
// MAIN APP
// =====================================================

@Composable
fun LakshyaApp() {

    var currentScreen by remember {
        mutableStateOf("welcome")
    }

    var currentUserId by remember {
        mutableStateOf("")
    }

    var currentUserRole by remember {
        mutableStateOf("")
    }

    var currentMasterUid by remember {
        mutableStateOf("")
    }

    var newCustomerBusinessName by remember {
        mutableStateOf("")
    }

    var newCustomerOwnerName by remember {
        mutableStateOf("")
    }

    var newCustomerEmail by remember {
        mutableStateOf("")
    }

    var pendingPaymentRequest by remember {
        mutableStateOf<PaymentRequestData?>(null)
    }
    var pendingPaymentId by remember {
        mutableStateOf("")
    }

    val currentEmployeePermissions = remember {
        mutableStateMapOf<String, Boolean>()
    }

    var entryToEdit by remember {
        mutableStateOf<SavedEntry?>(null)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()


    fun openPayment(request: PaymentRequestData) {
        pendingPaymentRequest = request
        pendingPaymentId = ""

        CloudPaymentManager.createPendingPayment(
            request = request,
            onSuccess = { id ->
                pendingPaymentId = id
                currentScreen = "payment"
            },
            onError = { message ->
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // =====================================================
    // CLOUD SUBSCRIPTION - single source of truth
    // =====================================================
    var cloudSubscriptionData by remember {
        mutableStateOf<CloudSubscriptionData?>(null)
    }

    var cloudSubscriptionLoading by remember {
        mutableStateOf(false)
    }

    // =====================================================
    // NAVIGATION SECURITY
    // =====================================================
    val publicScreens = remember {
        setOf(
            "welcome",
            "publicDemo",
            "publicRegister",
            "login"
        )
    }

    LaunchedEffect(currentScreen) {
        val firebaseUser =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser

        if (
            currentScreen !in publicScreens &&
            firebaseUser == null
        ) {
            currentUserId = ""
            currentUserRole = ""
            currentMasterUid = ""
            cloudSubscriptionData = null
            currentEmployeePermissions.clear()
            currentScreen = "welcome"
        }
    }

    val savedEntries = remember {
        mutableStateListOf<SavedEntry>()
    }

    val database = remember {
        AppDatabase.getDatabase(context)
    }


    DisposableEffect(currentUserRole, currentUserId, currentMasterUid) {
        if (currentUserRole != "EMPLOYEE" || currentUserId.isBlank() || currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val registration = CloudPermissionManager.getPermissionsForSignedInEmployee(
                onSuccess = { cloudPermissions ->
                    EMPLOYEE_FEATURE_OPTIONS.forEach { feature ->
                        currentEmployeePermissions[feature.key] =
                            cloudPermissions[feature.key] ?: true
                    }
                    coroutineScope.launch {
                        val rows = EMPLOYEE_FEATURE_OPTIONS.map { feature ->
                            EmployeePermissionEntity(
                                employeeUserId = currentUserId,
                                featureKey = feature.key,
                                isAllowed = currentEmployeePermissions[feature.key] ?: true
                            )
                        }
                        database.employeePermissionDao().savePermissions(rows)
                    }
                },
                onError = {
                    // Do not grant access when permissions cannot be verified.
                    // This protects a newly installed employee device when it is
                    // offline or Firestore access has been misconfigured.
                    EMPLOYEE_FEATURE_OPTIONS.forEach { feature ->
                        currentEmployeePermissions[feature.key] = false
                    }
                },
                onAccessRevoked = {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    currentUserId = ""
                    currentUserRole = ""
                    currentMasterUid = ""
                    cloudSubscriptionData = null
                    currentEmployeePermissions.clear()
                    currentScreen = "login"
                    Toast.makeText(
                        context,
                        "Your Employee account has been deactivated by the Master Admin.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
            onDispose { registration?.remove() }
        }
    }

    var showCloseDayDialog by remember {
        mutableStateOf(false)
    }

    val dayArchivePrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_day_archive_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    var businessDayStartRefresh by remember {
        mutableIntStateOf(0)
    }

    // =====================================================
    // REAL-TIME SHARED ACCOUNT DAY STATE
    // Same masterUid = same business day on every device/role.
    // =====================================================
    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val registration =
                CloudAccountSyncManager.listenDayState(
                    masterUid = currentMasterUid,
                    onUpdate = { state ->
                        dayArchivePrefs.edit()
                            .putLong("CURRENT_DAY_START", state.currentDayStart)
                            .putLong("LAST_CLOSED_DAY_START", state.lastClosedDayStart)
                            .putLong("LAST_CLOSED_DAY_END", state.lastClosedDayEnd)
                            .apply()

                        businessDayStartRefresh++
                    },
                    onError = { /* Keep local state if temporarily offline. */ }
                )

            onDispose {
                registration?.remove()
            }
        }
    }

    fun currentBusinessDayStart(): Long {
        // Reading this state makes CURRENT_DAY_START changes recompose
        // every current-day screen immediately.
        businessDayStartRefresh

        val savedStart =
            dayArchivePrefs.getLong(
                "CURRENT_DAY_START",
                0L
            )

        if (savedStart > 0L) {
            return savedStart
        }

        val calendar =
            java.util.Calendar.getInstance()

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

    fun todayRange(): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = calendar.timeInMillis
        return start to end
    }

    fun isToday(time: Long): Boolean {
        return time >= currentBusinessDayStart()
    }

    /*
     * DATA VISIBILITY
     *
     * ADMIN:
     *   Sees the complete Master account data.
     *
     * EMPLOYEE:
     *   Sees only entries created by the currently logged-in Employee ID.
     *
     * IMPORTANT:
     * We do NOT remove the Master UID relationship. Employee entries still
     * belong to the same Master account, so the Master can see them.
     */
    val visibleEntries = savedEntries.filter { entry ->
        currentUserRole == "ADMIN" ||
                (
                        currentUserRole == "EMPLOYEE" &&
                                entry.createdBy.trim().equals(
                                    currentUserId.trim(),
                                    ignoreCase = true
                                )
                        )
    }

    val todayEntries = visibleEntries.filter {
        isToday(it.savedTime)
    }

    // =====================================================
    // MASTER-WISE BILL RESTORE + REAL-TIME SYNC
    //
    // Firebase is the source of truth. After Clear Data / reinstall /
    // new phone, the first cloud snapshot restores the account again.
    // Every later cloud bill change refreshes this device automatically.
    // Room remains only the account-specific offline cache.
    // =====================================================
    DisposableEffect(currentMasterUid) {

        savedEntries.clear()

        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            fun refreshBillsFromCloud(): com.google.firebase.firestore.ListenerRegistration? {
                return CloudBillManager.getBills(
                    masterUid = currentMasterUid,
                    onSuccess = { cloudBills ->
                        val restored = cloudBills.map { bill ->
                            SavedEntry(
                                id = bill.localBillId,
                                customerName = bill.customerName,
                                games = bill.games,
                                entries = bill.entries.map {
                                    NumberAmountEntry(
                                        number = it.number,
                                        amount = it.amount,
                                        entryType = it.entryType,
                                        actualAmount = it.actualAmount
                                    )
                                },
                                perGameTotal = bill.perGameTotal,
                                grandTotal = bill.grandTotal,
                                savedTime = bill.savedTime,
                                status = bill.status,
                                createdBy = bill.createdBy,
                                cancelledBy = bill.cancelledBy,
                                cancelledTime = bill.cancelledTime,
                                isEdited = bill.isEdited,
                                lastEditedBy = bill.lastEditedBy,
                                lastEditedTime = bill.lastEditedTime,
                                isPrinted = bill.isPrinted,
                                printedBy = bill.printedBy,
                                printedTime = bill.printedTime,
                                printCount = bill.printCount,
                                isDayLocked = bill.isDayLocked,
                                dayLockedBy = bill.dayLockedBy,
                                dayLockedTime = bill.dayLockedTime
                            )
                        }

                        savedEntries.clear()
                        savedEntries.addAll(restored)

                        // Rebuild the local Room cache after Clear Data/reinstall.
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            restored.forEach { entry ->
                                try {
                                    database.billDao().insertBill(
                                        BillEntity(
                                            id = entry.id,
                                            masterUid = currentMasterUid,
                                            customerName = entry.customerName,
                                            games = serializeGames(entry.games),
                                            entries = serializeEntries(entry.entries),
                                            perGameTotal = entry.perGameTotal,
                                            grandTotal = entry.grandTotal,
                                            savedTime = entry.savedTime,
                                            status = entry.status,
                                            createdBy = entry.createdBy,
                                            cancelledBy = entry.cancelledBy,
                                            cancelledTime = entry.cancelledTime,
                                            isEdited = entry.isEdited,
                                            lastEditedBy = entry.lastEditedBy,
                                            lastEditedTime = entry.lastEditedTime,
                                            isPrinted = entry.isPrinted,
                                            printedBy = entry.printedBy,
                                            printedTime = entry.printedTime,
                                            isDayLocked = entry.isDayLocked,
                                            dayLockedBy = entry.dayLockedBy,
                                            dayLockedTime = entry.dayLockedTime
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                    onError = { cloudError ->
                        coroutineScope.launch {
                            val bills =
                                database.billDao().getAllBills(currentMasterUid)

                            savedEntries.clear()
                            savedEntries.addAll(
                                bills.map { bill ->
                                    SavedEntry(
                                        id = bill.id,
                                        customerName = bill.customerName,
                                        games = deserializeGames(bill.games),
                                        entries = deserializeEntries(bill.entries),
                                        perGameTotal = bill.perGameTotal,
                                        grandTotal = bill.grandTotal,
                                        savedTime = bill.savedTime,
                                        status = bill.status,
                                        createdBy = bill.createdBy,
                                        cancelledBy = bill.cancelledBy,
                                        cancelledTime = bill.cancelledTime,
                                        isEdited = bill.isEdited,
                                        lastEditedBy = bill.lastEditedBy,
                                        lastEditedTime = bill.lastEditedTime,
                                        isPrinted = bill.isPrinted,
                                        printedBy = bill.printedBy,
                                        printedTime = bill.printedTime,
                                        isDayLocked = bill.isDayLocked,
                                        dayLockedBy = bill.dayLockedBy,
                                        dayLockedTime = bill.dayLockedTime
                                    )
                                }
                            )

                            if (bills.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Cloud restore failed: $cloudError",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }

            // One listener is enough. Previously this listener started a new
            // listener after every bill change, which made the app slower over
            // time and repeated the same Room-cache work.
            val billRegistration = refreshBillsFromCloud()

            onDispose {
                billRegistration?.remove()
            }
        }
    }


    var printPreviewData by remember {
        mutableStateOf<PrintPreviewData?>(null)
    }

    // PRINT PREVIEW ko pata rahega ki BACK kis screen par jana hai.
    // New Entry se print hua to New Entry, History Edit se print hua to Edit Entry.
    var printPreviewReturnScreen by remember {
        mutableStateOf("newEntry")
    }


    var showExitDialog by remember { mutableStateOf(false) }

    // Mobile system BACK button / gesture support.
    BackHandler(enabled = currentScreen != "login") {
        when (currentScreen) {
            "adminDashboard", "dashboard" -> showExitDialog = true
            "printPreview" -> currentScreen = printPreviewReturnScreen
            "editEntry" -> {
                entryToEdit = null
                currentScreen = "searchReports"
            }
            else -> {
                currentScreen = if (currentUserRole == "ADMIN") "adminDashboard" else "dashboard"
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Lakshya?") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    (context as? android.app.Activity)?.finishAffinity()
                }) { Text("EXIT") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) { Text("CANCEL") }
            }
        )
    }

    when (currentScreen) {

        "printerSetup" -> {
            PrinterSetupScreen(
                onBack = { currentScreen = if (currentUserRole == "ADMIN") "adminDashboard" else "dashboard" }
            )
        }

        "payment" -> {
            val request = pendingPaymentRequest
            if (request == null) {
                LaunchedEffect(Unit) {
                    currentScreen = "subscription"
                }
            } else {
                PaymentScreen(
                    request = request,
                    paymentId = pendingPaymentId,
                    onBack = {
                        currentScreen =
                            if (cloudSubscriptionData?.isCurrentlyActive() == true)
                                "subscription"
                            else
                                "publicChoosePlan"
                    },
                    onProceed = {
                        Toast.makeText(
                            context,
                            "Payment gateway is not connected yet. Subscription activates only after secure backend verification.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }


        "subscription" -> {

            LaunchedEffect(currentMasterUid) {
                if (currentMasterUid.isNotBlank()) {
                    cloudSubscriptionLoading = true

                    CloudSubscriptionManager.getSubscription(
                        masterUid = currentMasterUid,
                        onSuccess = { data ->
                            cloudSubscriptionData = data
                            cloudSubscriptionLoading = false
                        },
                        onError = { message ->
                            cloudSubscriptionLoading = false
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            if (cloudSubscriptionLoading && cloudSubscriptionData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val data = cloudSubscriptionData

                SubscriptionScreen(
                    isActive = data?.isCurrentlyActive() == true,
                    expiryDate =
                        if (data != null && data.expiryDate > 0L) {
                            SimpleDateFormat(
                                "dd MMM yyyy",
                                Locale.getDefault()
                            ).format(Date(data.expiryDate))
                        } else {
                            "Not Active"
                        },
                    startDate =
                        if (data != null && data.startDate > 0L) {
                            SimpleDateFormat(
                                "dd MMM yyyy",
                                Locale.getDefault()
                            ).format(Date(data.startDate))
                        } else {
                            "Not Active"
                        },

                    daysRemaining =
                        if (data != null && data.isCurrentlyActive()) {
                            val remainingMillis =
                                data.expiryDate - System.currentTimeMillis()

                            val remainingDays =
                                remainingMillis / (24L * 60L * 60L * 1000L)

                            remainingDays.coerceAtLeast(0L)
                        } else {
                            0L
                        },



                    employeeLimit = data?.employeeLimit ?: 5,
                    monthlyPrice = data?.monthlyPrice ?: 5000,

                    onPayRenewClick = {
                        val limit = (data?.employeeLimit ?: 5).coerceIn(5, 10)
                        openPayment(
                            PaymentRequestData(
                                masterUid = currentMasterUid,
                                paymentType = PaymentType.RENEWAL,
                                currentEmployeeLimit = limit,
                                selectedEmployeeLimit = limit,
                                amount = limit * 1000
                            )
                        )
                    },

                    onUpgradeClick = {
                        currentScreen = "planUpgrade"
                    },

                    onBackClick = {
                        if (data?.isCurrentlyActive() == true) {
                            if (currentUserRole == "ADMIN") {
                                currentScreen = "adminDashboard"
                            } else {
                                currentScreen = "dashboard"
                            }
                        } else {
                            com.google.firebase.auth.FirebaseAuth
                                .getInstance()
                                .signOut()

                            currentUserId = ""
                            currentUserRole = ""
                            currentMasterUid = ""
                            cloudSubscriptionData = null
                            currentEmployeePermissions.clear()
                            currentScreen = "login"
                        }
                    }
                )
            }
        }

        "planUpgrade" -> {
            val data = cloudSubscriptionData
            val planIsActive = data?.isCurrentlyActive() == true

            LaunchedEffect(currentMasterUid) {
                if (currentMasterUid.isNotBlank()) {
                    CloudSubscriptionManager.getSubscription(
                        masterUid = currentMasterUid,
                        onSuccess = {
                            cloudSubscriptionData = it
                        },
                        onError = { message ->
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            EmployeePlanUpgradeScreen(
                isPlanActive = planIsActive,
                currentEmployeeLimit = data?.employeeLimit ?: 5,
                currentMonthlyPrice = data?.monthlyPrice ?: 5000,
                onContinuePayment = { selectedLimit, newMonthlyPrice, payNowAmount ->
                    val currentLimit =
                        (data?.employeeLimit ?: 5).coerceIn(5, 10)

                    openPayment(
                        PaymentRequestData(
                            masterUid = currentMasterUid,
                            paymentType =
                                if (planIsActive) PaymentType.EMPLOYEE_UPGRADE
                                else PaymentType.NEW_PLAN,
                            currentEmployeeLimit = currentLimit,
                            selectedEmployeeLimit = selectedLimit,
                            amount = payNowAmount
                        )
                    )
                },
                onBack = {
                    currentScreen = "subscription"
                }
            )
        }


        "welcome" -> {
            PublicWelcomeScreen(
                onWatchDemo = {
                    currentScreen = "publicDemo"
                },
                onCreateAccount = {
                    currentScreen = "publicRegister"
                },
                onLogin = {
                    currentScreen = "login"
                }
            )
        }


        "publicDemo" -> {
            PublicDemoScreen(
                onCreateAccount = {
                    currentScreen = "publicRegister"
                },
                onLogin = {
                    currentScreen = "login"
                },
                onBack = {
                    currentScreen = "welcome"
                }
            )
        }


        "publicRegister" -> {
            PublicCreateAccountScreen(
                onAccountCreated = { masterUid, businessName, ownerName, email ->
                    currentMasterUid = masterUid
                    currentUserId = "ADMIN"
                    currentUserRole = "ADMIN"
                    newCustomerBusinessName = businessName
                    newCustomerOwnerName = ownerName
                    newCustomerEmail = email
                    currentScreen = "publicChoosePlan"
                },
                onLogin = {
                    currentScreen = "login"
                },
                onBack = {
                    currentScreen = "welcome"
                }
            )
        }


        "publicChoosePlan" -> {
            PublicChoosePlanScreen(
                businessName = newCustomerBusinessName,
                onContinuePayment = { selectedLimit, monthlyPrice ->
                    val planData = mapOf<String, Any>(
                        "selectedEmployeeLimit" to selectedLimit,
                        "selectedMonthlyPrice" to monthlyPrice,
                        "accountStatus" to "PAYMENT_PENDING",
                        "updatedAt" to System.currentTimeMillis()
                    )

                    com.google.firebase.firestore.FirebaseFirestore
                        .getInstance()
                        .collection("masters")
                        .document(currentMasterUid)
                        .set(
                            planData,
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .addOnSuccessListener {
                            openPayment(
                                PaymentRequestData(
                                    masterUid = currentMasterUid,
                                    paymentType = PaymentType.NEW_PLAN,
                                    currentEmployeeLimit = 5,
                                    selectedEmployeeLimit = selectedLimit,
                                    amount = monthlyPrice
                                )
                            )
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(
                                context,
                                error.message ?: "Plan save failed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                },
                onBack = {
                    currentScreen = "publicRegister"
                }
            )
        }


        "login" -> {

            LoginScreen(
                database = database,
                onLoginSuccess = { userId, role, masterUid ->
                    currentUserId = userId
                    currentUserRole = role
                    currentMasterUid = masterUid
                    currentEmployeePermissions.clear()

                    if (role == "ADMIN") {
                        currentScreen = "adminDashboard"
                    } else {
                        coroutineScope.launch {
                            EMPLOYEE_FEATURE_OPTIONS.forEach { feature ->
                                currentEmployeePermissions[feature.key] = true
                            }

                            // Use local cache first so login is not blocked by a slow network.
                            database.employeePermissionDao()
                                .getPermissions(userId)
                                .forEach { permission ->
                                    currentEmployeePermissions[permission.featureKey] =
                                        permission.isAllowed
                                }

                            currentScreen = "dashboard"

                            // Real-time permission listener moved to DisposableEffect.
                        }
                    }
                },
                onMasterPlanExpired = { masterUid, subscription ->
                    currentUserId = "ADMIN"
                    currentUserRole = "ADMIN"
                    currentMasterUid = masterUid
                    currentEmployeePermissions.clear()
                    cloudSubscriptionData = subscription
                    currentScreen = "subscription"
                },
                onBack = {
                    currentScreen = "welcome"
                }
            )
        }


        "employeePermissions" -> {
            EmployeePermissionsScreen(
                database = database,
                masterUid = currentMasterUid,
                onBack = {
                    currentScreen = "adminDashboard"
                }
            )
        }


        "oldDayBackup" -> {
            OldDayBackupScreen(
                database = database,
                currentMasterUid = currentMasterUid,
                onBack = {
                    currentScreen = "adminDashboard"
                }
            )
        }


        "editHistory" -> {

            EditHistoryScreen(
                database = database,
                currentMasterUid = currentMasterUid,
                onBack = {
                    currentScreen = "adminDashboard"
                }
            )
        }


        "superMaster" -> {
            SuperMasterScreen(
                onBack = {
                    currentScreen = "adminDashboard"
                }
            )
        }


        "manageEmployees" -> {

            ManageEmployeesScreen(
                database = database,
                savedEntries = savedEntries,
                masterUid = currentMasterUid,
                onUpgradePlan = {
                    currentScreen = "planUpgrade"
                },
                onBack = {
                    currentScreen = "adminDashboard"
                }
            )
        }


        "adminDashboard" -> {

            AdminDashboardScreen(

                savedEntries = todayEntries,
                isSuperMaster =
                    currentMasterUid == LAKSHYA_SUPER_MASTER_UID,
                onSuperMaster = {
                    currentScreen = "superMaster"
                },

                onNewEntry = {
                    printPreviewData = null
                    currentScreen = "newEntry"
                },

                onTodayDashboard = {
                    currentScreen = "todayDashboard"
                },

                onSearchReports = {
                    currentScreen = "searchReports"
                },

                onManageEmployees = {
                    currentScreen = "manageEmployees"
                },

                onEditHistory = {
                    currentScreen = "editHistory"
                },

                onOldDayBackup = {
                    currentScreen = "oldDayBackup"
                },

                onEmployeePermissions = {
                    currentScreen = "employeePermissions"
                },

                onGameWiseLimit = {
                    currentScreen = "gameWiseLimit"
                },

                onResult = {
                    currentScreen = "result"
                },

                onProfitLoss = {
                    currentScreen = "profitLoss"
                },

                onExportExcel = {
                    currentScreen = "exportGame"
                },

                onSubscription = {
                    currentScreen = "subscription"
                },

                onPrinterSetup = {
                    currentScreen = "printerSetup"
                },

                onCloseDay = {
                    showCloseDayDialog = true
                },

                onUndoCloseDay = {
                    coroutineScope.launch {
                        val lastStart =
                            dayArchivePrefs.getLong(
                                "LAST_CLOSED_DAY_START",
                                0L
                            )

                        val lastEnd =
                            dayArchivePrefs.getLong(
                                "LAST_CLOSED_DAY_END",
                                0L
                            )

                        val currentStart =
                            dayArchivePrefs.getLong(
                                "CURRENT_DAY_START",
                                0L
                            )

                        val allBills =
                            database.billDao()
                                .getAllBills(currentMasterUid)

                        val newDayHasEntry =
                            currentStart > 0L &&
                                    allBills.any {
                                        it.savedTime >=
                                                currentStart
                                    }

                        if (
                            lastStart == 0L ||
                            lastEnd == 0L
                        ) {
                            Toast.makeText(
                                context,
                                "UNDO ke liye koi last closed day nahi hai",
                                Toast.LENGTH_LONG
                            ).show()

                        } else if (
                            newDayHasEntry
                        ) {
                            Toast.makeText(
                                context,
                                "UNDO BLOCKED: New day me entry save ho chuki hai",
                                Toast.LENGTH_LONG
                            ).show()

                        } else {

                            // CLOUD UNDO:
                            // Unlock the exact bills that belonged to the last closed day.
                            // This keeps Firebase in sync with the local Room undo.
                            val cloudUndoBills =
                                savedEntries
                                    .filter {
                                        it.savedTime >= lastStart &&
                                                it.savedTime <= lastEnd
                                    }

                            if (
                                currentMasterUid.isNotBlank() &&
                                cloudUndoBills.isNotEmpty()
                            ) {
                                CloudBackendManager.unlockDay(
                                    masterUid = currentMasterUid,
                                    bills = cloudUndoBills
                                ) { _, _ -> }
                            }

                            // Shared cloud DAY UNDO.
                            if (currentMasterUid.isNotBlank()) {
                                CloudAccountSyncManager.undoLastDay(
                                    masterUid = currentMasterUid,
                                    onError = { message ->
                                        Toast.makeText(
                                            context,
                                            "Undo cloud sync: $message",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }

                            // 1. Previous business day becomes CURRENT again.
                            dayArchivePrefs.edit()
                                .putLong(
                                    "CURRENT_DAY_START",
                                    lastStart
                                )
                                .remove(
                                    "LAST_CLOSED_DAY_START"
                                )
                                .remove(
                                    "LAST_CLOSED_DAY_END"
                                )
                                .apply()

                            // 2. Unlock exactly the bills locked by the last CLOSE DAY.
                            // This restores Edit / Cancel behaviour too.
                            kotlinx.coroutines.withContext(
                                kotlinx.coroutines.Dispatchers.IO
                            ) {
                                database.openHelper
                                    .writableDatabase
                                    .execSQL(
                                        """
                                        UPDATE bills
                                        SET isDayLocked = 0,
                                            dayLockedBy = '',
                                            dayLockedTime = NULL
                                        WHERE savedTime >= ?
                                          AND savedTime <= ?
                                        """.trimIndent(),
                                        arrayOf(
                                            lastStart,
                                            lastEnd
                                        )
                                    )
                            }

                            // 3. Restore the live results that CLOSE DAY archived.
                            // This makes Chukara / Profit-Loss work exactly like
                            // they did immediately before CLOSE DAY.
                            val resultPrefs =
                                context.getSharedPreferences(
                                    "lakshya_results_$currentMasterUid",
                                    android.content.Context.MODE_PRIVATE
                                )

                            val resultEditor =
                                resultPrefs.edit()

                            resultGames()
                                .forEach { openGame ->

                                    val archived =
                                        getResultHistory(
                                            resultPrefs,
                                            openGame
                                        )
                                            .filter {
                                                it.savedTime in
                                                        lastStart..
                                                        (lastEnd + 5000L)
                                            }
                                            .maxByOrNull {
                                                it.savedTime
                                            }
                                            ?.result
                                            .orEmpty()
                                            .trim()

                                    if (
                                        archived.isNotBlank()
                                    ) {
                                        resultEditor
                                            .putString(
                                                openGame,
                                                archived
                                            )
                                            .putLong(
                                                "RESULT_TIME_$openGame",
                                                lastEnd
                                            )

                                        val parts =
                                            archived.split("-")

                                        if (
                                            parts.size == 3
                                        ) {
                                            closeGameForOpenGame(
                                                openGame
                                            )?.let {
                                                    closeGame ->

                                                val closeAkda =
                                                    digitTotalLastDigit(
                                                        parts[2]
                                                    )

                                                resultEditor
                                                    .putString(
                                                        closeGame,
                                                        closeAkda
                                                    )
                                                    .putLong(
                                                        "RESULT_TIME_$closeGame",
                                                        lastEnd
                                                    )
                                            }
                                        }
                                    }
                                }

                            resultEditor.apply()

                            // 4. Reload Room after unlock so every screen receives
                            // the restored current-day objects.
                            val restoredBills =
                                database.billDao()
                                    .getAllBills(currentMasterUid)

                            savedEntries.clear()
                            savedEntries.addAll(
                                restoredBills.map {
                                        bill ->

                                    SavedEntry(
                                        id =
                                            bill.id,
                                        customerName =
                                            bill.customerName,
                                        games =
                                            deserializeGames(
                                                bill.games
                                            ),
                                        entries =
                                            deserializeEntries(
                                                bill.entries
                                            ),
                                        perGameTotal =
                                            bill.perGameTotal,
                                        grandTotal =
                                            bill.grandTotal,
                                        savedTime =
                                            bill.savedTime,
                                        status =
                                            bill.status,
                                        createdBy =
                                            bill.createdBy,
                                        cancelledBy =
                                            bill.cancelledBy,
                                        cancelledTime =
                                            bill.cancelledTime,
                                        isEdited =
                                            bill.isEdited,
                                        lastEditedBy =
                                            bill.lastEditedBy,
                                        lastEditedTime =
                                            bill.lastEditedTime,
                                        isPrinted =
                                            bill.isPrinted,
                                        printedBy =
                                            bill.printedBy,
                                        printedTime =
                                            bill.printedTime,
                                        isDayLocked =
                                            bill.isDayLocked,
                                        dayLockedBy =
                                            bill.dayLockedBy,
                                        dayLockedTime =
                                            bill.dayLockedTime
                                    )
                                }
                            )

                            // 5. Force all current-day calculations/screens to
                            // re-read the restored CURRENT_DAY_START.
                            businessDayStartRefresh++

                            Toast.makeText(
                                context,
                                "UNDO COMPLETE: Pura previous day current day me restore ho gaya",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },

                onLogout = {
                    currentUserId = ""
                    currentUserRole = ""
                    currentEmployeePermissions.clear()
                    currentScreen = "login"
                }
            )
        }


        "dashboard" -> {

            DashboardScreen(

                savedEntries = todayEntries.filter {
                    it.status == "ACTIVE"
                },

                permissions = currentEmployeePermissions,

                currentUserId = currentUserId,

                onNewEntry = {
                    printPreviewData = null
                    currentScreen = "newEntry"
                },

                onTodayDashboard = {
                    currentScreen = "todayDashboard"
                },

                onSearchReports = {
                    currentScreen = "searchReports"
                },

                onGameWiseList = {
                    currentScreen = "gameWiseLimit"
                },

                onChukara = {
                    currentScreen = "searchReports"
                },

                onResult = {
                    currentScreen = "result"
                },

                onProfitLoss = {
                    currentScreen = "profitLoss"
                },

                onExportExcel = {
                    currentScreen = "exportGame"
                },

                onPrinterSetup = {
                    currentScreen = "printerSetup"
                },

                onLogout = {
                    currentUserId = ""
                    currentUserRole = ""
                    currentScreen = "login"
                }
            )
        }


        "exportGame" -> {
            var exportingGame by remember {
                mutableStateOf<String?>(null)
            }

            ExportGameSelectionScreen(
                exportingGame = exportingGame,
                onBack = {
                    if (exportingGame == null) {
                        currentScreen =
                            if (currentUserRole == "ADMIN") {
                                "adminDashboard"
                            } else {
                                "dashboard"
                            }
                    }
                },
                onGameSelected = { selectedGame ->
                    if (exportingGame == null) {
                        exportingGame = selectedGame

                        coroutineScope.launch {
                            // Give Compose time to draw the DOWNLOADING state.
                            kotlinx.coroutines.delay(250)

                            try {
                                val fileName = exportLimitExcel(
                                    context = context,
                                    savedEntries = todayEntries,
                                    selectedGame = selectedGame
                                )

                                Toast.makeText(
                                    context,
                                    "$selectedGame Excel Download Complete: $fileName",
                                    Toast.LENGTH_LONG
                                ).show()

                                exportingGame = null
                                currentScreen =
                                    if (currentUserRole == "ADMIN") {
                                        "adminDashboard"
                                    } else {
                                        "dashboard"
                                    }

                            } catch (e: Exception) {
                                exportingGame = null

                                Toast.makeText(
                                    context,
                                    "Excel Export Failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }


        "newEntry" -> {

            NewEntryScreen(

                savedEntries = savedEntries,

                database = database,

                currentUserId = currentUserId,

                currentMasterUid = currentMasterUid,

                initialSavedData = printPreviewData,

                onPrintPreview = { data ->

                    printPreviewData = data
                    printPreviewReturnScreen = "newEntry"

                    currentScreen = "printPreview"
                },

                onBack = {
                    printPreviewData = null
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "gameWiseLimit" -> {

            GameWiseLimitScreen(
                savedEntries = todayEntries,
                currentMasterUid = currentMasterUid,
                onBack = {
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "result" -> {

            ResultScreen(
                currentUserRole = currentUserRole,
                currentMasterUid = currentMasterUid,
                permissions = currentEmployeePermissions,
                onBack = {
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "profitLoss" -> {

            ProfitLossScreen(
                savedEntries = todayEntries.filter {
                    it.status == "ACTIVE"
                },
                currentMasterUid = currentMasterUid,
                onBack = {
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "todayDashboard" -> {

            TodayDashboardScreen(

                savedEntries = todayEntries.filter {
                    it.status == "ACTIVE"
                },

                onBack = {
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "editEntry" -> {

            val selectedEntry =
                entryToEdit

            if (selectedEntry != null) {

                EditEntryScreen(
                    entry = selectedEntry,
                    database = database,
                    currentUserId = currentUserId,
                    currentMasterUid = currentMasterUid,
                    savedEntries = savedEntries,
                    onUpdated = { updatedEntry ->
                        // Update ke baad History Edit screen par hi rahenge.
                        // Isi latest saved copy se PRINT niklega.
                        entryToEdit = updatedEntry
                    },
                    onPrintPreview = { data ->
                        printPreviewData = data
                        printPreviewReturnScreen = "editEntry"
                        currentScreen = "printPreview"
                    },
                    onBack = {
                        entryToEdit = null
                        currentScreen = "searchReports"
                    }
                )

            } else {

                LaunchedEffect(Unit) {
                    currentScreen = "searchReports"
                }
            }
        }


        "searchReports" -> {

            SearchReportsScreen(

                savedEntries = savedEntries,

                currentBusinessDayStart = currentBusinessDayStart(),

                database = database,

                currentUserId = currentUserId,

                currentUserRole = currentUserRole,

                currentMasterUid = currentMasterUid,

                permissions = currentEmployeePermissions,

                onEditEntry = { entry ->
                    if (entry.isDayLocked) {
                        Toast.makeText(
                            context,
                            "DAY CLOSED: This entry is permanently locked",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        entryToEdit = entry
                        currentScreen = "editEntry"
                    }
                },

                onBack = {
                    currentScreen =
                        if (currentUserRole == "ADMIN") {
                            "adminDashboard"
                        } else {
                            "dashboard"
                        }
                }
            )
        }


        "printPreview" -> {

            val data =
                printPreviewData


            if (data != null) {

                PrintPreviewScreen(

                    data = data,

                    database = database,

                    currentUserId = currentUserId,

                    currentMasterUid = currentMasterUid,

                    onPrinted = { billId, printedBy, printedTime ->
                        val index = savedEntries.indexOfFirst { it.id == billId }
                        if (index >= 0) {
                            savedEntries[index] = savedEntries[index].copy(
                                isPrinted = true,
                                printedBy = printedBy,
                                printedTime = printedTime
                            )
                        }
                        entryToEdit = entryToEdit?.takeIf { it.id != billId } ?: entryToEdit?.copy(
                            isPrinted = true,
                            printedBy = printedBy,
                            printedTime = printedTime
                        )

                        // A receipt printed from New Entry starts a fresh form
                        // immediately, ready for the next customer's bill.
                        // Edit Entry keeps its existing return behaviour.
                        if (printPreviewReturnScreen == "newEntry") {
                            printPreviewData = null
                            currentScreen = "newEntry"
                        }
                    },

                    onBack = {
                        currentScreen = printPreviewReturnScreen
                    }

                )

            } else {

                currentScreen = "newEntry"
            }
        }
    }

    if (showCloseDayDialog) {
        AlertDialog(
            onDismissRequest = {
                showCloseDayDialog = false
            },
            title = {
                Text("CLOSE TODAY?")
            },
            text = {
                Text(
                    "Aaj ki sabhi entries permanently DAY LOCK ho jayengi. " +
                            "Locked entries edit/cancel nahi hongi."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseDayDialog = false

                        coroutineScope.launch {
                            val lockTime =
                                System.currentTimeMillis()

                            val start =
                                currentBusinessDayStart()

                            val end =
                                lockTime + 1L

                            val lockedCount =
                                database.billDao().lockDayBills(
                                    masterUid = currentMasterUid,
                                    startTime = start,
                                    endTime = end,
                                    lockedBy = currentUserId.ifBlank { "ADMIN" },
                                    lockedTime = lockTime
                                )

                            val cloudDayBills = savedEntries.filter { it.savedTime >= start && it.savedTime <= end }
                            if (currentMasterUid.isNotBlank()) {
                                CloudBackendManager.lockDay(
                                    masterUid = currentMasterUid,
                                    bills = cloudDayBills,
                                    lockedBy = currentUserId.ifBlank { "ADMIN" },
                                    lockedTime = lockTime
                                ) { _, _ -> }
                            }

                            // DAY CLOSE also closes today's RESULT cycle.
                            // Current live results go to history and then
                            // the live Result screen becomes blank for next day.
                            val resultPrefs =
                                context.getSharedPreferences(
                                    "lakshya_results_$currentMasterUid",
                                    android.content.Context.MODE_PRIVATE
                                )

                            val liveResultsBeforeClose =
                                resultGames().associateWith { game ->
                                    resultPrefs.getString(game, "").orEmpty()
                                }.filterValues { it.isNotBlank() }

                            val liveResultTimesBeforeClose =
                                resultGames().associateWith { game ->
                                    resultPrefs.getLong("RESULT_TIME_$game", 0L)
                                }.filterValues { it > 0L }

                            archiveAndClearCurrentResults(
                                resultPrefs
                            )

                            // Shared account DAY CLOSE state.
                            // Same masterUid on ADMIN / MASTER / EMPLOYEE / another phone
                            // receives this state through Firebase.
                            if (currentMasterUid.isNotBlank()) {
                                CloudAccountSyncManager.closeDay(
                                    masterUid = currentMasterUid,
                                    dayStart = start,
                                    dayEnd = lockTime,
                                    nextDayStart = lockTime + 1L,
                                    closedBy = currentUserId.ifBlank { "ADMIN" },
                                    liveResults = liveResultsBeforeClose,
                                    liveResultTimes = liveResultTimesBeforeClose,
                                    onError = { message ->
                                        Toast.makeText(
                                            context,
                                            "Day state cloud sync: $message",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }

                            // Save explicit day-close archive marker.
                            // Bills are NOT deleted; old dates remain available
                            // for History / Game List / P&L / Excel re-open.
                            dayArchivePrefs.edit()
                                .putLong(
                                    "LAST_CLOSED_DAY_START",
                                    start
                                )
                                .putLong(
                                    "LAST_CLOSED_DAY_END",
                                    lockTime
                                )
                                // IMPORTANT:
                                // CLOSE DAY means old business day is finished
                                // immediately, even if calendar date is same.
                                .putLong(
                                    "CURRENT_DAY_START",
                                    lockTime + 1L
                                )
                                .apply()

                            businessDayStartRefresh++

                            // CLOSE DAY = start a completely fresh working day.
                            // Old bills stay safely in Room database as backup,
                            // but disappear from current-day screens immediately.
                            savedEntries.clear()

                            Toast.makeText(
                                context,
                                if (lockedCount > 0) {
                                    "DAY CLOSED: $lockedCount entries locked"
                                } else {
                                    "Aaj ki entries pehle se locked hain ya koi entry nahi hai"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("YES, CLOSE DAY")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCloseDayDialog = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

}


// =====================================================
// EXPORT GAME SELECTION
// =====================================================

@Composable
fun ExportGameSelectionScreen(
    exportingGame: String?,
    onBack: () -> Unit,
    onGameSelected: (String) -> Unit
) {
    val navy = Color(0xFF071426)
    val gold = Color(0xFFD4AF37)
    val lightGold = Color(0xFFFFD86B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(navy)
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Text(
            text = "EXPORT TO EXCEL",
            color = lightGold,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select the game you want to download",
            color = Color(0xFFAAB6C5),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        gameList.chunked(2).forEach { rowGames ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowGames.forEach { game ->
                    Button(
                        onClick = {
                            onGameSelected(game)
                        },
                        enabled = exportingGame == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF132844),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gold)
                    ) {
                        Text(
                            text = game,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (rowGames.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (exportingGame != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF132844)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = gold,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "DOWNLOADING ${exportingGame} EXCEL...",
                            color = lightGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Please wait. File is being saved to your phone.",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("BACK")
        }
    }
}


// =====================================================
// LOGIN SCREEN
// =====================================================

// =====================================================
// PUBLIC / PLAY STORE ONBOARDING
// =====================================================

@Composable
fun PaymentScreen(
    request: PaymentRequestData,
    paymentId: String,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    BackHandler { onBack() }

    val title = when (request.paymentType) {
        PaymentType.NEW_PLAN -> "New Subscription"
        PaymentType.RENEWAL -> "Renew Subscription"
        PaymentType.EMPLOYEE_UPGRADE -> "Employee Limit Upgrade"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FB))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text(
                "PAYMENT",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF163A5F)
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(60.dp))
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163A5F)
                )
                Spacer(Modifier.height(18.dp))
                Text("AMOUNT TO PAY", fontSize = 12.sp, color = Color.Gray)
                Text(
                    "₹${request.amount}",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF163A5F)
                )
                Spacer(Modifier.height(16.dp))
                PaymentDetailRow("Employee Limit", request.selectedEmployeeLimit.toString())

                if (request.paymentType == PaymentType.EMPLOYEE_UPGRADE) {
                    PaymentDetailRow(
                        "Upgrade",
                        "${request.currentEmployeeLimit} → ${request.selectedEmployeeLimit}"
                    )
                    PaymentDetailRow("Rate", "₹1000 per added employee")
                }

                if (paymentId.isNotBlank()) {
                    PaymentDetailRow("Request ID", paymentId)
                }
                PaymentDetailRow("Status", "PENDING")
            }
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
        ) {
            Text(
                "Payment request is saved in cloud, but subscription is not activated until the payment is securely verified.",
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                "PROCEED TO PAY ₹${request.amount}",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PaymentDetailRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color.Gray,
            fontSize = 13.sp
        )
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163A5F),
            fontSize = 13.sp
        )
    }
}


@Composable
fun PublicWelcomeScreen(
    onWatchDemo: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit
) {
    BackHandler {
        // Welcome is the public root. Do not reveal stale protected screens.
    }

    val background = Color(0xFF07111F)
    val surface = Color(0xFF0D1B2D)
    val gold = Color(0xFFE0B84C)
    val muted = Color(0xFF9EADBF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF06101D),
                        Color(0xFF0A1A2E),
                        background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(70.dp))

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFFE07A),
                            Color(0xFFE0B84C),
                            Color(0xFFB8891D)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "L",
                color = Color(0xFF071426),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "LAKSHYA",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Text(
            "BUSINESS MANAGEMENT",
            color = gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Manage entries, employees, reports, results and business performance from one place.",
            color = muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(34.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surface)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "See Lakshya in action",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Watch the demo first, then create your account and choose the plan that fits your team.",
                    color = muted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onWatchDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = gold
                    )
                ) {
                    Text("WATCH DEMO", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gold,
                        contentColor = Color(0xFF071426)
                    )
                ) {
                    Text("CREATE ACCOUNT", fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Already have an account?  LOGIN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}


@Composable
fun PublicDemoScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val primary = Color(0xFF163A5F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA))
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "WATCH LAKSHYA DEMO",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = primary
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "See the main workflow before choosing a plan.",
            color = Color.Gray
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0D1B2D)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶", color = Color(0xFFE0B84C), fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "DEMO VIDEO",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Video link will be connected before Play Store release",
                            color = Color(0xFF9EADBF),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                listOf(
                    "New Entry & Print workflow",
                    "Today Dashboard",
                    "Employee IDs & Permissions",
                    "Search & Reports",
                    "Result, Chukara & Profit/Loss",
                    "Plan renewal & employee upgrades"
                ).forEach { feature ->
                    Text(
                        "•  $feature",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        color = primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onCreateAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already registered? LOGIN")
        }

        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun PublicCreateAccountScreen(
    onAccountCreated: (String, String, String, String) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current

    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val primary = Color(0xFF163A5F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA))
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        TextButton(
            onClick = onBack,
            enabled = !loading
        ) {
            Text("← Back")
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "CREATE YOUR ACCOUNT",
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            color = primary
        )

        Text(
            "This will be your Lakshya Master/Admin account.",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business / Customer Name") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Owner Name") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { value ->
                        mobile = value.filter { it.isDigit() }.take(10)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mobile Number") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email / Login ID") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Create Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val cleanBusiness = businessName.trim()
                val cleanOwner = ownerName.trim()
                val cleanMobile = mobile.trim()
                val cleanEmail = email.trim().lowercase()
                val cleanPassword = password.trim()

                when {
                    cleanBusiness.isBlank() -> {
                        Toast.makeText(context, "Enter business name", Toast.LENGTH_SHORT).show()
                    }
                    cleanOwner.isBlank() -> {
                        Toast.makeText(context, "Enter owner name", Toast.LENGTH_SHORT).show()
                    }
                    cleanMobile.length != 10 -> {
                        Toast.makeText(context, "Enter valid 10 digit mobile number", Toast.LENGTH_SHORT).show()
                    }
                    !cleanEmail.contains("@") -> {
                        Toast.makeText(context, "Enter valid email address", Toast.LENGTH_SHORT).show()
                    }
                    EmployeeAuthManager.strongPasswordError(cleanPassword) != null -> {
                        Toast.makeText(
                            context,
                            EmployeeAuthManager.strongPasswordError(cleanPassword) ?: "Invalid password",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    cleanPassword != confirmPassword.trim() -> {
                        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        loading = true

                        val auth =
                            com.google.firebase.auth.FirebaseAuth.getInstance()

                        auth.createUserWithEmailAndPassword(
                            cleanEmail,
                            cleanPassword
                        )
                            .addOnSuccessListener { result ->
                                val masterUid = result.user?.uid.orEmpty()

                                if (masterUid.isBlank()) {
                                    loading = false
                                    Toast.makeText(
                                        context,
                                        "Account UID could not be created",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@addOnSuccessListener
                                }

                                val masterData = hashMapOf<String, Any>(
                                    "businessName" to cleanBusiness,
                                    "ownerName" to cleanOwner,
                                    "mobile" to cleanMobile,
                                    "email" to cleanEmail,
                                    "role" to "ADMIN",
                                    "accountStatus" to "PLAN_PENDING",
                                    "isActive" to false,
                                    "active" to false,
                                    "adminStatus" to "INACTIVE",
                                    "accessEnabled" to false,
                                    "createdAt" to System.currentTimeMillis(),
                                    "updatedAt" to System.currentTimeMillis()
                                )

                                com.google.firebase.firestore.FirebaseFirestore
                                    .getInstance()
                                    .collection("masters")
                                    .document(masterUid)
                                    .set(masterData)
                                    .addOnSuccessListener {
                                        loading = false

                                        Toast.makeText(
                                            context,
                                            "Account created. Now choose your plan.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        onAccountCreated(
                                            masterUid,
                                            cleanBusiness,
                                            cleanOwner,
                                            cleanEmail
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        loading = false
                                        auth.signOut()

                                        Toast.makeText(
                                            context,
                                            error.message ?: "Master profile creation failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .addOnFailureListener { error ->
                                loading = false
                                Toast.makeText(
                                    context,
                                    error.message ?: "Account creation failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        TextButton(
            onClick = onLogin,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already have an account? LOGIN")
        }

        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun PublicChoosePlanScreen(
    businessName: String,
    onContinuePayment: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val baseLimit = 5
    val maxLimit = 10
    val basePrice = 5000
    val perEmployee = 1000

    var selectedLimit by remember {
        mutableIntStateOf(baseLimit)
    }

    val monthlyPrice =
        basePrice + ((selectedLimit - baseLimit) * perEmployee)

    val primary = Color(0xFF163A5F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA))
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "CHOOSE YOUR PLAN",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = primary
        )

        if (businessName.isNotBlank()) {
            Text(
                businessName,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "MONTHLY PLAN",
                    color = primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (selectedLimit > baseLimit) selectedLimit--
                        },
                        enabled = selectedLimit > baseLimit
                    ) {
                        Text("−", fontSize = 24.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$selectedLimit",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = primary
                        )
                        Text("Employees")
                    }

                    Button(
                        onClick = {
                            if (selectedLimit < maxLimit) selectedLimit++
                        },
                        enabled = selectedLimit < maxLimit
                    ) {
                        Text("+", fontSize = 24.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "₹$monthlyPrice",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = primary
                )

                Text(
                    "per month",
                    color = Color.Gray
                )

                Spacer(Modifier.height(18.dp))

                HorizontalDivider()

                Spacer(Modifier.height(18.dp))

                Text(
                    "5 Employees = ₹5,000/month",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "+1 Employee = ₹1,000/month",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                )
                Text(
                    "Maximum = 10 Employees",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = primary.copy(alpha = 0.07f)
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "PAY NOW",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "₹$monthlyPrice",
                    color = primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Your 1-month subscription will start after successful payment.",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onContinuePayment(selectedLimit, monthlyPrice)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "CONTINUE TO PAYMENT • ₹$monthlyPrice",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Payment verification will be connected before the Play Store release.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(30.dp))
    }
}


// =====================================================
// LOGIN
// =====================================================

@Composable
fun LoginScreen(
    database: AppDatabase,
    onLoginSuccess: (String, String, String) -> Unit,
    onMasterPlanExpired: (String, CloudSubscriptionData) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showCreateAccount by remember { mutableStateOf(false) }
    var showForgotId by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    val background = Color(0xFF07111F)
    val surface = Color(0xFF0D1B2D)
    val field = Color(0xFF12243A)
    val gold = Color(0xFFE0B84C)
    val softGold = Color(0xFFFFDF7A)
    val muted = Color(0xFF9EADBF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF06101D),
                        Color(0xFF0A1A2E),
                        background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(68.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFFE07A),
                                Color(0xFFE0B84C),
                                Color(0xFFB8891D)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    color = Color(0xFF071426),
                    fontSize = 47.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "LAKSHYA",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "BUSINESS MANAGEMENT",
                color = gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Manage smarter. Work faster.",
                color = muted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        text = "Welcome Back",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sign in to your Lakshya account",
                        color = muted,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = loginId,
                        onValueChange = { loginId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Master Email / Employee User ID") },
                        placeholder = { Text("Enter Master email or Employee User ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color(0xFF30465F),
                            focusedLabelColor = softGold,
                            unfocusedLabelColor = muted,
                            cursorColor = gold,
                            focusedContainerColor = field,
                            unfocusedContainerColor = field
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        singleLine = true,
                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    if (passwordVisible) "HIDE" else "SHOW",
                                    color = softGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color(0xFF30465F),
                            focusedLabelColor = softGold,
                            unfocusedLabelColor = muted,
                            cursorColor = gold,
                            focusedContainerColor = field,
                            unfocusedContainerColor = field
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showForgotId = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Forgot User ID?", color = softGold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { showForgotPassword = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Forgot Password?", color = softGold, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (loginId.isBlank() || password.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Please enter Email / User ID and Password",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val cleanLogin = loginId.trim()
                                val cleanPassword = password.trim()

                                // MASTER ADMIN: Firebase email + password login
                                if (cleanLogin.contains("@")) {
                                    FirebaseManager.loginMaster(
                                        email = cleanLogin,
                                        password = cleanPassword,
                                        onSuccess = {
                                            val masterUid =
                                                com.google.firebase.auth.FirebaseAuth
                                                    .getInstance()
                                                    .currentUser
                                                    ?.uid
                                                    .orEmpty()

                                            MasterAccessManager.verifyMasterAccess(
                                                masterUid = masterUid,
                                                onAllowed = { cloudSubscription ->
                                                    Toast.makeText(
                                                        context,
                                                        "Master Admin Login Successful",
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    onLoginSuccess(
                                                        "ADMIN",
                                                        "ADMIN",
                                                        masterUid
                                                    )
                                                },
                                                onBlocked = { cloudSubscription ->
                                                    if (
                                                        cloudSubscription != null &&
                                                        cloudSubscription.expiryDate > 0L &&
                                                        cloudSubscription.expiryDate <=
                                                        System.currentTimeMillis()
                                                    ) {
                                                        Toast.makeText(
                                                            context,
                                                            "Your plan has expired. Please renew your plan.",
                                                            Toast.LENGTH_LONG
                                                        ).show()

                                                        onMasterPlanExpired(
                                                            masterUid,
                                                            cloudSubscription
                                                        )
                                                    } else {
                                                        com.google.firebase.auth.FirebaseAuth
                                                            .getInstance()
                                                            .signOut()

                                                        Toast.makeText(
                                                            context,
                                                            "Master/Admin ID or plan is inactive. Contact Super Master.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                },
                                                onError = { message ->
                                                    com.google.firebase.auth.FirebaseAuth
                                                        .getInstance()
                                                        .signOut()

                                                    Toast.makeText(
                                                        context,
                                                        message,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            )
                                        },
                                        onError = { message ->
                                            Toast.makeText(
                                                context,
                                                message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                } else {
                                    // EMPLOYEE: Firebase cloud login.
                                    // User enters only Employee User ID + Password.
                                    val cleanUserId = cleanLogin.uppercase()
                                    val employeeEmail =
                                        EmployeeAuthManager.employeeEmail(cleanUserId)

                                    com.google.firebase.auth.FirebaseAuth
                                        .getInstance()
                                        .signInWithEmailAndPassword(
                                            employeeEmail,
                                            cleanPassword
                                        )
                                        .addOnSuccessListener { authResult ->

                                            val employeeUid =
                                                authResult.user?.uid

                                            if (employeeUid.isNullOrBlank()) {
                                                com.google.firebase.auth.FirebaseAuth
                                                    .getInstance()
                                                    .signOut()

                                                Toast.makeText(
                                                    context,
                                                    "Employee UID not found",
                                                    Toast.LENGTH_LONG
                                                ).show()

                                                return@addOnSuccessListener
                                            }

                                            // Employee lookup: signed-in employee can read ONLY
                                            // employee_lookup/{own Firebase Auth UID}.
                                            val firestore =
                                                com.google.firebase.firestore.FirebaseFirestore
                                                    .getInstance()

                                            firestore
                                                .collection("employee_lookup")
                                                .document(employeeUid)
                                                .get()
                                                .addOnSuccessListener { lookupDocument ->

                                                    if (!lookupDocument.exists()) {
                                                        com.google.firebase.auth.FirebaseAuth
                                                            .getInstance()
                                                            .signOut()

                                                        Toast.makeText(
                                                            context,
                                                            "Employee lookup not found",
                                                            Toast.LENGTH_LONG
                                                        ).show()

                                                        return@addOnSuccessListener
                                                    }

                                                    val masterUid =
                                                        lookupDocument
                                                            .getString("masterUid")
                                                            ?.trim()
                                                            .orEmpty()

                                                    if (masterUid.isBlank()) {
                                                        com.google.firebase.auth.FirebaseAuth
                                                            .getInstance()
                                                            .signOut()

                                                        Toast.makeText(
                                                            context,
                                                            "Master UID not found for employee",
                                                            Toast.LENGTH_LONG
                                                        ).show()

                                                        return@addOnSuccessListener
                                                    }

                                                    // This is the employee's own real-time-access record.
                                                    // Do not proceed even if a stale local session exists.
                                                    val lookupIsActive =
                                                        lookupDocument.getBoolean("isActive")
                                                            ?: lookupDocument.getBoolean("active")
                                                            ?: false
                                                    if (!lookupIsActive) {
                                                        com.google.firebase.auth.FirebaseAuth
                                                            .getInstance()
                                                            .signOut()

                                                        Toast.makeText(
                                                            context,
                                                            "Your Employee account is inactive. Contact Master Admin.",
                                                            Toast.LENGTH_LONG
                                                        ).show()

                                                        return@addOnSuccessListener
                                                    }

                                                    // =====================================================
                                                    // EMPLOYEE LOGIN SECURITY
                                                    // Master/Admin + subscription must be ACTIVE first.
                                                    // =====================================================
                                                    MasterAccessManager.verifyMasterAccess(
                                                        masterUid = masterUid,

                                                        onAllowed = { _ ->

                                                            // Master and subscription are ACTIVE.
                                                            // Now employee profile can be checked.
                                                            firestore
                                                                .collection("masters")
                                                                .document(masterUid)
                                                                .collection("employees")
                                                                .document(employeeUid)
                                                                .get()
                                                                .addOnSuccessListener { employeeDocument ->

                                                                    if (!employeeDocument.exists()) {
                                                                        com.google.firebase.auth.FirebaseAuth
                                                                            .getInstance()
                                                                            .signOut()

                                                                        Toast.makeText(
                                                                            context,
                                                                            "Employee cloud profile not found",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()

                                                                        return@addOnSuccessListener
                                                                    }

                                                                    val cloudEmployee =
                                                                        employeeDocument.toObject(
                                                                            CloudEmployee::class.java
                                                                        )

                                                                    if (cloudEmployee == null) {
                                                                        com.google.firebase.auth.FirebaseAuth
                                                                            .getInstance()
                                                                            .signOut()

                                                                        Toast.makeText(
                                                                            context,
                                                                            "Employee profile could not be read",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()

                                                                        return@addOnSuccessListener
                                                                    }

                                                                    // Read the raw Firestore value too, so access is
                                                                    // denied even if model mapping/defaults change.
                                                                    val employeeIsActive =
                                                                        employeeDocument.getBoolean("isActive")
                                                                            ?: employeeDocument.getBoolean("active")
                                                                            ?: false
                                                                    if (!employeeIsActive) {
                                                                        com.google.firebase.auth.FirebaseAuth
                                                                            .getInstance()
                                                                            .signOut()

                                                                        Toast.makeText(
                                                                            context,
                                                                            "Your Employee account is inactive. Contact Master Admin.",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()

                                                                        return@addOnSuccessListener
                                                                    }

                                                                    if (
                                                                        !cloudEmployee.userId.equals(
                                                                            cleanUserId,
                                                                            ignoreCase = true
                                                                        )
                                                                    ) {
                                                                        com.google.firebase.auth.FirebaseAuth
                                                                            .getInstance()
                                                                            .signOut()

                                                                        Toast.makeText(
                                                                            context,
                                                                            "Employee account verification failed",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()

                                                                        return@addOnSuccessListener
                                                                    }

                                                                    // Login success only after ALL checks pass.
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Employee Login Successful",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()

                                                                    onLoginSuccess(
                                                                        cloudEmployee.userId,
                                                                        "EMPLOYEE",
                                                                        masterUid
                                                                    )
                                                                }
                                                                .addOnFailureListener { error ->
                                                                    com.google.firebase.auth.FirebaseAuth
                                                                        .getInstance()
                                                                        .signOut()

                                                                    Toast.makeText(
                                                                        context,
                                                                        error.message
                                                                            ?: "Employee profile read failed",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                }
                                                        },

                                                        onBlocked = {
                                                            com.google.firebase.auth.FirebaseAuth
                                                                .getInstance()
                                                                .signOut()

                                                            Toast.makeText(
                                                                context,
                                                                "Master subscription is inactive. Please contact your Master Admin.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        },

                                                        onError = { message ->
                                                            com.google.firebase.auth.FirebaseAuth
                                                                .getInstance()
                                                                .signOut()

                                                            Toast.makeText(
                                                                context,
                                                                "Unable to verify Master access: $message",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    )
                                                }
                                                .addOnFailureListener { error ->
                                                    com.google.firebase.auth.FirebaseAuth
                                                        .getInstance()
                                                        .signOut()

                                                    Toast.makeText(
                                                        context,
                                                        error.message
                                                            ?: "Employee lookup failed",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                        }
                                        .addOnFailureListener {

                                            Toast.makeText(
                                                context,
                                                "Invalid User ID or Password",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = gold,
                            contentColor = Color(0xFF071426)
                        )
                    ) {
                        Text(
                            text = "SIGN IN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = Color(0xFF263B53))
                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "New to Lakshya?",
                        modifier = Modifier.fillMaxWidth(),
                        color = muted,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(7.dp))

                    OutlinedButton(
                        onClick = { showCreateAccount = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = softGold
                        )
                    ) {
                        Text(
                            text = "CREATE NEW ACCOUNT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Secure • Simple • Professional",
                color = Color(0xFF71849B),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "LAKSHYA  •  VERSION 1.0",
                color = Color(0xFF52667D),
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showCreateAccount) {
        var fullName by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateAccount = false },
            title = {
                Text("Create New Account", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Enter your details to start registration.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full Name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = {
                            if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                                mobile = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Create Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Your registered mobile number will be your User ID. " +
                                "Use it with the password you create below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            fullName.isBlank() ->
                                Toast.makeText(
                                    context, "Enter full name", Toast.LENGTH_SHORT
                                ).show()

                            mobile.length != 10 ->
                                Toast.makeText(
                                    context,
                                    "Enter valid 10 digit mobile number",
                                    Toast.LENGTH_SHORT
                                ).show()

                            EmployeeAuthManager.strongPasswordError(newPassword.trim()) != null ->
                                Toast.makeText(
                                    context,
                                    EmployeeAuthManager.strongPasswordError(newPassword.trim()) ?: "Invalid password",
                                    Toast.LENGTH_SHORT
                                ).show()

                            newPassword != confirmPassword ->
                                Toast.makeText(
                                    context,
                                    "Passwords do not match",
                                    Toast.LENGTH_SHORT
                                ).show()

                            else -> {
                                val cleanName = fullName.trim()
                                val cleanMobile = mobile.trim()
                                val cleanPassword = newPassword.trim()

                                coroutineScope.launch {
                                    val exists = database.employeeDao().userIdExists(cleanMobile)

                                    if (exists > 0) {
                                        Toast.makeText(
                                            context,
                                            "This mobile number is already registered",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        database.employeeDao().insertEmployee(
                                            EmployeeEntity(
                                                employeeName = cleanName,
                                                userId = cleanMobile,
                                                role = "EMPLOYEE",
                                                isActive = true
                                            )
                                        )

                                        showCreateAccount = false
                                        loginId = cleanMobile
                                        password = ""

                                        Toast.makeText(
                                            context,
                                            "Account created. Your User ID is $cleanMobile",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("CONTINUE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAccount = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showForgotId) {
        var mobile by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showForgotId = false },
            title = { Text("Forgot User ID?") },
            text = {
                Column {
                    Text("Enter your registered mobile number.")
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = {
                            if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                                mobile = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Registered Mobile Number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mobile.length != 10) {
                            Toast.makeText(
                                context,
                                "Enter valid mobile number",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "User ID recovery will work after mobile verification is connected.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("FIND USER ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotId = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showForgotPassword) {

        var recoveryMethod by remember {
            mutableStateOf("EMAIL")
        }

        var resetEmail by remember {
            mutableStateOf("")
        }

        var resetMobile by remember {
            mutableStateOf("")
        }

        var otpCode by remember {
            mutableStateOf("")
        }

        var resetLoading by remember {
            mutableStateOf(false)
        }

        AlertDialog(
            onDismissRequest = {
                if (!resetLoading) {
                    showForgotPassword = false
                }
            },

            title = {
                Text(
                    "Forgot ID / Password",
                    fontWeight = FontWeight.Bold
                )
            },

            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        "Choose recovery method",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF163A5F)
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        if (recoveryMethod == "EMAIL") {
                            Button(
                                onClick = {
                                    recoveryMethod = "EMAIL"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("EMAIL")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    recoveryMethod = "EMAIL"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("EMAIL")
                            }
                        }

                        if (recoveryMethod == "MOBILE") {
                            Button(
                                onClick = {
                                    recoveryMethod = "MOBILE"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("MOBILE OTP")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    recoveryMethod = "MOBILE"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("MOBILE OTP")
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    if (recoveryMethod == "EMAIL") {

                        Text(
                            "EMAIL RECOVERY",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF163A5F)
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "Your registered email is your Master/Admin Login ID. Enter it below to receive a secure password reset link.",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = {
                                resetEmail = it.trim()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Registered Email")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            singleLine = true,
                            enabled = !resetLoading
                        )

                    } else {

                        Text(
                            "MOBILE OTP RECOVERY",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF163A5F)
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "Enter the mobile number registered with your Lakshya Master/Admin account.",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = resetMobile,
                            onValueChange = { value ->
                                resetMobile =
                                    value.filter { it.isDigit() }
                                        .take(10)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Registered Mobile Number")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone
                            ),
                            singleLine = true,
                            enabled = !resetLoading
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { value ->
                                otpCode =
                                    value.filter { it.isDigit() }
                                        .take(6)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("6-digit OTP")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = false,
                            supportingText = {
                                Text(
                                    "OTP entry will activate after the secure recovery backend is connected."
                                )
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "For security, mobile OTP will not change a Firebase email/password account directly from the app. OTP verification and password update will be completed through the secure backend.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    HorizontalDivider()

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "EMPLOYEE LOGIN",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF163A5F)
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Employee ID and password are managed by the Master/Admin. Employees should contact their Master/Admin for login recovery.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            },

            confirmButton = {

                if (recoveryMethod == "EMAIL") {

                    Button(
                        onClick = {

                            val cleanEmail =
                                resetEmail.trim().lowercase()

                            if (
                                cleanEmail.isBlank() ||
                                !android.util.Patterns.EMAIL_ADDRESS
                                    .matcher(cleanEmail)
                                    .matches()
                            ) {

                                Toast.makeText(
                                    context,
                                    "Enter a valid registered email",
                                    Toast.LENGTH_SHORT
                                ).show()

                            } else {

                                resetLoading = true

                                com.google.firebase.auth.FirebaseAuth
                                    .getInstance()
                                    .sendPasswordResetEmail(cleanEmail)
                                    .addOnSuccessListener {

                                        resetLoading = false
                                        showForgotPassword = false

                                        Toast.makeText(
                                            context,
                                            "Password reset link sent. Please check your email.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    .addOnFailureListener { error ->

                                        resetLoading = false

                                        Toast.makeText(
                                            context,
                                            error.message
                                                ?: "Could not send password reset email",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        },
                        enabled = !resetLoading
                    ) {

                        if (resetLoading) {

                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )

                        } else {

                            Text("SEND RESET LINK")
                        }
                    }

                } else {

                    Button(
                        onClick = {

                            if (resetMobile.length != 10) {

                                Toast.makeText(
                                    context,
                                    "Enter a valid 10 digit registered mobile number",
                                    Toast.LENGTH_SHORT
                                ).show()

                            } else {

                                Toast.makeText(
                                    context,
                                    "Mobile OTP recovery is ready in the app. Secure backend connection is required before OTP can be sent and used to reset the password.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Text("SEND OTP")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showForgotPassword = false
                    },
                    enabled = !resetLoading
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showContact) {
        AlertDialog(
            onDismissRequest = { showContact = false },
            title = { Text("Contact & Support") },
            text = {
                Column {
                    Text("Lakshya Support", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "For account, subscription or technical assistance, " +
                                "contact Lakshya support."
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Mobile / WhatsApp: 8847761604\nEmail: rajbyar1999@gmail.com",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showContact = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Text("About Lakshya", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "LAKSHYA BUSINESS MANAGEMENT",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Lakshya is a professional business management application " +
                                "designed to simplify daily operations in one organized platform."
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "It provides tools for daily entries, calculations, reports, " +
                                "results, employee management, printing, Excel export and " +
                                "business performance tracking."
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Version 1.0", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy Policy") },
            text = {
                Text(
                    "Lakshya is designed to keep business and account information " +
                            "organized and protected. A complete privacy policy covering " +
                            "account data, mobile verification, backup and cloud services " +
                            "will be published before public release."
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacy = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text("Terms & Conditions") },
            text = {
                Text(
                    "Use of Lakshya will be subject to account, subscription and " +
                            "acceptable-use terms. Final terms will be provided before " +
                            "the application is distributed publicly."
                )
            },
            confirmButton = {
                Button(onClick = { showTerms = false }) {
                    Text("CLOSE")
                }
            }
        )
    }
}


// =====================================================
// ADMIN DASHBOARD
// =====================================================

data class EmployeeFeatureOption(
    val key: String,
    val label: String
)

val EMPLOYEE_FEATURE_OPTIONS = listOf(
    EmployeeFeatureOption("NEW_ENTRY", "NEW ENTRY"),
    EmployeeFeatureOption("TODAY_DASHBOARD", "TODAY'S DASHBOARD"),
    EmployeeFeatureOption("SEARCH_REPORTS", "SEARCH & REPORTS"),
    EmployeeFeatureOption("ALL_ENTRY_HISTORY", "ALL ENTRY HISTORY"),
    EmployeeFeatureOption("GAME_WISE_LIST", "GAME WISE LIST"),
    EmployeeFeatureOption("RESULT", "RESULT"),
    EmployeeFeatureOption("JODI_HISTORY", "JODI HISTORY"),
    EmployeeFeatureOption("PANEL_HISTORY", "PANEL HISTORY"),
    EmployeeFeatureOption("CHUKARA", "CHUKARA"),
    EmployeeFeatureOption("PROFIT_LOSS", "PROFIT / LOSS"),
    EmployeeFeatureOption("EXCEL_EXPORT", "EXPORT TO EXCEL"),
    EmployeeFeatureOption("PRINT", "PRINT"),
    EmployeeFeatureOption("EDIT_ENTRY", "EDIT ENTRY"),
    EmployeeFeatureOption("CANCEL_ENTRY", "CANCEL ENTRY")
)

@Composable
fun EmployeePermissionsScreen(
    database: AppDatabase,
    masterUid: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var employees by remember {
        mutableStateOf<List<EmployeeEntity>>(emptyList())
    }

    var selectedEmployee by remember {
        mutableStateOf<EmployeeEntity?>(null)
    }

    var employeeMenuExpanded by remember {
        mutableStateOf(false)
    }

    val permissions = remember {
        mutableStateMapOf<String, Boolean>()
    }

    DisposableEffect(masterUid) {
        if (masterUid.isBlank()) {
            employees = emptyList()
            onDispose { }
        } else {
            val registration =
                com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
                    .collection("masters")
                    .document(masterUid)
                    .collection("employees")
                    .addSnapshotListener { snapshot, error ->

                        if (error != null || snapshot == null) {
                            return@addSnapshotListener
                        }

                        val ownedUserIds =
                            snapshot.documents
                                .mapNotNull { it.getString("userId") }
                                .map { it.trim().uppercase() }
                                .toSet()

                        scope.launch {
                            val local = database.employeeDao().getAllEmployees()

                            snapshot.documents.forEach { document ->
                                val cloudUserId =
                                    document.getString("userId")
                                        .orEmpty()
                                        .trim()
                                        .uppercase()

                                if (cloudUserId.isBlank()) return@forEach

                                val existing =
                                    local.firstOrNull {
                                        it.userId.trim().uppercase() == cloudUserId
                                    }

                                val cloudActive =
                                    document.getBoolean("isActive")
                                        ?: document.getBoolean("active")
                                        ?: true

                                if (existing == null) {
                                    try {
                                        database.employeeDao().insertEmployee(
                                            EmployeeEntity(
                                                employeeName =
                                                    document.getString("employeeName")
                                                        .orEmpty()
                                                        .ifBlank { cloudUserId },
                                                userId = cloudUserId,
                                                role = "EMPLOYEE",
                                                isActive = cloudActive
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                } else {
                                    if (cloudActive) {
                                        database.employeeDao().activateEmployee(existing.id)
                                    } else {
                                        database.employeeDao().deactivateEmployee(existing.id)
                                    }
                                }
                            }

                            employees =
                                database.employeeDao()
                                    .getAllEmployees()
                                    .filter {
                                        it.role.uppercase() != "ADMIN" &&
                                                it.userId.trim().uppercase() in ownedUserIds
                                    }
                        }
                    }

            onDispose {
                registration.remove()
            }
        }
    }

    LaunchedEffect(selectedEmployee?.userId) {
        permissions.clear()

        EMPLOYEE_FEATURE_OPTIONS.forEach {
            permissions[it.key] = true
        }

        val employee = selectedEmployee
        if (employee != null) {
            // First show locally cached permissions immediately.
            database.employeePermissionDao()
                .getPermissions(employee.userId)
                .forEach {
                    permissions[it.featureKey] = it.isAllowed
                }

            // Then refresh from Firebase so Master Admin permissions
            // stay synced across devices.
            if (masterUid.isNotBlank()) {
                CloudPermissionManager.getPermissions(
                    masterUid = masterUid,
                    employeeUserId = employee.userId,
                    onSuccess = { cloudPermissions ->
                        EMPLOYEE_FEATURE_OPTIONS.forEach { feature ->
                            permissions[feature.key] =
                                cloudPermissions[feature.key] ?: true
                        }

                        scope.launch {
                            val rows = EMPLOYEE_FEATURE_OPTIONS.map { feature ->
                                EmployeePermissionEntity(
                                    employeeUserId = employee.userId,
                                    featureKey = feature.key,
                                    isAllowed = permissions[feature.key] ?: true
                                )
                            }
                            database.employeePermissionDao().savePermissions(rows)
                        }
                    },
                    onError = { /* Keep local cached permissions if cloud is unavailable. */ }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "EMPLOYEE PERMISSIONS",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Employee ko kaunsa option SHOW/HIDE karna hai select karo.",
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box {
            OutlinedButton(
                onClick = {
                    employeeMenuExpanded = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    selectedEmployee?.let {
                        "${it.employeeName} (${it.userId})"
                    } ?: "SELECT EMPLOYEE"
                )
            }

            DropdownMenu(
                expanded = employeeMenuExpanded,
                onDismissRequest = {
                    employeeMenuExpanded = false
                }
            ) {
                employees.forEach { employee ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${employee.employeeName} (${employee.userId})"
                            )
                        },
                        onClick = {
                            selectedEmployee = employee
                            employeeMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedEmployee != null) {
            EMPLOYEE_FEATURE_OPTIONS.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        feature.label,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked =
                            permissions[feature.key] ?: true,
                        onCheckedChange = {
                            permissions[feature.key] = it
                        }
                    )
                }

            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val employee =
                        selectedEmployee
                            ?: return@Button

                    scope.launch {
                        val rows =
                            EMPLOYEE_FEATURE_OPTIONS.map { feature ->
                                EmployeePermissionEntity(
                                    employeeUserId =
                                        employee.userId,
                                    featureKey =
                                        feature.key,
                                    isAllowed =
                                        permissions[feature.key]
                                            ?: true
                                )
                            }

                        database.employeePermissionDao()
                            .savePermissions(rows)

                        if (masterUid.isBlank()) {
                            Toast.makeText(
                                context,
                                "Master UID missing. Permissions saved only on this device.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            CloudPermissionManager.savePermissions(
                                masterUid = masterUid,
                                employeeUserId = employee.userId,
                                permissions = permissions.toMap(),
                                onSuccess = {
                                    Toast.makeText(
                                        context,
                                        "Permissions saved for ${employee.userId}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { message ->
                                    Toast.makeText(
                                        context,
                                        "Local save OK, cloud save failed: $message",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SAVE PERMISSIONS")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@Composable
fun historicalResultForDate(
    prefs: android.content.SharedPreferences,
    game: String,
    selectedDate: Long
): String {
    val start = lakshyaDayStart(selectedDate)
    val end = lakshyaDayEnd(selectedDate)

    return getResultHistory(
        prefs = prefs,
        game = game
    )
        .filter {
            it.savedTime in start..end
        }
        .maxByOrNull {
            it.savedTime
        }
        ?.result
        .orEmpty()
        .trim()
}


fun calculateHistoricalChukara(
    savedEntry: SavedEntry,
    historicalResults: Map<String, String>,
    resultPrefs: android.content.SharedPreferences
): List<WinningChukara> {

    if (savedEntry.status != "ACTIVE") {
        return emptyList()
    }

    // Keep the same print-security rule as current Chukara.
    val securityStartTime =
        resultPrefs.getLong(
            "PRINT_SECURITY_START_TIME",
            Long.MAX_VALUE
        )

    val isLegacyEntry =
        savedEntry.savedTime <
                securityStartTime

    if (
        !savedEntry.isPrinted &&
        !isLegacyEntry
    ) {
        return emptyList()
    }

    val wins =
        mutableListOf<WinningChukara>()

    savedEntry.games.forEach { game ->

        val pairedOpenGame =
            openGameForCloseGame(game)

        val result =
            if (pairedOpenGame != null) {

                val openResult =
                    historicalResults[
                        pairedOpenGame
                    ].orEmpty().trim()

                val parts =
                    openResult.split("-")

                if (parts.size == 3) {
                    digitTotalLastDigit(
                        parts[2]
                    )
                } else {
                    ""
                }

            } else {
                historicalResults[
                    game
                ].orEmpty().trim()
            }

        if (result.isBlank()) {
            return@forEach
        }

        val isCloseAkdaOnly =
            result.length == 1 &&
                    result.all {
                        it.isDigit()
                    }

        val parts =
            if (isCloseAkdaOnly) {
                emptyList()
            } else {
                result.split("-")
            }

        if (
            !isCloseAkdaOnly &&
            parts.size != 2 &&
            parts.size != 3
        ) {
            return@forEach
        }

        val openPana =
            if (isCloseAkdaOnly) {
                ""
            } else {
                parts[0]
            }

        val openSingle =
            if (isCloseAkdaOnly) {
                result
            } else {
                parts[1]
                    .firstOrNull()
                    ?.toString()
                    .orEmpty()
            }

        val isFullResult =
            !isCloseAkdaOnly &&
                    parts.size == 3

        val jodi =
            if (isFullResult) {
                parts[1]
            } else {
                ""
            }

        val closePana =
            if (isFullResult) {
                parts[2]
            } else {
                ""
            }

        savedEntry.entries
            .forEachIndexed {
                    index,
                    entry ->

                val isWin =
                    when (
                        entry.entryType
                    ) {
                        "Single" ->
                            entry.number ==
                                    openSingle

                        "Jodi" ->
                            isFullResult &&
                                    entry.number
                                        .padStart(
                                            2,
                                            '0'
                                        ) ==
                                    jodi

                        "Pana" ->
                            !isCloseAkdaOnly &&
                                    (
                                            entry.number ==
                                                    openPana ||
                                                    (
                                                            isFullResult &&
                                                                    entry.number ==
                                                                    closePana
                                                            )
                                            )

                        else ->
                            false
                    }

                if (isWin) {

                    val multiplier =
                        when (
                            entry.entryType
                        ) {
                            "Single" -> 9
                            "Jodi" -> 80
                            "Pana" -> 100
                            else -> 0
                        }

                    wins.add(
                        WinningChukara(
                            game = game,
                            entryType =
                                entry.entryType,
                            number =
                                entry.number,
                            playedAmount =
                                entry.amount,
                            chukaraAmount =
                                entry.amount *
                                        multiplier,
                            paymentKey =
                                "OLD|${savedEntry.id}|$game|${entry.entryType}|${entry.number}|$index|$result"
                        )
                    )
                }
            }
    }

    return wins
}


@Composable
fun OldDayBackupScreen(
    database: AppDatabase,
    currentMasterUid: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resultPrefs =
        remember {
            context.getSharedPreferences(
                "lakshya_results_$currentMasterUid",
                android.content.Context.MODE_PRIVATE
            )
        }

    var dateText by remember {
        mutableStateOf(
            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(
                Date(
                    System.currentTimeMillis() -
                            24L *
                            60L *
                            60L *
                            1000L
                )
            )
        )
    }

    var selectedDate by remember {
        mutableLongStateOf(
            System.currentTimeMillis() -
                    24L *
                    60L *
                    60L *
                    1000L
        )
    }

    var selectedSection by remember {
        mutableStateOf("ALL")
    }

    var bills by remember {
        mutableStateOf<List<BillEntity>>(
            emptyList()
        )
    }

    var editHistory by remember {
        mutableStateOf<List<CloudEditHistoryRecord>>(emptyList())
    }

    var exportingGame by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(selectedDate, currentMasterUid) {

        val start = lakshyaDayStart(selectedDate)
        val end = lakshyaDayEnd(selectedDate)

        // Local cache first, so Backup still opens while temporarily offline.
        bills = database.billDao()
            .getAllBills(currentMasterUid)
            .filter { it.savedTime in start..end }
            .sortedByDescending { it.savedTime }

        // Cloud is the source of truth for old-day bills on every device.
        if (currentMasterUid.isNotBlank()) {
            CloudBillManager.getBills(
                masterUid = currentMasterUid,
                onSuccess = { cloudBills ->
                    bills = cloudBills
                        .filter { it.savedTime in start..end }
                        .map { bill ->
                            BillEntity(
                                id = bill.localBillId,
                                masterUid = currentMasterUid,
                                customerName = bill.customerName,
                                games = serializeGames(bill.games),
                                entries = serializeEntries(
                                    bill.entries.map {
                                        NumberAmountEntry(it.number, it.amount, it.entryType, it.actualAmount)
                                    }
                                ),
                                perGameTotal = bill.perGameTotal,
                                grandTotal = bill.grandTotal,
                                savedTime = bill.savedTime,
                                status = bill.status,
                                createdBy = bill.createdBy,
                                cancelledBy = bill.cancelledBy,
                                cancelledTime = bill.cancelledTime,
                                isEdited = bill.isEdited,
                                lastEditedBy = bill.lastEditedBy,
                                lastEditedTime = bill.lastEditedTime,
                                isPrinted = bill.isPrinted,
                                printedBy = bill.printedBy,
                                printedTime = bill.printedTime,
                                isDayLocked = bill.isDayLocked,
                                dayLockedBy = bill.dayLockedBy,
                                dayLockedTime = bill.dayLockedTime
                            )
                        }
                        .sortedByDescending { it.savedTime }
                },
                onError = { /* Keep local fallback. */ }
            )
        }
    }

    DisposableEffect(currentMasterUid, selectedDate) {
        val start = lakshyaDayStart(selectedDate)
        val end = lakshyaDayEnd(selectedDate)
        val registration = CloudEditHistoryManager.listenHistory(
            masterUid = currentMasterUid,
            onUpdate = { rows ->
                editHistory = rows
                    .filter { it.editedTime in start..end }
                    .sortedByDescending { it.editedTime }
            },
            onError = { }
        )
        onDispose { registration?.remove() }
    }

    val backupEntries =
        bills.map { bill ->
            SavedEntry(
                id = bill.id,
                customerName =
                    bill.customerName,
                games =
                    deserializeGames(
                        bill.games
                    ),
                entries =
                    deserializeEntries(
                        bill.entries
                    ),
                perGameTotal =
                    bill.perGameTotal,
                grandTotal =
                    bill.grandTotal,
                savedTime =
                    bill.savedTime,
                status =
                    bill.status,
                createdBy =
                    bill.createdBy,
                cancelledBy =
                    bill.cancelledBy,
                cancelledTime =
                    bill.cancelledTime,
                isEdited =
                    bill.isEdited,
                lastEditedBy =
                    bill.lastEditedBy,
                lastEditedTime =
                    bill.lastEditedTime,
                isPrinted =
                    bill.isPrinted,
                printedBy =
                    bill.printedBy,
                printedTime =
                    bill.printedTime,
                isDayLocked =
                    bill.isDayLocked,
                dayLockedBy =
                    bill.dayLockedBy,
                dayLockedTime =
                    bill.dayLockedTime
            )
        }

    val activeEntries =
        backupEntries.filter {
            it.status == "ACTIVE"
        }

    val historicalResults =
        resultGames()
            .associateWith { game ->
                historicalResultForDate(
                    prefs =
                        resultPrefs,
                    game =
                        game,
                    selectedDate =
                        selectedDate
                )
            }

    val totalCollection =
        activeEntries.sumOf {
            it.grandTotal
        }

    val totalChukara =
        activeEntries
            .flatMap { entry ->
                calculateHistoricalChukara(
                    savedEntry =
                        entry,
                    historicalResults =
                        historicalResults,
                    resultPrefs =
                        resultPrefs
                )
            }
            .sumOf {
                it.chukaraAmount
            }

    val netAmount =
        totalCollection -
                totalChukara

    val profitLossText =
        when {
            netAmount > 0 ->
                "PROFIT"

            netAmount < 0 ->
                "LOSS"

            else ->
                "NO PROFIT / NO LOSS"
        }

    val gameTotals =
        gameList.associateWith {
                game ->

            activeEntries.sumOf {
                    entry ->

                if (
                    game in
                    entry.games
                ) {
                    entry.perGameTotal
                } else {
                    0
                }
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp)
    ) {

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        Text(
            "OLD DAY / BACKUP",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            "Admin only • old data read-only",
            fontSize = 11.sp
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = dateText,
            onValueChange = {
                    value ->

                dateText =
                    value.filter {
                        it.isDigit() ||
                                it == '/'
                    }.take(10)
            },
            label = {
                Text(
                    "DATE - DD/MM/YYYY"
                )
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Button(
            onClick = {

                val parsed =
                    parseLakshyaDate(
                        dateText
                    )

                if (parsed == null) {

                    Toast.makeText(
                        context,
                        "Date DD/MM/YYYY format me dalo",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    selectedDate =
                        parsed

                    selectedSection =
                        "ALL"
                }
            },
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "OPEN THIS DATE"
            )
        }

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        Text(
            "DATE: ${
                lakshyaDateLabel(
                    selectedDate
                )
            }",
            fontSize = 13.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            "TOTAL COLLECTION: ₹$totalCollection",
            fontSize = 13.sp
        )

        Text(
            "TOTAL ENTRIES: ${bills.size}",
            fontSize = 12.sp
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        // RESULT HISTORY is intentionally
        // NOT shown in OLD DAY / BACKUP.
        val backupOptions =
            listOf(
                "ALL" to
                        "ALL ENTRY HISTORY",

                "EDIT" to
                        "EDIT HISTORY",

                "GAME" to
                        "GAME WISE LIST",

                "PL" to
                        "PROFIT / LOSS",

                "EXCEL" to
                        "EXPORT TO EXCEL"
            )

        backupOptions.forEach {
                option ->

            val key =
                option.first

            val label =
                option.second

            if (
                selectedSection ==
                key
            ) {

                Button(
                    onClick = {
                        selectedSection =
                            key
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(label)
                }

            } else {

                OutlinedButton(
                    onClick = {
                        selectedSection =
                            key
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(label)
                }
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        when (
            selectedSection
        ) {

            "ALL" -> {

                Text(
                    "ALL ENTRY HISTORY",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    bills.isEmpty()
                ) {

                    Text(
                        "Is date ki koi entry nahi mili.",
                        fontSize = 12.sp
                    )

                } else {

                    bills.forEach {
                            bill ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical =
                                            4.dp
                                    )
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .padding(
                                            10.dp
                                        )
                            ) {

                                Text(
                                    "SLIP #${bill.id} • ${bill.customerName}",
                                    fontSize =
                                        13.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "Games: ${bill.games}",
                                    fontSize =
                                        10.sp
                                )

                                Text(
                                    "Entries: ${bill.entries}",
                                    fontSize =
                                        10.sp
                                )

                                Text(
                                    "Total: ₹${bill.grandTotal}",
                                    fontSize =
                                        11.sp
                                )

                                Text(
                                    "Status: ${bill.status}",
                                    fontSize =
                                        10.sp
                                )

                                Text(
                                    SimpleDateFormat(
                                        "dd-MM-yyyy hh:mm a",
                                        Locale.getDefault()
                                    ).format(
                                        Date(
                                            bill.savedTime
                                        )
                                    ),
                                    fontSize =
                                        9.sp
                                )
                            }
                        }
                    }
                }
            }

            "EDIT" -> {

                Text(
                    "EDIT HISTORY",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    editHistory
                        .isEmpty()
                ) {

                    Text(
                        "Is date ki koi edit history nahi mili.",
                        fontSize =
                            12.sp
                    )

                } else {

                    editHistory
                        .groupBy {
                            it.billId
                        }
                        .map {
                                (_, edits) ->

                            val oldest =
                                edits
                                    .minByOrNull {
                                        it.editedTime
                                    }!!

                            val latest =
                                edits
                                    .maxByOrNull {
                                        it.editedTime
                                    }!!

                            latest.copy(
                                oldCustomerName =
                                    oldest
                                        .oldCustomerName,

                                oldGames =
                                    oldest
                                        .oldGames,

                                oldEntries =
                                    oldest
                                        .oldEntries,

                                oldPerGameTotal =
                                    oldest
                                        .oldPerGameTotal,

                                oldGrandTotal =
                                    oldest
                                        .oldGrandTotal
                            )
                        }
                        .sortedByDescending {
                            it.editedTime
                        }
                        .forEach {
                                history ->

                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                4.dp
                                        )
                            ) {

                                Column(
                                    modifier =
                                        Modifier
                                            .padding(
                                                10.dp
                                            )
                                ) {

                                    Text(
                                        "ENTRY #${history.billId}",
                                        fontSize =
                                            13.sp,
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                    Text(
                                        "Edited By: ${history.editedBy}",
                                        fontSize =
                                            11.sp
                                    )

                                    Text(
                                        SimpleDateFormat(
                                            "dd-MM-yyyy hh:mm a",
                                            Locale.getDefault()
                                        ).format(
                                            Date(
                                                history.editedTime
                                            )
                                        ),
                                        fontSize =
                                            10.sp
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier
                                                .height(
                                                    5.dp
                                                )
                                    )

                                    Text(
                                        "OLD: ${history.oldCustomerName} | ₹${history.oldGrandTotal}",
                                        fontSize =
                                            11.sp
                                    )

                                    Text(
                                        "NEW: ${history.newCustomerName} | ₹${history.newGrandTotal}",
                                        fontSize =
                                            11.sp
                                    )
                                }
                            }
                        }
                }
            }

            "GAME" -> {

                Text(
                    "GAME WISE LIST",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )

                gameTotals
                    .forEach {
                            (game, total) ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical =
                                            3.dp
                                    )
                        ) {

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            10.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement
                                        .SpaceBetween
                            ) {

                                Text(
                                    game,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "₹$total"
                                )
                            }
                        }
                    }
            }

            "PL" -> {

                Text(
                    "PROFIT / LOSS",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Text(
                            "TOTAL COLLECTION",
                            fontSize =
                                12.sp
                        )

                        Text(
                            "₹$totalCollection",
                            fontSize =
                                24.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Text(
                            "TOTAL CHUKARA",
                            fontSize =
                                12.sp
                        )

                        Text(
                            "₹$totalChukara",
                            fontSize =
                                24.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Text(
                            profitLossText,
                            fontSize =
                                16.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "₹${
                                kotlin.math.abs(
                                    netAmount
                                )
                            }",
                            fontSize =
                                28.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            "EXCEL" -> {

                Text(
                    "EXPORT TO EXCEL",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "DATE: ${
                        lakshyaDateLabel(
                            selectedDate
                        )
                    }",
                    fontSize = 11.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )

                if (
                    bills.isEmpty()
                ) {

                    Text(
                        "Is date me export karne ke liye data nahi hai.",
                        fontSize =
                            12.sp
                    )

                } else {

                    gameList
                        .chunked(2)
                        .forEach {
                                rowGames ->

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement
                                        .spacedBy(
                                            8.dp
                                        )
                            ) {

                                rowGames
                                    .forEach {
                                            game ->

                                        Button(
                                            onClick = {

                                                if (
                                                    exportingGame ==
                                                    null
                                                ) {

                                                    exportingGame =
                                                        game

                                                    scope.launch {

                                                        try {

                                                            val fileName =
                                                                exportLimitExcel(
                                                                    context =
                                                                        context,

                                                                    savedEntries =
                                                                        backupEntries,

                                                                    selectedGame =
                                                                        game
                                                                )

                                                            Toast.makeText(
                                                                context,
                                                                "$game ${lakshyaDateLabel(selectedDate)} Excel Download Complete: $fileName",
                                                                Toast.LENGTH_LONG
                                                            ).show()

                                                        } catch (
                                                            e:
                                                            Exception
                                                        ) {

                                                            Toast.makeText(
                                                                context,
                                                                "Excel Export Failed: ${e.message}",
                                                                Toast.LENGTH_LONG
                                                            ).show()

                                                        } finally {

                                                            exportingGame =
                                                                null
                                                        }
                                                    }
                                                }
                                            },
                                            enabled =
                                                exportingGame ==
                                                        null,
                                            modifier =
                                                Modifier
                                                    .weight(
                                                        1f
                                                    )
                                        ) {

                                            Text(
                                                if (
                                                    exportingGame ==
                                                    game
                                                ) {
                                                    "DOWNLOADING..."
                                                } else {
                                                    game
                                                }
                                            )
                                        }
                                    }

                                if (
                                    rowGames.size ==
                                    1
                                ) {
                                    Spacer(
                                        modifier =
                                            Modifier
                                                .weight(
                                                    1f
                                                )
                                    )
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier
                                        .height(
                                            8.dp
                                        )
                            )
                        }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(18.dp)
        )

        Text(
            "Old data read-only hai. Current day ke records change nahi honge.",
            fontSize = 10.sp
        )

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick =
                onBack,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}



@Composable
fun SuperMasterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var customers by remember {
        mutableStateOf<List<SuperMasterCustomer>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var loadingMore by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var lastDocument by remember {
        mutableStateOf<
                com.google.firebase.firestore.DocumentSnapshot?
                >(null)
    }

    var hasMore by remember {
        mutableStateOf(true)
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    var manualActivationCustomer by remember {
        mutableStateOf<SuperMasterCustomer?>(null)
    }

    var manualEmployeeLimit by remember {
        mutableIntStateOf(5)
    }

    var manualActivationLoading by remember {
        mutableStateOf(false)
    }

    var deactivationCustomer by remember {
        mutableStateOf<SuperMasterCustomer?>(null)
    }

    var deactivationLoading by remember {
        mutableStateOf(false)
    }

    BackHandler {
        onBack()
    }

    fun loadFirstPage() {
        loading = true
        errorMessage = ""
        lastDocument = null
        hasMore = true

        SuperMasterCloudManager.getCustomersPage(
            pageSize = 25L,
            lastDocument = null,
            onSuccess = { page ->
                customers = page.customers
                lastDocument = page.lastDocument
                hasMore = page.hasMore
                loading = false
            },
            onError = {
                errorMessage = it
                loading = false
            }
        )
    }

    fun loadNextPage() {
        if (
            loading ||
            loadingMore ||
            !hasMore
        ) {
            return
        }

        loadingMore = true
        errorMessage = ""

        SuperMasterCloudManager.getCustomersPage(
            pageSize = 25L,
            lastDocument = lastDocument,
            onSuccess = { page ->
                val existingIds =
                    customers
                        .asSequence()
                        .map { it.masterUid }
                        .toHashSet()

                customers =
                    customers +
                            page.customers.filter {
                                it.masterUid !in existingIds
                            }

                lastDocument = page.lastDocument
                hasMore = page.hasMore
                loadingMore = false
            },
            onError = {
                errorMessage = it
                loadingMore = false
            }
        )
    }

    LaunchedEffect(refreshKey) {
        loadFirstPage()
    }

    val query =
        searchText.trim().lowercase()

    val visibleCustomers =
        remember(customers, query) {
            if (query.isBlank()) {
                customers
            } else {
                customers.filter { customer ->
                    customer.businessName
                        .lowercase()
                        .contains(query) ||
                            customer.ownerName
                                .lowercase()
                                .contains(query) ||
                            customer.mobile
                                .lowercase()
                                .contains(query) ||
                            customer.email
                                .lowercase()
                                .contains(query) ||
                            customer.masterUid
                                .lowercase()
                                .contains(query)
                }
            }
        }

    val activeCount =
        customers.count {
            it.masterAccessActive &&
                    it.subscription?.isCurrentlyActive() == true
        }

    val pendingCount =
        customers.size - activeCount

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA)),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack
                ) {
                    Text("← Back")
                }

                Spacer(Modifier.weight(1f))

                Text(
                    "SUPER MASTER",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF163A5F)
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = {
                        refreshKey++
                    }
                ) {
                    Text("REFRESH")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                SuperMasterStatCard(
                    title = "LOADED",
                    value = customers.size.toString(),
                    modifier = Modifier.weight(1f)
                )

                SuperMasterStatCard(
                    title = "ACTIVE",
                    value = activeCount.toString(),
                    modifier = Modifier.weight(1f)
                )

                SuperMasterStatCard(
                    title = "PENDING / EXPIRED",
                    value = pendingCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "Search loaded customers"
                    )
                },
                placeholder = {
                    Text(
                        "Business / Owner / Mobile / Email / UID"
                    )
                },
                singleLine = true
            )

            if (
                query.isNotBlank() &&
                hasMore
            ) {
                Spacer(Modifier.height(6.dp))

                Text(
                    "Search is checking the customers currently loaded. Tap LOAD MORE to include the next 25 customers.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (errorMessage.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(18.dp),
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        } else if (visibleCustomers.isEmpty()) {
            item {
                Text(
                    if (query.isBlank()) {
                        "No registered customers found."
                    } else {
                        "No matching customer in loaded records."
                    },
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {

            items(
                count = visibleCustomers.size,
                key = { index ->
                    visibleCustomers[index].masterUid
                }
            ) { index ->

                val customer =
                    visibleCustomers[index]

                val subscription =
                    customer.subscription

                // ID access and plan are separate checks.
                // Firebase `isActive` controls the ID; subscription controls plan validity.
                val active =
                    customer.masterAccessActive &&
                            subscription?.isCurrentlyActive() == true

                val employeeLimit =
                    subscription?.employeeLimit
                        ?: customer.selectedEmployeeLimit

                val monthlyPrice =
                    subscription?.monthlyPrice
                        ?: customer.selectedMonthlyPrice

                val expiry =
                    if (
                        subscription != null &&
                        subscription.expiryDate > 0L
                    ) {
                        SimpleDateFormat(
                            "dd MMM yyyy",
                            Locale.getDefault()
                        ).format(
                            Date(subscription.expiryDate)
                        )
                    } else {
                        "Not Active"
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    customer.businessName
                                        .ifBlank {
                                            "Unnamed Business"
                                        },
                                    fontSize = 18.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        Color(0xFF163A5F)
                                )

                                Text(
                                    customer.ownerName
                                        .ifBlank {
                                            "Owner not entered"
                                        },
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }

                            Surface(
                                shape =
                                    RoundedCornerShape(50),
                                color =
                                    if (active) {
                                        Color(0xFFE8F5E9)
                                    } else {
                                        Color(0xFFFFEBEE)
                                    }
                            ) {
                                Text(
                                    text =
                                        if (active) {
                                            "ACTIVE"
                                        } else {
                                            "INACTIVE"
                                        },
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                    fontSize = 11.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        if (active) {
                                            Color(0xFF168447)
                                        } else {
                                            Color(0xFFD32F2F)
                                        }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        SuperMasterDetailRow(
                            "Email",
                            customer.email.ifBlank { "-" }
                        )

                        SuperMasterDetailRow(
                            "Mobile",
                            customer.mobile.ifBlank { "-" }
                        )

                        SuperMasterDetailRow(
                            "Master UID",
                            customer.masterUid
                        )

                        SuperMasterDetailRow(
                            "Employee Limit",
                            employeeLimit.toString()
                        )

                        SuperMasterDetailRow(
                            "Monthly Plan",
                            "₹$monthlyPrice"
                        )

                        SuperMasterDetailRow(
                            "Expiry",
                            expiry
                        )


                        Spacer(Modifier.height(14.dp))

                        if (customer.masterUid != LAKSHYA_SUPER_MASTER_UID) {
                            OutlinedButton(
                                onClick = {
                                    manualActivationCustomer = customer
                                    manualEmployeeLimit =
                                        employeeLimit.coerceIn(5, 10)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (active) {
                                        "MANUAL RENEW / CHANGE PLAN"
                                    } else {
                                        "ACTIVATE MANUALLY"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }


                        Spacer(Modifier.height(8.dp))

                        if (
                            active &&
                            customer.masterUid != LAKSHYA_SUPER_MASTER_UID
                        ) {
                            OutlinedButton(
                                onClick = {
                                    deactivationCustomer = customer
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFD32F2F)
                                )
                            ) {
                                Text(
                                    "DEACTIVATE PLAN",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!loading && hasMore) {
            item {
                Button(
                    onClick = {
                        loadNextPage()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !loadingMore
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "LOAD NEXT 25 CUSTOMERS"
                        )
                    }
                }
            }
        }

        if (
            !loading &&
            !hasMore &&
            customers.isNotEmpty()
        ) {
            item {
                Text(
                    "All loaded customers reached.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }

    deactivationCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = {
                if (!deactivationLoading) {
                    deactivationCustomer = null
                }
            },
            title = {
                Text(
                    "Deactivate Plan?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        customer.businessName.ifBlank {
                            customer.email.ifBlank {
                                customer.masterUid
                            }
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF163A5F)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "This will immediately block this Master/Admin from using the app. Customer data will not be deleted.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deactivationLoading = true

                        SuperMasterCloudManager.deactivatePlan(
                            masterUid = customer.masterUid,
                            onSuccess = {
                                deactivationLoading = false
                                deactivationCustomer = null
                                refreshKey++
                            },
                            onError = { message ->
                                deactivationLoading = false
                                errorMessage = message
                            }
                        )
                    },
                    enabled = !deactivationLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    if (deactivationLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("DEACTIVATE PLAN")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deactivationCustomer = null
                    },
                    enabled = !deactivationLoading
                ) {
                    Text("CANCEL")
                }
            }
        )
    }


    manualActivationCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = {
                if (!manualActivationLoading) {
                    manualActivationCustomer = null
                }
            },
            title = {
                Text(
                    "Activate Without Payment",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        customer.businessName.ifBlank {
                            customer.email.ifBlank {
                                customer.masterUid
                            }
                        },
                        color = Color(0xFF163A5F),
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Employee Limit",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        (5..10).forEach { limit ->
                            FilterChip(
                                selected =
                                    manualEmployeeLimit == limit,
                                onClick = {
                                    manualEmployeeLimit = limit
                                },
                                label = {
                                    Text(limit.toString())
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    PaymentDetailRow(
                        "Validity",
                        "1 Month"
                    )

                    PaymentDetailRow(
                        "Monthly Plan",
                        "₹${manualEmployeeLimit * 1000}"
                    )

                    PaymentDetailRow(
                        "Payment",
                        "NO PAYMENT"
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "This is a Super Master manual activation. No paid transaction will be created.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        manualActivationLoading = true

                        SuperMasterCloudManager
                            .activateManuallyWithoutPayment(
                                masterUid = customer.masterUid,
                                employeeLimit =
                                    manualEmployeeLimit,
                                onSuccess = {
                                    manualActivationLoading = false
                                    manualActivationCustomer = null

                                    Toast.makeText(
                                        context,
                                        "Plan ACTIVE. Master/Admin access enabled.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    refreshKey++
                                },
                                onError = { message ->
                                    manualActivationLoading =
                                        false
                                    errorMessage = message
                                }
                            )
                    },
                    enabled = !manualActivationLoading
                ) {
                    if (manualActivationLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("ACTIVATE WITHOUT PAYMENT")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        manualActivationCustomer = null
                    },
                    enabled = !manualActivationLoading
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

}

@Composable
private fun SuperMasterStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 14.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF163A5F)
            )

            Text(
                title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
        }
    }
}


@Composable
private fun SuperMasterDetailRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(0.38f),
            fontSize = 12.sp,
            color = Color.Gray
        )

        Text(
            value,
            modifier = Modifier.weight(0.62f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163A5F)
        )
    }
}


@Composable
fun AdminDashboardScreen(
    savedEntries: List<SavedEntry>,
    isSuperMaster: Boolean,
    onSuperMaster: () -> Unit,
    onNewEntry: () -> Unit,
    onTodayDashboard: () -> Unit,
    onSearchReports: () -> Unit,
    onManageEmployees: () -> Unit,
    onEditHistory: () -> Unit,
    onOldDayBackup: () -> Unit,
    onEmployeePermissions: () -> Unit,
    onGameWiseLimit: () -> Unit,
    onResult: () -> Unit,
    onProfitLoss: () -> Unit,
    onExportExcel: () -> Unit,
    onSubscription: () -> Unit,
    onPrinterSetup: () -> Unit,
    onCloseDay: () -> Unit,
    onUndoCloseDay: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }

    // Remote UI configuration for the actual Master Admin Dashboard.
    var uiConfig by remember { mutableStateOf(UiConfig()) }
    DisposableEffect(Unit) {
        val uiListener = UiConfigManager.listenUiConfig(
            onUpdate = { updatedConfig ->
                uiConfig = updatedConfig
            },
            onError = { /* Keep current/default UI if remote UI config fails */ }
        )
        onDispose { uiListener.remove() }
    }

    fun adminRemoteColor(value: String, fallback: Color): Color {
        return try {
            Color(android.graphics.Color.parseColor(value.trim()))
        } catch (_: Exception) {
            fallback
        }
    }

    val activeEntries = remember(savedEntries) { savedEntries.filter { it.status == "ACTIVE" } }
    val todayCollection = remember(activeEntries) { activeEntries.sumOf { it.grandTotal } }
    val entryCount = activeEntries.size
    val customerCount = remember(activeEntries) {
        activeEntries.asSequence()
            .map { it.customerName.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .count()
    }

    val navy = Color(0xFF071426)
    val navyCard = Color(0xFF0D1F36)
    val navySoft = Color(0xFF132844)
    val gold = Color(0xFFD4AF37)
    val lightGold = Color(0xFFFFD86B)
    val muted = Color(0xFFAAB6C5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF06101D),
                        Color(0xFF09182A),
                        navy
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAKSHYA",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "BUSINESS MANAGEMENT",
                        color = gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                }

                Surface(
                    onClick = { showMenu = true },
                    shape = RoundedCornerShape(13.dp),
                    color = navySoft,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF29405D)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☰",
                            color = lightGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "MENU",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val adminHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val adminGreeting = when (adminHour) {
                in 5..11 -> "Good Morning"
                in 12..16 -> "Good Afternoon"
                in 17..20 -> "Good Evening"
                else -> "Good Night"
            }
            Text(
                text = "$adminGreeting, Admin",
                color = lightGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = uiConfig.dashboardTitle.ifBlank { "Master Admin Dashboard" },
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            if (uiConfig.showAnnouncement && uiConfig.announcementText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = navySoft)
                ) {
                    Text(
                        text = uiConfig.announcementText,
                        modifier = Modifier.padding(14.dp),
                        color = lightGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = navyCard
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "TODAY'S COLLECTION",
                        color = muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "₹${String.format("%,d", todayCollection)}",
                        color = lightGold,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF263B53))
                    Spacer(Modifier.height(9.dp))

                    Row(Modifier.fillMaxWidth()) {
                        HomeStat(
                            title = "ENTRIES",
                            value = entryCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStat(
                            title = "CUSTOMERS",
                            value = customerCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStat(
                            title = "STATUS",
                            value = "ACTIVE",
                            modifier = Modifier.weight(1f),
                            alignEnd = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (uiConfig.showNewEntry) {
                Button(
                    onClick = onNewEntry,
                    enabled = uiConfig.enableNewEntry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gold,
                        contentColor = navy
                    )
                ) {
                    Text(
                        text = "+  ${uiConfig.newEntryButtonText}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(18.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeQuickCard(
                    title = "TODAY",
                    subtitle = "Dashboard",
                    modifier = Modifier.weight(1f),
                    onClick = onTodayDashboard
                )
                if (uiConfig.showResult) {
                    HomeQuickCard(
                        title = uiConfig.resultButtonText,
                        subtitle = "View result",
                        modifier = Modifier.weight(1f),
                        onClick = { if (uiConfig.enableResult) onResult() }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeQuickCard(
                    title = "REPORTS",
                    subtitle = "Search entries",
                    modifier = Modifier.weight(1f),
                    onClick = onSearchReports
                )
                HomeQuickCard(
                    title = "P & L",
                    subtitle = "Profit / Loss",
                    modifier = Modifier.weight(1f),
                    onClick = onProfitLoss
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("ADMIN MANAGEMENT", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeQuickCard("EMPLOYEES", "Profiles & activity", Modifier.weight(1f), onManageEmployees)
                HomeQuickCard("PERMISSIONS", "Access control", Modifier.weight(1f), onEmployeePermissions)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiConfig.showHistory) {
                    HomeQuickCard(
                        uiConfig.historyButtonText,
                        "Audit changes",
                        Modifier.weight(1f),
                        { if (uiConfig.enableHistory) onEditHistory() }
                    )
                }
                if (uiConfig.showBackup) {
                    HomeQuickCard(
                        uiConfig.backupButtonText,
                        "Old day records",
                        Modifier.weight(1f),
                        { if (uiConfig.enableBackup) onOldDayBackup() }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lakshya Menu",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Admin controls",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showMenu = false }) {
                        Text("CLOSE")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    MenuSectionTitle("BUSINESS")
                    if (uiConfig.showNewEntry) {
                        HomeMenuRow(uiConfig.newEntryButtonText) {
                            if (uiConfig.enableNewEntry) {
                                showMenu = false
                                onNewEntry()
                            }
                        }
                    }
                    HomeMenuRow("Today's Dashboard") {
                        showMenu = false
                        onTodayDashboard()
                    }
                    HomeMenuRow("Search & Reports") {
                        showMenu = false
                        onSearchReports()
                    }
                    if (uiConfig.showHistory) {
                        HomeMenuRow(uiConfig.historyButtonText) {
                            if (uiConfig.enableHistory) {
                                showMenu = false
                                onEditHistory()
                            }
                        }
                    }
                    if (uiConfig.showBackup) {
                        HomeMenuRow(uiConfig.backupButtonText) {
                            if (uiConfig.enableBackup) {
                                showMenu = false
                                onOldDayBackup()
                            }
                        }
                    }
                    HomeMenuRow("Game Wise Limit") {
                        showMenu = false
                        onGameWiseLimit()
                    }
                    if (uiConfig.showResult) {
                        HomeMenuRow(uiConfig.resultButtonText) {
                            if (uiConfig.enableResult) {
                                showMenu = false
                                onResult()
                            }
                        }
                    }
                    HomeMenuRow("Profit / Loss") {
                        showMenu = false
                        onProfitLoss()
                    }
                    HomeMenuRow("Export to Excel") {
                        showMenu = false
                        onExportExcel()
                    }

                    MenuSectionTitle("EMPLOYEES")
                    HomeMenuRow("Manage Employees") {
                        showMenu = false
                        onManageEmployees()
                    }
                    HomeMenuRow("Employee Permissions") {
                        showMenu = false
                        onEmployeePermissions()
                    }

                    MenuSectionTitle("DAY CONTROL")
                    HomeMenuRow("Close Day") {
                        showMenu = false
                        onCloseDay()
                    }
                    HomeMenuRow("Undo Close Day") {
                        showMenu = false
                        onUndoCloseDay()
                    }

                    if (isSuperMaster) {
                        MenuSectionTitle("SUPER MASTER")
                        HomeMenuRow("All Customers / Masters") {
                            showMenu = false
                            onSuperMaster()
                        }
                    }

                    MenuSectionTitle("ACCOUNT & APP")
                    HomeMenuRow("Subscription") {
                        showMenu = false
                        onSubscription()
                    }
                    HomeMenuRow("Printer Setup") {
                        showMenu = false
                        onPrinterSetup()
                    }
                    HomeMenuRow("Share Lakshya App") {
                        showMenu = false
                        AppConfigManager.getAppConfig(
                            onSuccess = { appConfig ->
                                val downloadUrl = appConfig.updateUrl.trim()
                                if (!AppDistribution.isOfficialDownloadUrl(downloadUrl)) {
                                    Toast.makeText(
                                        context,
                                        "Official download link is not configured yet.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Lakshya Business Management")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "LAKSHYA BUSINESS MANAGEMENT\n\n" +
                                                    "Professional business management application.\n" +
                                                    "\nOfficial download:\n" +
                                                    "$downloadUrl\n" +
                                                    "\nDownload and install the latest Lakshya APK."
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share Lakshya")
                                    )
                                }
                            },
                            onError = {
                                Toast.makeText(
                                    context,
                                    "Could not load the official download link.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                    HomeMenuRow("Contact Us") {
                        showMenu = false
                        showContact = true
                    }
                    HomeMenuRow("About Lakshya") {
                        showMenu = false
                        showAbout = true
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showMenu = false
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "LOG OUT",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Text(
                    text = "About Lakshya",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "LAKSHYA BUSINESS MANAGEMENT",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Lakshya is designed to simplify daily entries, " +
                                "calculations, reports, results, employee management, " +
                                "printing and business tracking in one organized application."
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Version 1.0",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (showContact) {
        AlertDialog(
            onDismissRequest = { showContact = false },
            title = {
                Text(
                    text = "Contact & Support",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Lakshya Support", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Mobile / WhatsApp: 8847761604")
                    Text("Email: rajbyar1999@gmail.com")
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:8847761604"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CALL SUPPORT") }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/918847761604")
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("WHATSAPP") }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val emailIntent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:rajbyar1999@gmail.com")
                            ).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Lakshya Support")
                            }
                            context.startActivity(emailIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("EMAIL SUPPORT") }
                }
            },
            confirmButton = {
                Button(onClick = { showContact = false }) {
                    Text("CLOSE")
                }
            }
        )
    }
}

@Composable
private fun HomeStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            if (alignEnd) Alignment.End
            else Alignment.Start
    ) {
        Text(
            text = title,
            color = Color(0xFF9EADBF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HomeQuickCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0D1F36),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF203A58)
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color(0xFFFFD86B),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9EADBF),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MenuSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(
            top = 12.dp,
            bottom = 6.dp,
            start = 4.dp
        ),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun HomeMenuRow(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "›",
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EditHistoryScreen(
    database: AppDatabase,
    currentMasterUid: String,
    onBack: () -> Unit
) {
    var historyList by remember {
        mutableStateOf<List<CloudEditHistoryRecord>>(emptyList())
    }

    val context = LocalContext.current

    val archivePrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_day_archive_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    val currentDayStart =
        archivePrefs.getLong(
            "CURRENT_DAY_START",
            lakshyaDayStart(System.currentTimeMillis())
        )

    DisposableEffect(currentMasterUid, currentDayStart) {
        val registration = CloudEditHistoryManager.listenHistory(
            masterUid = currentMasterUid,
            onUpdate = { cloudRows ->
                historyList = cloudRows.filter { it.editedTime >= currentDayStart }
            },
            onError = {
                // Offline fallback: this screen stays available from cloud when connection returns.
            }
        )
        onDispose { registration?.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(
            text = "EDIT HISTORY",
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (historyList.isEmpty()) {
            Text(
                text = "No edit history found",
                fontSize = 14.sp
            )
        } else {
            historyList
                .groupBy { it.billId }
                .map { (_, edits) ->
                    val oldest = edits.minByOrNull { it.editedTime }!!
                    val latest = edits.maxByOrNull { it.editedTime }!!

                    latest.copy(
                        oldCustomerName = oldest.oldCustomerName,
                        oldGames = oldest.oldGames,
                        oldEntries = oldest.oldEntries,
                        oldPerGameTotal = oldest.oldPerGameTotal,
                        oldGrandTotal = oldest.oldGrandTotal
                    )
                }
                .sortedByDescending { it.editedTime }
                .forEach { history ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Entry ID: ${history.billId}",
                                fontSize = 15.sp
                            )

                            Text(
                                text = "Edited By: ${history.editedBy}",
                                fontSize = 13.sp
                            )

                            Text(
                                text = "Edited Time: ${
                                    SimpleDateFormat(
                                        "dd-MM-yyyy hh:mm a",
                                        Locale.getDefault()
                                    ).format(Date(history.editedTime))
                                }",
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 5.dp)
                                ) {
                                    Text(
                                        text = "OLD DATA",
                                        fontSize = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Customer: ${history.oldCustomerName}",
                                        fontSize = 12.sp
                                    )

                                    Text(
                                        text = "Games: ${deserializeGames(history.oldGames).joinToString(", ")}",
                                        fontSize = 12.sp
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    deserializeEntries(history.oldEntries).forEach { item ->
                                        Text(
                                            text = "${item.entryType} ${item.number} = ₹${item.amount}",
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Per Game: ₹${history.oldPerGameTotal}",
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        text = "Total: ₹${history.oldGrandTotal}",
                                        fontSize = 12.sp
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 5.dp)
                                ) {
                                    Text(
                                        text = "NEW DATA",
                                        fontSize = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Customer: ${history.newCustomerName}",
                                        fontSize = 12.sp,
                                        color =
                                            if (history.newCustomerName != history.oldCustomerName)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                LocalContentColor.current
                                    )

                                    Text(
                                        text = "Games: ${deserializeGames(history.newGames).joinToString(", ")}",
                                        fontSize = 12.sp,
                                        color =
                                            if (history.newGames != history.oldGames)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                LocalContentColor.current
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    val oldEntryItems = deserializeEntries(history.oldEntries)

                                    deserializeEntries(history.newEntries).forEach { item ->

                                        val matchingOld =
                                            oldEntryItems.firstOrNull {
                                                it.entryType == item.entryType &&
                                                        it.number == item.number
                                            }

                                        val wasChanged =
                                            matchingOld == null ||
                                                    matchingOld.amount != item.amount

                                        Text(
                                            text =
                                                if (wasChanged)
                                                    "${item.entryType} ${item.number} = ₹${item.amount}  ← EDITED"
                                                else
                                                    "${item.entryType} ${item.number} = ₹${item.amount}",
                                            fontSize = 11.sp,
                                            color =
                                                if (wasChanged)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    LocalContentColor.current
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Per Game: ₹${history.newPerGameTotal}",
                                        fontSize = 11.sp,
                                        color =
                                            if (history.newPerGameTotal != history.oldPerGameTotal)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                LocalContentColor.current
                                    )

                                    Text(
                                        text = "Total: ₹${history.newGrandTotal}",
                                        fontSize = 12.sp,
                                        color =
                                            if (history.newGrandTotal != history.oldGrandTotal)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }
                }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "BACK")
        }

        Spacer(modifier = Modifier.height(25.dp))
    }
}


// =====================================================
// EMPLOYEE PLAN UPGRADE
// =====================================================

@Composable
fun EmployeePlanUpgradeScreen(
    isPlanActive: Boolean,
    currentEmployeeLimit: Int,
    currentMonthlyPrice: Int,
    onContinuePayment: (selectedLimit: Int, newMonthlyPrice: Int, payNowAmount: Int) -> Unit,
    onBack: () -> Unit
) {
    val baseLimit = 5
    val maxLimit = 10
    val basePrice = 5000
    val perEmployeePrice = 1000

    val safeCurrentLimit = currentEmployeeLimit.coerceIn(baseLimit, maxLimit)

    // ACTIVE: start from current limit.
    // EXPIRED: always start fresh from 5 employees.
    val startingLimit = if (isPlanActive) safeCurrentLimit else baseLimit

    var selectedLimit by remember(isPlanActive, safeCurrentLimit) {
        mutableIntStateOf(startingLimit)
    }

    val newMonthlyPrice =
        basePrice + ((selectedLimit - baseLimit) * perEmployeePrice)

    val payNowAmount =
        if (isPlanActive) {
            ((selectedLimit - safeCurrentLimit).coerceAtLeast(0)) * perEmployeePrice
        } else {
            newMonthlyPrice
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            if (isPlanActive) "UPGRADE PLAN" else "CHOOSE YOUR PLAN",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (isPlanActive) {
                "Add employees to your active plan. You only pay for the extra employee limit now."
            } else {
                "Your previous plan is expired. Choose a fresh monthly plan from 5 to 10 employees."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    if (isPlanActive) "CURRENT ACTIVE PLAN" else "PLAN STATUS",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(10.dp))

                if (isPlanActive) {
                    Text("Employee Limit: $safeCurrentLimit")
                    Text("Monthly Price: ₹$currentMonthlyPrice")
                } else {
                    Text(
                        "Previous plan expired",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text("New plan starts from 5 Employees • ₹5,000/month")
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "SELECT EMPLOYEE LIMIT",
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = {
                            val minimum =
                                if (isPlanActive) safeCurrentLimit else baseLimit

                            if (selectedLimit > minimum) {
                                selectedLimit--
                            }
                        },
                        enabled = selectedLimit >
                                (if (isPlanActive) safeCurrentLimit else baseLimit)
                    ) {
                        Text("−", fontSize = 24.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$selectedLimit",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("Employees")
                    }

                    Button(
                        onClick = {
                            if (selectedLimit < maxLimit) {
                                selectedLimit++
                            }
                        },
                        enabled = selectedLimit < maxLimit
                    ) {
                        Text("+", fontSize = 24.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "+1 Employee = ₹1,000/month",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(6.dp))
                Text("Maximum = 10 Employees")
            }
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    if (isPlanActive) "UPGRADE SUMMARY" else "NEW PLAN",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Employee Limit: $selectedLimit",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Monthly Plan: ₹$newMonthlyPrice",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "PAY NOW",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "₹$payNowAmount",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isPlanActive && selectedLimit > safeCurrentLimit) {
                    Text(
                        "Only ${selectedLimit - safeCurrentLimit} additional employee(s) charged now. Current expiry date remains unchanged.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isPlanActive) {
                    Text(
                        "This starts a new 1-month subscription after successful payment.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onContinuePayment(
                    selectedLimit,
                    newMonthlyPrice,
                    payNowAmount
                )
            },
            enabled = if (isPlanActive) {
                selectedLimit > safeCurrentLimit
            } else {
                true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (isPlanActive) {
                    "CONTINUE • PAY ₹$payNowAmount"
                } else {
                    "CONTINUE • PAY ₹$payNowAmount"
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("BACK")
        }

        Spacer(Modifier.height(30.dp))
    }
}


// =====================================================
// MANAGE EMPLOYEES
// =====================================================

@Composable
fun ManageEmployeesScreen(

    database: AppDatabase,
    savedEntries: List<SavedEntry>,
    masterUid: String,
    onUpgradePlan: () -> Unit,

    onBack: () -> Unit

) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    var employeeName by remember {
        mutableStateOf("")
    }

    var userId by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    val employees =
        remember {
            mutableStateListOf<EmployeeEntity>()
        }

    var selectedEmployeeProfile by remember { mutableStateOf<EmployeeEntity?>(null) }

    var employeePendingDeletion by remember { mutableStateOf<EmployeeEntity?>(null) }

    var showEmployeeLimitDialog by remember {
        mutableStateOf(false)
    }

    var reachedEmployeeLimit by remember {
        mutableIntStateOf(5)
    }

    fun showLimitUpgrade(limit: Int) {
        reachedEmployeeLimit = limit.coerceIn(5, 10)
        showEmployeeLimitDialog = true
    }

    fun refreshEmployees() {

        if (masterUid.isBlank()) {
            employees.clear()
            return
        }

        com.google.firebase.firestore.FirebaseFirestore
            .getInstance()
            .collection("masters")
            .document(masterUid)
            .collection("employees")
            .get()
            .addOnSuccessListener { snapshot ->

                val ownedUserIds =
                    snapshot.documents
                        .mapNotNull { it.getString("userId") }
                        .map { it.trim().uppercase() }
                        .toSet()

                coroutineScope.launch {
                    val list =
                        database.employeeDao()
                            .getAllEmployees()
                            .filter {
                                it.userId.trim().uppercase() in ownedUserIds
                            }

                    employees.clear()
                    employees.addAll(list)
                }
            }
            .addOnFailureListener { error ->
                employees.clear()
                Toast.makeText(
                    context,
                    error.message ?: "Unable to load employees",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    DisposableEffect(masterUid) {
        if (masterUid.isBlank()) {
            employees.clear()
            onDispose { }
        } else {
            val registration =
                com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
                    .collection("masters")
                    .document(masterUid)
                    .collection("employees")
                    .addSnapshotListener { snapshot, error ->

                        if (error != null || snapshot == null) {
                            return@addSnapshotListener
                        }

                        coroutineScope.launch {
                            val localEmployees =
                                database.employeeDao().getAllEmployees()

                            snapshot.documents.forEach { document ->
                                val cloudUserId =
                                    document.getString("userId")
                                        .orEmpty()
                                        .trim()
                                        .uppercase()

                                if (cloudUserId.isBlank()) {
                                    return@forEach
                                }

                                val cloudName =
                                    document.getString("employeeName")
                                        .orEmpty()
                                        .ifBlank { cloudUserId }

                                val cloudActive =
                                    document.getBoolean("isActive")
                                        ?: document.getBoolean("active")
                                        ?: true

                                val existing =
                                    localEmployees.firstOrNull {
                                        it.userId.trim().uppercase() == cloudUserId
                                    }

                                if (existing == null) {
                                    try {
                                        database.employeeDao().insertEmployee(
                                            EmployeeEntity(
                                                employeeName = cloudName,
                                                userId = cloudUserId,
                                                role = "EMPLOYEE",
                                                isActive = cloudActive
                                            )
                                        )
                                    } catch (_: Exception) {
                                        // Another listener/refresh may have inserted it.
                                    }
                                } else {
                                    if (cloudActive) {
                                        database.employeeDao().activateEmployee(existing.id)
                                    } else {
                                        database.employeeDao().deactivateEmployee(existing.id)
                                    }
                                }
                            }

                            refreshEmployees()
                        }
                    }

            onDispose {
                registration.remove()
            }
        }
    }

    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp)

    ) {

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "MANAGE EMPLOYEES",
            fontSize = 28.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedTextField(
            value = employeeName,
            onValueChange = {
                employeeName = it
            },
            label = {
                Text("Employee Name")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = userId,
            onValueChange = {
                // IDs are matched without considering uppercase/lowercase.
                // Keep the text exactly as the admin typed it in this field.
                userId = it
            },
            label = {
                Text("User ID")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
            },
            label = {
                Text("Password")
            },
            visualTransformation =
                PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(

            onClick = {

                val cleanName =
                    employeeName.trim()

                val cleanUserId =
                    userId
                        .trim()
                        .uppercase()

                val cleanPassword =
                    password.trim()

                when {

                    cleanName.isBlank() -> {

                        Toast.makeText(
                            context,
                            "Enter Employee Name",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    cleanUserId.isBlank() -> {

                        Toast.makeText(
                            context,
                            "Enter User ID",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    cleanUserId == "ADMIN" -> {

                        Toast.makeText(
                            context,
                            "ADMIN User ID is reserved",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    cleanPassword.isBlank() -> {

                        Toast.makeText(
                            context,
                            "Enter Password",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    EmployeeAuthManager.strongPasswordError(cleanPassword) != null -> {

                        Toast.makeText(
                            context,
                            EmployeeAuthManager.strongPasswordError(cleanPassword) ?: "Invalid password",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {

                        coroutineScope.launch {

                            val exists =
                                database
                                    .employeeDao()
                                    .userIdExists(
                                        cleanUserId
                                    )

                            if (exists > 0) {

                                Toast.makeText(
                                    context,
                                    "User ID already exists",
                                    Toast.LENGTH_LONG
                                ).show()

                            } else {

                                if (masterUid.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Master UID missing. Please login again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }

                                CloudSubscriptionManager.getSubscription(
                                    masterUid = masterUid,
                                    onSuccess = { cloudSubscription ->

                                        if (!cloudSubscription.isCurrentlyActive()) {
                                            Toast.makeText(
                                                context,
                                                "Subscription expired or inactive. Please renew your plan.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@getSubscription
                                        }

                                        val employeeLimit =
                                            cloudSubscription.employeeLimit.coerceAtLeast(0)

                                        com.google.firebase.firestore.FirebaseFirestore
                                            .getInstance()
                                            .collection("masters")
                                            .document(masterUid)
                                            .collection("employees")
                                            .get()
                                            .addOnSuccessListener { employeeSnapshot ->

                                                val activeEmployeeCount =
                                                    employeeSnapshot.documents.count { document ->
                                                        document.getBoolean("isActive") == true ||
                                                                document.getBoolean("active") == true
                                                    }

                                                if (activeEmployeeCount >= employeeLimit) {
                                                    showLimitUpgrade(employeeLimit)
                                                } else {

                                                    EmployeeAuthManager.createEmployeeAccount(
                                                        userId = cleanUserId,
                                                        password = cleanPassword,
                                                        onSuccess = { employeeUid, employeeEmail ->

                                                            FirebaseManager.saveEmployee(
                                                                employeeUid = employeeUid,
                                                                employeeName = cleanName,
                                                                userId = cleanUserId,
                                                                authEmail = employeeEmail,
                                                                onSuccess = {
                                                                    /*
                                                                     * Do NOT insert the employee into Room here.
                                                                     *
                                                                     * The Firestore employee snapshot listener already
                                                                     * synchronizes masters/{masterUid}/employees into
                                                                     * the local employee table. Inserting again here can
                                                                     * hit the UNIQUE userId constraint.
                                                                     */
                                                                    employeeName = ""
                                                                    userId = ""
                                                                    password = ""
                                                                    refreshEmployees()

                                                                    Toast.makeText(
                                                                        context,
                                                                        "Employee Created Successfully (${
                                                                            activeEmployeeCount + 1
                                                                        }/$employeeLimit)",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                },
                                                                onError = { error ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        error,
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                }
                                                            )
                                                        },
                                                        onError = { error ->
                                                            Toast.makeText(
                                                                context,
                                                                error,
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    )
                                                }
                                            }
                                            .addOnFailureListener { error ->
                                                Toast.makeText(
                                                    context,
                                                    error.message
                                                        ?: "Unable to check employee limit",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                    },
                                    onError = { error ->
                                        Toast.makeText(
                                            context,
                                            error,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)

        ) {

            Text(
                text = "ADD EMPLOYEE"
            )
        }

        Spacer(
            modifier = Modifier.height(25.dp)
        )

        Text(
            text = "EMPLOYEE LIST",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        if (employees.isEmpty()) {

            Text(
                text = "No employees created yet"
            )

        } else {

            employees.forEach { employee ->

                Card(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    onClick = { selectedEmployeeProfile = employee }

                ) {

                    Column(

                        modifier =
                            Modifier.padding(16.dp)

                    ) {

                        Text(
                            text =
                                employee.employeeName,
                            fontSize = 19.sp
                        )

                        Text(
                            text =
                                "User ID: ${employee.userId}"
                        )

                        Text(
                            text =
                                "Status: ${
                                    if (employee.isActive) {
                                        "ACTIVE"
                                    } else {
                                        "INACTIVE"
                                    }
                                }"
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Button(

                            onClick = {

                                val newStatus = !employee.isActive

                                fun updateEmployeeStatus() {
                                    FirebaseManager.findEmployee(
                                        userId = employee.userId,
                                        onSuccess = { cloudEmployee ->
                                            if (cloudEmployee == null) {
                                                Toast.makeText(
                                                    context,
                                                    "Cloud employee not found",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                FirebaseManager.setEmployeeActive(
                                                    employeeUid = cloudEmployee.employeeUid,
                                                    active = newStatus,
                                                    onSuccess = {
                                                        coroutineScope.launch {
                                                            if (newStatus) {
                                                                database.employeeDao().activateEmployee(employee.id)
                                                            } else {
                                                                database.employeeDao().deactivateEmployee(employee.id)
                                                            }
                                                            refreshEmployees()
                                                            Toast.makeText(
                                                                context,
                                                                if (newStatus) "Employee Activated" else "Employee Deactivated",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    },
                                                    onError = { error ->
                                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }

                                if (!newStatus) {
                                    updateEmployeeStatus()
                                } else if (masterUid.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Master UID missing. Please login again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    CloudSubscriptionManager.getSubscription(
                                        masterUid = masterUid,
                                        onSuccess = { cloudSubscription ->
                                            if (!cloudSubscription.isCurrentlyActive()) {
                                                Toast.makeText(
                                                    context,
                                                    "Subscription expired or inactive. Please renew your plan.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                return@getSubscription
                                            }

                                            val employeeLimit =
                                                cloudSubscription.employeeLimit.coerceAtLeast(0)

                                            com.google.firebase.firestore.FirebaseFirestore
                                                .getInstance()
                                                .collection("masters")
                                                .document(masterUid)
                                                .collection("employees")
                                                .get()
                                                .addOnSuccessListener { employeeSnapshot ->
                                                    val activeEmployeeCount =
                                                        employeeSnapshot.documents.count { document ->
                                                            document.getBoolean("isActive") == true ||
                                                                    document.getBoolean("active") == true
                                                        }

                                                    if (activeEmployeeCount >= employeeLimit) {
                                                        showLimitUpgrade(employeeLimit)
                                                    } else {
                                                        updateEmployeeStatus()
                                                    }
                                                }
                                                .addOnFailureListener { error ->
                                                    Toast.makeText(
                                                        context,
                                                        error.message
                                                            ?: "Unable to check employee limit",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                        },
                                        onError = { error ->
                                            Toast.makeText(
                                                context,
                                                error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            },

                            modifier =
                                Modifier.fillMaxWidth()

                        ) {

                            Text(
                                text =
                                    if (employee.isActive) {
                                        "DEACTIVATE"
                                    } else {
                                        "ACTIVATE"
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { employeePendingDeletion = employee },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DELETE EMPLOYEE")
                        }
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "BACK TO ADMIN DASHBOARD"
            )
        }

        Spacer(
            modifier = Modifier.height(30.dp)
        )
    }

    if (showEmployeeLimitDialog) {
        AlertDialog(
            onDismissRequest = {
                showEmployeeLimitDialog = false
            },
            title = {
                Text(
                    "Employee Limit Reached",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Your current plan allows maximum $reachedEmployeeLimit employees."
                    )
                    Spacer(Modifier.height(10.dp))
                    if (reachedEmployeeLimit < 10) {
                        Text(
                            "Increase your employee limit from ${reachedEmployeeLimit + 1} up to 10."
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "+1 Employee = ₹1,000/month",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Maximum employee limit is 10."
                        )
                    }
                }
            },
            confirmButton = {
                if (reachedEmployeeLimit < 10) {
                    Button(
                        onClick = {
                            showEmployeeLimitDialog = false
                            onUpgradePlan()
                        }
                    ) {
                        Text("INCREASE LIMIT")
                    }
                } else {
                    Button(
                        onClick = {
                            showEmployeeLimitDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (reachedEmployeeLimit < 10) {
                    OutlinedButton(
                        onClick = {
                            showEmployeeLimitDialog = false
                        }
                    ) {
                        Text("NOT NOW")
                    }
                }
            }
        )
    }

    employeePendingDeletion?.let { employee ->
        AlertDialog(
            onDismissRequest = { employeePendingDeletion = null },
            title = { Text("Delete Employee ID?") },
            text = {
                Text(
                    "${employee.employeeName} (${employee.userId}) ka access aur permissions permanently remove ho jayenge. Iske baad aap nayi employee ID bana sakte hain."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        FirebaseManager.findEmployee(
                            userId = employee.userId,
                            onSuccess = { cloudEmployee ->
                                if (cloudEmployee == null) {
                                    Toast.makeText(context, "Cloud employee not found", Toast.LENGTH_LONG).show()
                                } else {
                                    FirebaseManager.deleteEmployee(
                                        employeeUid = cloudEmployee.employeeUid,
                                        employeeUserId = employee.userId,
                                        onSuccess = {
                                            coroutineScope.launch {
                                                database.employeePermissionDao().deleteEmployeePermissions(employee.userId)
                                                database.employeeDao().deleteEmployee(employee.id)
                                                selectedEmployeeProfile = null
                                                employeePendingDeletion = null
                                                refreshEmployees()
                                                Toast.makeText(context, "Employee ID deleted", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            onError = { error -> Toast.makeText(context, error, Toast.LENGTH_LONG).show() }
                        )
                    }
                ) { Text("DELETE") }
            },
            dismissButton = {
                OutlinedButton(onClick = { employeePendingDeletion = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    selectedEmployeeProfile?.let { employee ->
        val created = savedEntries.filter { it.createdBy.equals(employee.userId, true) }
        val edited = savedEntries.filter { it.lastEditedBy.equals(employee.userId, true) }
        val cancelled = savedEntries.filter { it.cancelledBy.equals(employee.userId, true) }
        val printed = savedEntries.filter { it.printedBy.equals(employee.userId, true) }
        val activeCreated = created.filter { it.status == "ACTIVE" }
        AlertDialog(
            onDismissRequest = { selectedEmployeeProfile = null },
            title = {
                Column {
                    Text(employee.employeeName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("EMPLOYEE ACTIVITY", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("User ID: ${employee.userId}")
                    Text("Status: ${if (employee.isActive) "ACTIVE" else "INACTIVE"}")
                    Spacer(Modifier.height(14.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("WORK SUMMARY", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Entries created: ${created.size}")
                            Text("Active amount: ₹${activeCreated.sumOf { it.grandTotal }}")
                            Text("Edited entries: ${edited.size}")
                            Text("Cancelled entries: ${cancelled.size}")
                            Text("Printed entries: ${printed.size}")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("RECENT ENTRIES", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (created.isEmpty()) Text("No entries found on this device")
                    created.sortedByDescending { it.savedTime }.take(10).forEach { bill ->
                        Text("#${bill.id}  ${bill.customerName}  •  ₹${bill.grandTotal}", fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Note: Until cloud backend is connected, this activity shows records stored on this mobile only.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { selectedEmployeeProfile = null }) { Text("CLOSE") } }
        )
    }
}


// =====================================================
// MAIN DASHBOARD
// =====================================================

@Composable
fun DashboardScreen(
    savedEntries: List<SavedEntry>,
    permissions: Map<String, Boolean>,
    currentUserId: String,
    onNewEntry: () -> Unit,
    onTodayDashboard: () -> Unit,
    onSearchReports: () -> Unit,
    onGameWiseList: () -> Unit,
    onChukara: () -> Unit,
    onResult: () -> Unit,
    onProfitLoss: () -> Unit,
    onExportExcel: () -> Unit,
    onPrinterSetup: () -> Unit,
    onLogout: () -> Unit
) {
    val totalAmount = remember(savedEntries) { savedEntries.sumOf { it.grandTotal } }
    val customerCount = remember(savedEntries) {
        savedEntries.map { it.customerName.trim().uppercase() }.filter { it.isNotBlank() }.distinct().size
    }
    val activeGames = remember(savedEntries) {
        savedEntries.flatMap { it.games }.distinct().size
    }

    val now = java.util.Calendar.getInstance()
    val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
    val todayDate = remember {
        SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date())
    }

    fun allowed(key: String) = permissions[key] ?: true

    // =====================================================
    // REAL-TIME UI CONFIG (Firestore: ui_config/main)
    // Defaults keep the existing UI working if cloud config is unavailable.
    // =====================================================
    var uiConfig by remember { mutableStateOf(UiConfig()) }

    DisposableEffect(Unit) {
        val uiListener = UiConfigManager.listenUiConfig(
            onUpdate = { updatedConfig -> uiConfig = updatedConfig },
            onError = { /* Keep current/default UI if remote UI config fails */ }
        )
        onDispose { uiListener.remove() }
    }

    fun remoteColor(value: String, fallback: Color): Color {
        return try {
            Color(android.graphics.Color.parseColor(value.trim()))
        } catch (_: Exception) {
            fallback
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }

    val bg = Color(0xFF07111F)
    val surface = Color(0xFF0D1B2D)
    val card = Color(0xFF12243A)
    val gold = Color(0xFFE0B84C)
    val softGold = Color(0xFFFFDF7A)
    val muted = Color(0xFF9EADBF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06101D), Color(0xFF0A1A2E), bg)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uiConfig.appTitle.ifBlank { "Lakshya" }.uppercase(), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("BUSINESS MANAGEMENT", color = gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Surface(
                onClick = { showMenu = true },
                shape = RoundedCornerShape(13.dp),
                color = card,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29435F))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☰", color = softGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Text("MENU", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (uiConfig.showAnnouncement && uiConfig.announcementText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = remoteColor(uiConfig.primaryColor, Color(0xFF102943)).copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, remoteColor(uiConfig.primaryColor, Color(0xFF24415F)))
            ) {
                Text(
                    text = uiConfig.announcementText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(greeting, color = softGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (currentUserId.isBlank()) "Welcome back" else "Welcome, $currentUserId",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(todayDate, color = muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF102943),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24415F))
                ) {
                    Text(
                        text = if (savedEntries.isEmpty()) "Ready for today's first entry" else "${savedEntries.size} entries saved today • ₹${String.format("%,d", totalAmount)} total",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = Color(0xFFD5DFEA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF203A58))
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiConfig.dashboardTitle.ifBlank { "Today's Dashboard" }.uppercase(), color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text("LIVE", color = gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(7.dp))
                Text("₹${String.format("%,d", totalAmount)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF263B53))
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth()) {
                    HomeStat("ENTRIES", savedEntries.size.toString(), Modifier.weight(1f))
                    HomeStat("CUSTOMERS", customerCount.toString(), Modifier.weight(1f))
                    HomeStat("ACTIVE GAMES", activeGames.toString(), Modifier.weight(1f), alignEnd = true)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("QUICK ACTIONS", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))

        if (allowed("NEW_ENTRY") && uiConfig.showNewEntry) {
            Button(
                onClick = onNewEntry,
                enabled = uiConfig.enableNewEntry,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF071426)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x22071426)),
                        contentAlignment = Alignment.Center
                    ) { Text("+", fontSize = 23.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(uiConfig.newEntryButtonText.ifBlank { "NEW ENTRY" }, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Text("Create a new business entry", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("›", fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val quickActions = buildList<Pair<String, Pair<String, () -> Unit>>> {
            if (allowed("TODAY_DASHBOARD")) add("TODAY" to ("Dashboard" to onTodayDashboard))
            if (allowed("SEARCH_REPORTS")) add("REPORTS" to ("Search entries" to onSearchReports))
            if (allowed("RESULT") && uiConfig.showResult && uiConfig.enableResult) add(uiConfig.resultButtonText.ifBlank { "RESULT" } to ("View result" to onResult))
            if (allowed("GAME_WISE_LIST")) add("GAME LIST" to ("Game wise" to onGameWiseList))
            if (allowed("PROFIT_LOSS")) add("P & L" to ("Profit / Loss" to onProfitLoss))
            if (allowed("CHUKARA")) add("CHUKARA" to ("Winning" to onChukara))
        }

        quickActions.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    HomeQuickCard(
                        title = item.first,
                        subtitle = item.second.first,
                        modifier = Modifier.weight(1f),
                        onClick = item.second.second
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0A1829),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF182F49))
        ) {
            Text(
                "Your workspace shows only the features enabled by Admin.",
                modifier = Modifier.padding(13.dp),
                color = Color(0xFF71849B),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).padding(8.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lakshya Menu", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Employee workspace", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showMenu = false }) { Text("CLOSE") }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())) {
                    MenuSectionTitle("BUSINESS")
                    if (allowed("NEW_ENTRY") && uiConfig.showNewEntry && uiConfig.enableNewEntry) HomeMenuRow(uiConfig.newEntryButtonText.ifBlank { "NEW ENTRY" }) { showMenu = false; onNewEntry() }
                    if (allowed("TODAY_DASHBOARD")) HomeMenuRow("Today's Dashboard") { showMenu = false; onTodayDashboard() }
                    if (allowed("SEARCH_REPORTS")) HomeMenuRow("Search & Reports") { showMenu = false; onSearchReports() }
                    if (allowed("GAME_WISE_LIST")) HomeMenuRow("Game Wise List") { showMenu = false; onGameWiseList() }
                    if (allowed("CHUKARA")) HomeMenuRow("Chukara") { showMenu = false; onChukara() }
                    if (allowed("RESULT") && uiConfig.showResult && uiConfig.enableResult) HomeMenuRow(uiConfig.resultButtonText.ifBlank { "RESULT" }) { showMenu = false; onResult() }
                    if (allowed("PROFIT_LOSS")) HomeMenuRow("Profit / Loss") { showMenu = false; onProfitLoss() }
                    if (allowed("EXCEL_EXPORT")) HomeMenuRow("Export to Excel") { showMenu = false; onExportExcel() }
                    HomeMenuRow("Printer Setup") { showMenu = false; onPrinterSetup() }

                    MenuSectionTitle("SUPPORT & LEGAL")
                    HomeMenuRow("Contact Us") { showMenu = false; infoDialog = "CONTACT" }
                    HomeMenuRow("About Lakshya") { showMenu = false; infoDialog = "ABOUT" }
                    HomeMenuRow("Privacy Policy") { showMenu = false; infoDialog = "PRIVACY" }
                    HomeMenuRow("Terms & Conditions") { showMenu = false; infoDialog = "TERMS" }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showMenu = false; onLogout() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("LOG OUT", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                }
            },
            confirmButton = {}
        )
    }

    infoDialog?.let { type ->
        val title = when (type) {
            "CONTACT" -> "Contact & Support"
            "ABOUT" -> "About Lakshya"
            "PRIVACY" -> "Privacy Policy"
            else -> "Terms & Conditions"
        }
        val message = when (type) {
            "CONTACT" -> "Lakshya Support\n\nFor account, subscription or technical assistance, contact Lakshya support."
            "ABOUT" -> "LAKSHYA BUSINESS MANAGEMENT\n\nA professional business management application for daily entries, reports, results, printing, Excel export and business tracking.\n\nVersion 1.0"
            "PRIVACY" -> "Lakshya is designed to keep business and account information organized and protected. The complete privacy policy will be published before public release."
            else -> "Use of Lakshya is subject to account, subscription and acceptable-use terms. Final terms will be provided before public release."
        }
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = { Button(onClick = { infoDialog = null }) { Text("CLOSE") } }
        )
    }
}

@Composable
fun DashboardActionButton(
    title: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val gold = Color(0xFFE0B84C)
    val dark = Color(0xFF071426)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) gold else Color(0xFF12243A),
            contentColor = if (primary) dark else Color.White
        ),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFF29435F)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (primary) Color(0x22071426)
                        else Color(0xFF1B3552)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (primary) "+" else title.take(1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.35.sp
            )
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}


// =====================================================
// DASHBOARD BUTTON
// =====================================================

@Composable
fun DashboardButton(
    title: String,
    onClick: () -> Unit
) {

    Button(

        onClick = onClick,

        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)

    ) {

        Text(
            text = title,
            fontSize = 17.sp
        )
    }
}


// =====================================================
// TODAY'S DASHBOARD
// =====================================================

@Composable
fun TodayDashboardScreen(

    savedEntries: List<SavedEntry>,

    onBack: () -> Unit

) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val gameTotals =

        gameList.associateWith { game ->

            savedEntries.sumOf { savedEntry ->

                if (
                    game in savedEntry.games
                ) {

                    savedEntry.perGameTotal

                } else {

                    0
                }
            }
        }


    val overallTotal =

        gameTotals
            .values
            .sum()


    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp)

    ) {

        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Text(
            text = "TODAY'S DASHBOARD",
            fontSize = 28.sp
        )


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        Text(
            text = "Total Entries: ${savedEntries.size}",
            fontSize = 16.sp
        )


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        gameList.forEach { game ->


            Card(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 5.dp
                    )

            ) {


                Row(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {


                    Text(
                        text = game,
                        fontSize = 20.sp
                    )


                    Text(
                        text = "₹${gameTotals[game] ?: 0}",
                        fontSize = 20.sp
                    )

                }

            }

        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val fileName = exportLimitExcel(context, savedEntries)
                        Toast.makeText(
                            context,
                            "Excel saved: Downloads/Lakshya/$fileName",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Excel export failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EXPORT LIMIT EXCEL")
        }

        Spacer(modifier = Modifier.height(15.dp))


        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {


            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally

            ) {


                Text(

                    text =
                        "TOTAL ALL 14 GAMES",

                    fontSize = 18.sp

                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                Text(

                    text =
                        "₹$overallTotal",

                    fontSize = 30.sp

                )

            }

        }


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        OutlinedButton(

            onClick = onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "BACK TO DASHBOARD"
            )
        }


        Spacer(
            modifier = Modifier.height(30.dp)
        )
    }
}


// =====================================================
// =====================================================
// LIMIT EXCEL EXPORT
// =====================================================

@SuppressLint("MissingPermission")
fun showDownloadNotification(
    notificationManager: NotificationManager,
    fileName: String,
    notification: android.app.Notification
) {
    notificationManager.notify(
        fileName.hashCode(),
        notification
    )
}


@SuppressLint("MissingPermission")
fun exportLimitExcel(
    context: android.content.Context,
    savedEntries: List<SavedEntry>,
    selectedGame: String? = null
): String {
    val activeEntries = savedEntries.filter { it.status == "ACTIVE" }
    val workbook = XSSFWorkbook()
    gameList.forEach { game ->
        val sheet = workbook.createSheet(game)
        val gameEntries = activeEntries.filter { game in it.games }
        val totalCollection = gameEntries.sumOf { it.perGameTotal }

        // PROFIT / LOSS COLLECTION:
        // Pana is deliberately excluded here.
        // Only Single + Jodi money is used for Akda Profit/Loss.
        val profitLossCollection = gameEntries.sumOf { saved ->
            saved.entries
                .filter { entry ->
                    entry.entryType.equals("Single", ignoreCase = true) ||
                            entry.entryType.equals("Jodi", ignoreCase = true)
                }
                .sumOf { entry -> entry.amount }
        }

        // -----------------------------------------------------
        // Jodi ki open side first digit me actual amount add hoti hai.
        // Close side second digit me 8x liability add hoti hai.
        // Example: Jodi 12=100 => Limit 1=100, Limit 2=800.
        // -----------------------------------------------------
        // Cutting limit includes the close-side 8x Jodi risk.
        val akdaLimit = mutableMapOf<String, Int>()
        // Profit/Loss must not include the close-side 8x amount: it is only
        // used while cutting the limit.
        val profitLossAkdaLimit = mutableMapOf<String, Int>()
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            .forEach {
                akdaLimit[it] = 0
                profitLossAkdaLimit[it] = 0
            }

        gameEntries.forEach { saved ->
            saved.entries.forEach { entry ->
                when {
                    entry.entryType.equals("Single", ignoreCase = true) -> {
                        val digit = entry.number.takeLast(1)
                        if (digit in akdaLimit) {
                            akdaLimit[digit] = (akdaLimit[digit] ?: 0) + entry.amount
                            profitLossAkdaLimit[digit] =
                                (profitLossAkdaLimit[digit] ?: 0) + entry.amount
                        }
                    }

                    entry.entryType.equals("Jodi", ignoreCase = true) -> {
                        val cleanNumber = entry.number.filter { it.isDigit() }
                        val jodi = when {
                            cleanNumber.length >= 2 -> cleanNumber.takeLast(2)
                            cleanNumber.length == 1 -> cleanNumber.padStart(2, '0')
                            else -> ""
                        }

                        if (jodi.length == 2) {
                            val firstDigit = jodi[0].toString()
                            val secondDigit = jodi[1].toString()

                            // Open side: actual jodi amount.
                            akdaLimit[firstDigit] =
                                (akdaLimit[firstDigit] ?: 0) + entry.amount
                            profitLossAkdaLimit[firstDigit] =
                                (profitLossAkdaLimit[firstDigit] ?: 0) + entry.amount

                            // Close side: jodi ka possible 8x payment risk.
                            akdaLimit[secondDigit] =
                                (akdaLimit[secondDigit] ?: 0) + (entry.amount * 8)
                        }
                    }
                }
            }
        }

        data class AkdaRisk(
            val akda: String,
            val limit: Int,
            val profitLoss: Int
        )

        // Cutting calculation for Akda: 10 ke 100 = x10.
        // Ascending P/L gives: highest loss -> lower loss -> low profit -> highest profit.
        // PROFIT / LOSS:
        // Close-side Jodi risk is excluded from Profit/Loss.
        // Winning payout = selected Akda limit × 9
        //
        // Example:
        // Total Akda Limit = 950
        // Akda 2 Limit = 300
        // Payout = 300 × 9 = 2700
        // P/L = 950 - 2700 = -1750 => LOSS 1750
        val totalAkdaLimitForPL =
            profitLossAkdaLimit.values.sum()

        val riskList = profitLossAkdaLimit.map { (akda, limit) ->
            AkdaRisk(
                akda = akda,
                limit = limit,
                profitLoss = totalAkdaLimitForPL - (limit * 9)
            )
        }.sortedBy { it.profitLoss }

        var rowIndex = 0

        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("GAME")
            createCell(1).setCellValue(game)
        }

        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("TOTAL COLLECTION")
            createCell(1).setCellValue(totalCollection.toDouble())
        }

        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("LIMIT TYPE")
            createCell(1).setCellValue(
                "TOTAL LIMIT - SINGLE + JODI OPEN/CLOSE"
            )
        }

        rowIndex++

        // -----------------------------------------------------
        // AKDA LIMIT - FIXED SEQUENCE ONLY
        // 1 2 3 4 5 6 7 8 9 0
        // -----------------------------------------------------
        sheet.createRow(rowIndex++).createCell(0).setCellValue("AKDA LIMIT")

        val akdaRow = sheet.createRow(rowIndex++)
        val limitRow = sheet.createRow(rowIndex++)

        akdaRow.createCell(0).setCellValue("AKDA")
        limitRow.createCell(0).setCellValue("LIMIT")

        val fixedAkdaSequence =
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        fixedAkdaSequence.forEachIndexed { index, akda ->
            val column = index + 1

            akdaRow
                .createCell(column)
                .setCellValue(akda)

            limitRow
                .createCell(column)
                .setCellValue(
                    (akdaLimit[akda] ?: 0).toDouble()
                )
        }

        // TOTAL column after 0.
        // This only sums the displayed Akda limits.
        // Pana and Profit/Loss logic are not changed.
        val totalColumn = fixedAkdaSequence.size + 1
        val totalAkdaLimit = fixedAkdaSequence.sumOf { akda ->
            akdaLimit[akda] ?: 0
        }

        akdaRow
            .createCell(totalColumn)
            .setCellValue("TOTAL")

        limitRow
            .createCell(totalColumn)
            .setCellValue(totalAkdaLimit.toDouble())

        rowIndex += 2

        // -----------------------------------------------------
        // PANA LIMIT - LEFT SIDE VERTICAL
        // -----------------------------------------------------
        val panaAmount = mutableMapOf<String, Int>()

        gameEntries.forEach { saved ->
            saved.entries
                .filter {
                    it.entryType.equals(
                        "Pana",
                        ignoreCase = true
                    )
                }
                .forEach { entry ->

                    val pana =
                        entry.number.filter {
                            it.isDigit()
                        }

                    if (pana.isNotBlank()) {
                        panaAmount[pana] =
                            (panaAmount[pana] ?: 0) +
                                    entry.amount
                    }
                }
        }

        val sectionStartRow = rowIndex

        sheet
            .createRow(rowIndex++)
            .createCell(0)
            .setCellValue("PANA LIMIT")

        val panaHeader =
            sheet.createRow(rowIndex++)

        panaHeader
            .createCell(0)
            .setCellValue("PANA")

        panaHeader
            .createCell(1)
            .setCellValue("AMOUNT")

        panaAmount
            .toSortedMap()
            .forEach { (pana, amount) ->

                val row =
                    sheet.createRow(rowIndex++)

                row
                    .createCell(0)
                    .setCellValue(pana)

                row
                    .createCell(1)
                    .setCellValue(
                        amount.toDouble()
                    )
            }

        if (panaAmount.isEmpty()) {

            val row =
                sheet.createRow(rowIndex++)

            row
                .createCell(0)
                .setCellValue("NO PANA")

            row
                .createCell(1)
                .setCellValue(0.0)
        }

        // -----------------------------------------------------
        // AKDA PROFIT / LOSS - RIGHT SIDE VERTICAL
        //
        // PANA uses columns A-B.
        // C-D are intentionally left blank.
        // Profit/Loss starts from E-F.
        //
        // Order:
        // Highest LOSS first
        // then lower LOSS
        // then BREAK EVEN
        // then lower PROFIT
        // then highest PROFIT
        // -----------------------------------------------------

        val plStartColumn = 4

        val plTitleRow =
            sheet.getRow(sectionStartRow)
                ?: sheet.createRow(sectionStartRow)

        plTitleRow
            .createCell(plStartColumn)
            .setCellValue("AKDA PROFIT / LOSS")

        val plHeaderRow =
            sheet.getRow(sectionStartRow + 1)
                ?: sheet.createRow(sectionStartRow + 1)

        plHeaderRow
            .createCell(plStartColumn)
            .setCellValue("AKDA")

        plHeaderRow
            .createCell(plStartColumn + 1)
            .setCellValue("PROFIT / LOSS")

        // riskList is already sorted by profitLoss ascending.
        // Negative values (largest loss) therefore come first.
        riskList.forEachIndexed { index, risk ->

            val excelRowIndex =
                sectionStartRow + 2 + index

            val row =
                sheet.getRow(excelRowIndex)
                    ?: sheet.createRow(excelRowIndex)

            row
                .createCell(plStartColumn)
                .setCellValue(risk.akda)

            row
                .createCell(plStartColumn + 1)
                .setCellValue(
                    when {
                        risk.profitLoss < 0 ->
                            "LOSS ${
                                kotlin.math.abs(
                                    risk.profitLoss
                                )
                            }"

                        risk.profitLoss > 0 ->
                            "PROFIT ${
                                risk.profitLoss
                            }"

                        else ->
                            "0"
                    }
                )
        }

        val plLastRow =
            sectionStartRow + 2 + riskList.size

        if (plLastRow > rowIndex) {
            rowIndex = plLastRow
        }

        sheet.setColumnWidth(0, 6500)

        for (column in 1..10) {
            sheet.setColumnWidth(column, 4200)
        }

        sheet.setColumnWidth(4, 4200)
        sheet.setColumnWidth(5, 6500)
        sheet.setColumnWidth(11, 5000)
    }

    val fileName = "${selectedGame ?: "LAKSHYA"}_LIMIT_${SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())}.xlsx"

    var downloadedUri: android.net.Uri? = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Lakshya"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Excel file create nahi ho payi")

        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Excel output stream nahi mila" }
            workbook.write(output)
        }

        val completedValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, completedValues, null, null)
        downloadedUri = uri

    } else {
        val baseDir =
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

        val lakshyaDir = java.io.File(baseDir, "Lakshya")
        if (!lakshyaDir.exists()) {
            lakshyaDir.mkdirs()
        }

        val outputFile = java.io.File(lakshyaDir, fileName)

        FileOutputStream(outputFile).use { output ->
            workbook.write(output)
        }

        // Scan old-Android download so it becomes visible to other apps.
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
            null
        )
    }

    workbook.close()

    // =====================================================
    // DOWNLOAD COMPLETE NOTIFICATION
    // Tap notification -> open the downloaded Excel directly.
    // =====================================================
    downloadedUri?.let { uri ->

        val channelId = "lakshya_excel_downloads"

        val notificationManager =
            context.getSystemService(
                android.content.Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Lakshya Excel Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lakshya Excel download complete"
            }

            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            fileName.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            channelId
        )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Excel Download Complete")
            .setContentText("Tap to open $fileName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Lakshya Excel downloaded. Tap here to open $fileName")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (notificationAllowed) {
            showDownloadNotification(
                notificationManager = notificationManager,
                fileName = fileName,
                notification = notification
            )
        }
    }

    return fileName
}


// SEARCH & REPORTS
// =====================================================

@Composable
fun SearchReportsScreen(

    savedEntries: SnapshotStateList<SavedEntry>,

    currentBusinessDayStart: Long,

    database: AppDatabase,

    currentUserId: String,

    currentUserRole: String,

    currentMasterUid: String,

    permissions: Map<String, Boolean>,

    onEditEntry: (SavedEntry) -> Unit,

    onBack: () -> Unit

) {

    val context =
        LocalContext.current

    fun employeeAllowed(key: String): Boolean =
        currentUserRole == "ADMIN" ||
                (permissions[key] ?: true)


    val coroutineScope =
        rememberCoroutineScope()

    val resultPrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_results_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    val paidPrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_chukara_paid_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    var paidRefresh by remember {
        mutableStateOf(0)
    }

    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val resultRegistration =
                CloudAccountSyncManager.listenLiveResults(
                    masterUid = currentMasterUid,
                    onUpdate = { liveResults, resultTimes ->
                        val editor = resultPrefs.edit()

                        resultGames().forEach { game ->
                            val value = liveResults[game].orEmpty()
                            val time = resultTimes[game] ?: 0L

                            if (value.isBlank()) {
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                            } else {
                                editor.putString(game, value)
                                editor.putLong("RESULT_TIME_$game", time)
                            }
                        }

                        editor.apply()
                        paidRefresh++
                    },
                    onError = { }
                )

            val paidRegistration =
                CloudAccountSyncManager.listenChukaraPaid(
                    masterUid = currentMasterUid,
                    onUpdate = { paidItems ->
                        val editor = paidPrefs.edit()
                        editor.clear()

                        paidItems.forEach { item ->
                            editor.putBoolean(item.paymentKey, true)
                            editor.putString("${item.paymentKey}_by", item.paidBy)
                            editor.putLong("${item.paymentKey}_time", item.paidTime)
                        }

                        editor.apply()
                        paidRefresh++
                    },
                    onError = { }
                )

            onDispose {
                resultRegistration?.remove()
                paidRegistration?.remove()
            }
        }
    }

    var entryToCancel by remember {
        mutableStateOf<SavedEntry?>(null)
    }

    var searchText by remember {
        mutableStateOf("")
    }


    val search =

        searchText
            .trim()
            .uppercase()


    /*
     * Search & Reports data isolation:
     * The current-day report must only use rows created after the last
     * CLOSE DAY. Older rows stay in Firebase/Room for Old Day Backup and
     * undo, but must not reappear in a fresh day's report when the bill
     * listener restores the complete account history.
     *
     * Master can search all current-day account entries. Employee can
     * search only their own current-day entries.
     *
     * Keep savedEntries itself shared/mutable because existing edit/cancel
     * logic updates it. Only the visible/searchable source is filtered.
     */
    val currentDayEntries = savedEntries.filter { entry ->
        entry.savedTime >= currentBusinessDayStart
    }

    val roleVisibleEntries =
        if (currentUserRole == "ADMIN") {
            currentDayEntries
        } else {
            currentDayEntries.filter { entry ->
                entry.createdBy.trim().equals(
                    currentUserId.trim(),
                    ignoreCase = true
                )
            }
        }

    val filteredEntries =

        if (
            search.isBlank()
        ) {

            roleVisibleEntries

        } else {

            roleVisibleEntries.filter {
                    savedEntry ->


                val customerMatch =

                    savedEntry
                        .customerName
                        .uppercase()
                        .contains(search)


                val gameMatch =

                    savedEntry
                        .games
                        .any { game ->

                            game
                                .uppercase()
                                .contains(search)

                        }


                val numberMatch =

                    savedEntry
                        .entries
                        .any { entry ->

                            entry
                                .number
                                .contains(search)

                        }


                val typeMatch =

                    savedEntry
                        .entries
                        .any { entry ->

                            entry
                                .entryType
                                .uppercase()
                                .contains(search)

                        }


                customerMatch ||
                        gameMatch ||
                        numberMatch ||
                        typeMatch

            }

        }


    val filteredTotal =

        filteredEntries
            .sumOf {

                it.grandTotal

            }


    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp)

    ) {


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Text(

            text =
                "SEARCH & REPORTS",

            fontSize = 28.sp

        )


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        OutlinedTextField(

            value =
                searchText,

            onValueChange = {

                searchText = it

            },

            label = {

                Text(
                    "Search Customer / Game / Number"
                )

            },

            placeholder = {

                Text(
                    "Example: Raj, MO, 123"
                )

            },

            modifier =
                Modifier.fillMaxWidth(),

            singleLine = true

        )


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {


            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)

            ) {


                Text(

                    text =
                        "Found Entries: ${filteredEntries.size}",

                    fontSize = 17.sp

                )


                Text(

                    text =
                        "Total Amount: ₹$filteredTotal",

                    fontSize = 20.sp

                )

            }

        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )

        // =====================================================
        // COMPACT CUSTOMER-WISE CHUKARA SUMMARY
        // =====================================================
        val customerChukaraSummary = filteredEntries
            .filter { it.status == "ACTIVE" }
            .mapNotNull { entry ->
                val wins = calculateEntryChukara(entry, resultPrefs)
                if (wins.isEmpty()) null else Triple(entry.customerName, entry, wins)
            }
            .groupBy { it.second.id }

        if (
            employeeAllowed("CHUKARA") &&
            customerChukaraSummary.isNotEmpty()
        ) {
            Text(text = "AAJ KA CHUKARA", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            customerChukaraSummary.forEach { (_, items) ->
                val customerName = items.first().first
                val slipId = items.first().second.id
                val allWins = items.flatMap { it.third }
                val totalChukara = allWins.sumOf { it.chukaraAmount }
                val allPaid = allWins.all { paidPrefs.getBoolean(it.paymentKey, false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "#$slipId  $customerName",
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "₹$totalChukara",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        if (allPaid) {
                            Text(text = "✓ PAID", fontSize = 15.sp)
                        } else {
                            Button(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    val editor = paidPrefs.edit()
                                    allWins.forEach { win ->
                                        if (!paidPrefs.getBoolean(win.paymentKey, false)) {
                                            editor
                                                .putBoolean(win.paymentKey, true)
                                                .putString("${win.paymentKey}_by", currentUserId)
                                                .putLong("${win.paymentKey}_time", now)
                                        }
                                    }
                                    editor.apply()

                                    allWins.forEach { win ->
                                        CloudAccountSyncManager.markChukaraPaid(
                                            masterUid = currentMasterUid,
                                            paymentKey = win.paymentKey,
                                            paidBy = currentUserId,
                                            paidTime = now,
                                            amount = win.chukaraAmount
                                        )
                                    }

                                    paidRefresh++
                                    Toast.makeText(
                                        context,
                                        "$customerName Chukara Paid ₹$totalChukara",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("PAY", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(15.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (
            filteredEntries.isEmpty()
        ) {


            Text(

                text =
                    "No matching records found",

                fontSize = 17.sp

            )


        } else {


            filteredEntries
                .reversed()
                .forEach {
                        savedEntry ->


                    val savedDate =

                        SimpleDateFormat(
                            "dd-MM-yyyy",
                            Locale.getDefault()
                        ).format(
                            Date(
                                savedEntry.savedTime
                            )
                        )


                    val savedTime =

                        SimpleDateFormat(
                            "hh:mm:ss a",
                            Locale.getDefault()
                        ).format(
                            Date(
                                savedEntry.savedTime
                            )
                        )


                    Card(

                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 7.dp
                            )

                    ) {


                        Column(

                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)

                        ) {


                            Text(

                                text =
                                    "SLIP #${savedEntry.id} • ${savedEntry.customerName}",

                                fontSize = 21.sp

                            )


                            Spacer(
                                modifier =
                                    Modifier.height(5.dp)
                            )


                            Text(
                                text =
                                    "Date: $savedDate"
                            )


                            Text(
                                text =
                                    "Time: $savedTime"
                            )


                            Text(

                                text =
                                    "Games: ${
                                        savedEntry.games
                                            .joinToString(", ")
                                    }"

                            )


                            Text(
                                text =
                                    "Status: ${savedEntry.status}"
                            )


                            Text(
                                text =
                                    "Created By: ${
                                        if (savedEntry.createdBy.isBlank()) "OLD RECORD"
                                        else savedEntry.createdBy
                                    }"
                            )


                            if (savedEntry.isEdited) {

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text = "✏️ EDITED",
                                    fontSize = 17.sp
                                )

                                Text(
                                    text = "Edited By: ${
                                        if (savedEntry.lastEditedBy.isBlank()) "UNKNOWN"
                                        else savedEntry.lastEditedBy
                                    }"
                                )

                                savedEntry.lastEditedTime?.let { editedAt ->
                                    Text(
                                        text = "Edited Time: ${
                                            SimpleDateFormat(
                                                "dd-MM-yyyy hh:mm:ss a",
                                                Locale.getDefault()
                                            ).format(Date(editedAt))
                                        }"
                                    )
                                }
                            }


                            if (
                                savedEntry.status == "CANCELLED"
                            ) {

                                Text(
                                    text =
                                        "Cancelled By: ${savedEntry.cancelledBy}"
                                )

                                val cancelledAt =
                                    savedEntry.cancelledTime

                                if (cancelledAt != null) {

                                    Text(
                                        text =
                                            "Cancelled: ${
                                                SimpleDateFormat(
                                                    "dd-MM-yyyy hh:mm:ss a",
                                                    Locale.getDefault()
                                                ).format(
                                                    Date(cancelledAt)
                                                )
                                            }"
                                    )
                                }
                            }


                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )


                            HorizontalDivider()


                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )


                            listOf(
                                "Single",
                                "Jodi",
                                "Pana"
                            ).forEach {
                                    type ->


                                val typeEntries =

                                    savedEntry
                                        .entries
                                        .filter {

                                            it.entryType ==
                                                    type

                                        }


                                if (
                                    typeEntries
                                        .isNotEmpty()
                                ) {


                                    Text(

                                        text =
                                            type.uppercase(),

                                        fontSize = 17.sp

                                    )


                                    val grouped =

                                        typeEntries
                                            .groupBy {

                                                it.amount

                                            }


                                    grouped.forEach {
                                            (amount, entries) ->


                                        val numbers =

                                            entries
                                                .joinToString(
                                                    " "
                                                ) {

                                                    it.number

                                                }


                                        Text(

                                            text =
                                                "$numbers = ₹$amount"

                                        )

                                    }


                                    Spacer(
                                        modifier =
                                            Modifier.height(8.dp)
                                    )

                                }

                            }


                            HorizontalDivider()


                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )


                            Text(

                                text =
                                    "Per Game: ₹${savedEntry.perGameTotal}",

                                fontSize = 16.sp

                            )


                            Text(

                                text =
                                    "Grand Total: ₹${savedEntry.grandTotal}",

                                fontSize = 19.sp

                            )


                            val winningChukara = remember(savedEntry, paidRefresh) {
                                calculateEntryChukara(savedEntry, resultPrefs)
                            }

                            if (winningChukara.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(7.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(text = "CHUKARA", fontSize = 15.sp)

                                winningChukara.forEach { win ->
                                    val isPaid = paidPrefs.getBoolean(win.paymentKey, false)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${win.game} ${win.entryType.uppercase()} ${win.number} = ₹${win.chukaraAmount}",
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isPaid) {
                                            Text(text = "✓ PAID", fontSize = 12.sp)
                                        } else {
                                            Button(
                                                onClick = {
                                                    val now = System.currentTimeMillis()
                                                    paidPrefs.edit()
                                                        .putBoolean(win.paymentKey, true)
                                                        .putString("${win.paymentKey}_by", currentUserId)
                                                        .putLong("${win.paymentKey}_time", now)
                                                        .apply()

                                                    CloudAccountSyncManager.markChukaraPaid(
                                                        masterUid = currentMasterUid,
                                                        paymentKey = win.paymentKey,
                                                        paidBy = currentUserId,
                                                        paidTime = now,
                                                        amount = win.chukaraAmount
                                                    )

                                                    paidRefresh++
                                                    Toast.makeText(
                                                        context,
                                                        "Chukara Paid ₹${win.chukaraAmount}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("PAY", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                val totalWin = winningChukara.sumOf { it.chukaraAmount }
                                val totalPaid = winningChukara
                                    .filter { paidPrefs.getBoolean(it.paymentKey, false) }
                                    .sumOf { it.chukaraAmount }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Total: ₹$totalWin", fontSize = 14.sp)
                                    if (totalPaid > 0) {
                                        Text(text = "Paid: ₹$totalPaid", fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )


                            // =========================================
                            // FINAL EDIT SECURITY
                            // A printed slip may be corrected and printed again.
                            // A declared result or closed day locks edits permanently.
                            // =========================================

                            val hasDeclaredResult = savedEntry.games.any { game ->
                                val pairedOpenGame =
                                    openGameForCloseGame(game)

                                if (pairedOpenGame != null) {
                                    // CLOSE GAME entry (MC/NC/RDC/KC/KNC/RC/MBC):
                                    // lock only when paired OPEN game CURRENTLY has
                                    // a full result such as 123-66-330.
                                    //
                                    // If MO is corrected back to 123-6,
                                    // MC entry becomes editable/cancellable again.
                                    val pairedOpenResult =
                                        resultPrefs.getString(
                                            pairedOpenGame,
                                            ""
                                        ).orEmpty().trim()

                                    pairedOpenResult
                                        .split("-")
                                        .size == 3
                                } else {
                                    // OPEN GAME entry:
                                    // open result 123-6 (or full result) locks it.
                                    resultPrefs.getLong(
                                        "RESULT_TIME_$game",
                                        0L
                                    ) > 0L ||
                                            !resultPrefs.getString(
                                                game,
                                                ""
                                            ).isNullOrBlank()
                                }
                            }

                            val editLocked =
                                savedEntry.isDayLocked ||
                                        hasDeclaredResult

                            if (savedEntry.status == "ACTIVE") {

                                if (!editLocked) {
                                    Button(
                                        onClick = {
                                            onEditEntry(savedEntry)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "EDIT ENTRY")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                } else {
                                    Text(
                                        text = when {
                                            savedEntry.isDayLocked ->
                                                "🔒 LOCKED - DAY CLOSED"

                                            hasDeclaredResult ->
                                                "🔒 LOCKED - RESULT DECLARED"

                                            else ->
                                                "🔒 PRINTED - EDIT LOCKED"
                                        },
                                        fontSize = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // RESULT/AKDA DECLARED = FULL PERMANENT LOCK.
                                // Result declared hone ke baad CANCEL bhi allowed nahi hai.
                                if (!hasDeclaredResult && !savedEntry.isDayLocked) {
                                    Button(
                                        onClick = {
                                            entryToCancel = savedEntry
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "CANCEL ENTRY")
                                    }
                                }
                            }

                        }

                    }

                }

        }


        if (entryToCancel != null) {

            AlertDialog(

                onDismissRequest = {
                    entryToCancel = null
                },

                title = {
                    Text(
                        "Cancel Entry?"
                    )
                },

                text = {
                    Text(
                        "This entry will be marked CANCELLED and kept in history. Continue?"
                    )
                },

                confirmButton = {

                    Button(

                        onClick = {

                            val entry =
                                entryToCancel
                                    ?: return@Button

                            coroutineScope.launch {

                                val cancelTime =
                                    System.currentTimeMillis()

                                database
                                    .billDao()
                                    .cancelBill(
                                        billId = entry.id,
                                        masterUid = currentMasterUid,
                                        cancelledBy = currentUserId,
                                        cancelledTime = cancelTime
                                    )

                                if (currentMasterUid.isNotBlank()) {
                                    CloudBillManager.cancelBill(
                                        masterUid = currentMasterUid,
                                        localBillId = entry.id,
                                        savedTime = entry.savedTime,
                                        cancelledBy = currentUserId,
                                        cancelledTime = cancelTime,
                                        onSuccess = {},
                                        onError = { message ->
                                            Toast.makeText(context, "Cancel sync failed: $message", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }

                                val index =
                                    savedEntries.indexOfFirst {
                                        it.id == entry.id
                                    }

                                if (index >= 0) {

                                    savedEntries[index] =
                                        entry.copy(
                                            status = "CANCELLED",
                                            cancelledBy = currentUserId,
                                            cancelledTime = cancelTime
                                        )
                                }

                                entryToCancel = null

                                Toast.makeText(
                                    context,
                                    "Entry Cancelled - Record kept in history",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                    ) {

                        Text(
                            "CANCEL ENTRY"
                        )
                    }
                },

                dismissButton = {

                    OutlinedButton(

                        onClick = {
                            entryToCancel = null
                        }

                    ) {

                        Text(
                            "CANCEL"
                        )
                    }
                }
            )
        }


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        OutlinedButton(

            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "BACK TO DASHBOARD"
            )

        }


        Spacer(
            modifier = Modifier.height(30.dp)
        )

    }

}


// =====================================================
// SECURE EDIT ENTRY SCREEN
// =====================================================

@Composable
fun EditEntryScreen(

    entry: SavedEntry,

    database: AppDatabase,

    currentUserId: String,

    currentMasterUid: String,

    savedEntries:
    SnapshotStateList<SavedEntry>,

    onUpdated: (SavedEntry) -> Unit,

    onPrintPreview: (PrintPreviewData) -> Unit,

    onBack: () -> Unit

) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun entryText(source: List<NumberAmountEntry>): String =
        source
            .groupBy { it.entryType }
            .entries
            .joinToString("\n") { (entryType, items) ->
                buildString {
                    append(entryType)
                    append("\n")
                    append(
                        items.joinToString("\n") {
                            "${it.number}=${displayAmount(it.actualAmount)}"
                        }
                    )
                }
            }

    var customerName by remember(entry.id) {
        mutableStateOf(entry.customerName)
    }

    var gameInput by remember(entry.id) {
        mutableStateOf(entry.games.joinToString(" "))
    }

    var rawEntry by remember(entry.id) {
        mutableStateOf(entryText(entry.entries))
    }

    // HISTORY EDIT RULE:
    // Screen open = UPDATE active, PRINT locked.
    // Successful UPDATE = UPDATE locked, PRINT active.
    // Update ke baad koi bhi field badli = UPDATE active, PRINT locked again.
    var latestUpdateSaved by remember(entry.id) {
        mutableStateOf(false)
    }

    var isUpdating by remember(entry.id) {
        mutableStateOf(false)
    }

    var latestSavedEntry by remember(entry.id) {
        mutableStateOf(entry)
    }

    LaunchedEffect(entry.isPrinted, entry.printedTime) {
        if (entry.isPrinted) {
            latestSavedEntry = entry
            latestUpdateSaved = false
        }
    }

    // =====================================================
    // RESULT LOCK - EDIT SCREEN REAL-TIME SECURITY
    // If Admin declares a result while this screen is open,
    // UPDATE and PRINT must lock immediately.
    // =====================================================
    val editResultPrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_results_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    var editResultRefresh by remember(currentMasterUid) {
        mutableIntStateOf(0)
    }

    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val registration =
                CloudAccountSyncManager.listenLiveResults(
                    masterUid = currentMasterUid,
                    onUpdate = { liveResults, resultTimes ->
                        val editor = editResultPrefs.edit()

                        resultGames().forEach { game ->
                            val value = liveResults[game].orEmpty()
                            val time = resultTimes[game] ?: 0L

                            if (value.isBlank()) {
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                            } else {
                                editor.putString(game, value)
                                editor.putLong("RESULT_TIME_$game", time)
                            }
                        }

                        editor.apply()
                        editResultRefresh++
                    },
                    onError = { }
                )

            onDispose {
                registration?.remove()
            }
        }
    }

    fun entryResultLocked(): Boolean {
        editResultRefresh

        return latestSavedEntry.games.any { game ->
            val pairedOpenGame = openGameForCloseGame(game)

            if (pairedOpenGame != null) {
                val pairedOpenResult =
                    editResultPrefs.getString(
                        pairedOpenGame,
                        ""
                    ).orEmpty().trim()

                pairedOpenResult.split("-").size == 3
            } else {
                editResultPrefs.getLong(
                    "RESULT_TIME_$game",
                    0L
                ) > 0L ||
                        !editResultPrefs.getString(
                            game,
                            ""
                        ).isNullOrBlank()
            }
        }
    }

    val selectedGames = remember(gameInput) {
        parseGames(gameInput)
    }

    val parsedResult = remember(rawEntry) {
        parseMixedEntries(rawEntry)
    }

    val parsedEntries = parsedResult.entries

    // Keep 12.5 as 12.5 in entries; only the final payable total is rounded up.
    val perGameTotal = kotlin.math.ceil(parsedEntries.sumOf { it.actualAmount }).toInt()

    val grandTotal = perGameTotal * selectedGames.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "EDIT ENTRY",
            fontSize = 28.sp
        )

        Text(text = "Entry ID: ${entry.id}")
        Text(text = "Editing By: $currentUserId")

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = customerName,
            onValueChange = {
                customerName = it
                latestUpdateSaved = false
            },
            label = { Text("Customer Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = gameInput,
            onValueChange = {
                gameInput = it
                latestUpdateSaved = false
            },
            label = { Text("Game Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = rawEntry,
            onValueChange = {
                rawEntry = it
                latestUpdateSaved = false
            },
            label = {
                Text("Single / Jodi / Pana Entries")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Games: ${selectedGames.joinToString(", ")}")
        Text(text = "Per Game Total: ₹$perGameTotal")
        Text(
            text = "Grand Total: ₹$grandTotal",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                when {
                    entryResultLocked() -> {
                        Toast.makeText(
                            context,
                            "RESULT DECLARED: Edit/Update is locked",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    entry.isDayLocked -> {
                        Toast.makeText(
                            context,
                            "DAY CLOSED: This entry is permanently locked",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    entry.status != "ACTIVE" -> {
                        Toast.makeText(
                            context,
                            "Cancelled entry cannot be edited",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    customerName.trim().isBlank() -> {
                        Toast.makeText(
                            context,
                            "Enter Customer Name",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    selectedGames.isEmpty() -> {
                        Toast.makeText(
                            context,
                            "Invalid Game Name",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    parsedResult.invalidNumbers.isNotEmpty() ||
                            parsedResult.invalidEntryTypes.isNotEmpty() -> {
                        Toast.makeText(
                            context,
                            "Invalid Entry: ${
                                (parsedResult.invalidEntryTypes + parsedResult.invalidNumbers)
                                    .joinToString(", ")
                            }",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    parsedEntries.isEmpty() -> {
                        Toast.makeText(
                            context,
                            "Enter valid entry data",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        isUpdating = true

                        coroutineScope.launch {
                            try {
                                val oldSaved = latestSavedEntry
                                val editTime = System.currentTimeMillis()

                                // Update se pehle purana saved version Edit History me rakho.
                                database.editHistoryDao().insertHistory(
                                    EditHistoryEntity(
                                        billId = oldSaved.id,
                                        oldCustomerName = oldSaved.customerName,
                                        oldGames = serializeGames(oldSaved.games),
                                        oldEntries = serializeEntries(oldSaved.entries),
                                        oldPerGameTotal = oldSaved.perGameTotal,
                                        oldGrandTotal = oldSaved.grandTotal,
                                        newCustomerName = customerName.trim(),
                                        newGames = serializeGames(selectedGames),
                                        newEntries = serializeEntries(parsedEntries),
                                        newPerGameTotal = perGameTotal,
                                        newGrandTotal = grandTotal,
                                        editedBy = currentUserId
                                    )
                                )

                                // Same audit record cloud me bhi save hota hai,
                                // isliye same Master ID ke har mobile par Edit History same rahegi.
                                CloudEditHistoryManager.saveHistory(
                                    masterUid = currentMasterUid,
                                    record = CloudEditHistoryRecord(
                                        billId = oldSaved.id,
                                        oldCustomerName = oldSaved.customerName,
                                        oldGames = serializeGames(oldSaved.games),
                                        oldEntries = serializeEntries(oldSaved.entries),
                                        oldPerGameTotal = oldSaved.perGameTotal,
                                        oldGrandTotal = oldSaved.grandTotal,
                                        newCustomerName = customerName.trim(),
                                        newGames = serializeGames(selectedGames),
                                        newEntries = serializeEntries(parsedEntries),
                                        newPerGameTotal = perGameTotal,
                                        newGrandTotal = grandTotal,
                                        editedBy = currentUserId,
                                        editedTime = editTime
                                    ),
                                    onError = { message ->
                                        Toast.makeText(
                                            context,
                                            "Edit history sync: $message",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )

                                // Edited version abhi print nahi hua hai.
                                // Isliye previous print status reset kar rahe hain.
                                val updatedBill = BillEntity(
                                    id = oldSaved.id,
                                    masterUid = currentMasterUid,
                                    customerName = customerName.trim(),
                                    games = serializeGames(selectedGames),
                                    entries = serializeEntries(parsedEntries),
                                    perGameTotal = perGameTotal,
                                    grandTotal = grandTotal,
                                    savedTime = oldSaved.savedTime,
                                    status = oldSaved.status,
                                    createdBy = oldSaved.createdBy,
                                    cancelledBy = oldSaved.cancelledBy,
                                    cancelledTime = oldSaved.cancelledTime,
                                    isEdited = true,
                                    lastEditedBy = currentUserId,
                                    lastEditedTime = editTime,
                                    isPrinted = false,
                                    printedBy = "",
                                    printedTime = null,
                                    printCount = oldSaved.printCount,
                                    isDayLocked = oldSaved.isDayLocked,
                                    dayLockedBy = oldSaved.dayLockedBy,
                                    dayLockedTime = oldSaved.dayLockedTime
                                )

                                database.billDao().updateBill(updatedBill)

                                val updatedSavedEntry = oldSaved.copy(
                                    customerName = customerName.trim(),
                                    games = selectedGames.toList(),
                                    entries = parsedEntries.toList(),
                                    perGameTotal = perGameTotal,
                                    grandTotal = grandTotal,
                                    isEdited = true,
                                    lastEditedBy = currentUserId,
                                    lastEditedTime = editTime,
                                    isPrinted = false,
                                    printedBy = "",
                                    printedTime = null
                                )

                                val index = savedEntries.indexOfFirst {
                                    it.id == oldSaved.id
                                }

                                if (index >= 0) {
                                    savedEntries[index] = updatedSavedEntry
                                }

                                latestSavedEntry = updatedSavedEntry
                                onUpdated(updatedSavedEntry)

                                if (currentMasterUid.isBlank()) {
                                    latestUpdateSaved = false
                                    Toast.makeText(
                                        context,
                                        "Update sync failed. Please login again and retry.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    CloudBillManager.updateBill(
                                        entry = updatedSavedEntry,
                                        masterUid = currentMasterUid,
                                        onSuccess = {
                                            latestUpdateSaved = true
                                            Toast.makeText(
                                                context,
                                                "Entry Updated Successfully",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onError = { message ->
                                            latestUpdateSaved = false
                                            Toast.makeText(
                                                context,
                                                "Update sync failed: $message",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                latestUpdateSaved = false
                                Toast.makeText(
                                    context,
                                    "Update Failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isUpdating = false
                            }
                        }
                    }
                }
            },
            enabled =
                !latestUpdateSaved &&
                        !isUpdating &&
                        !entry.isDayLocked &&
                        !entryResultLocked(),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = if (isUpdating) "UPDATING..." else "UPDATE / SAVE",
                fontSize = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val saved = latestSavedEntry
                val receiptDate = Date(saved.savedTime)

                onPrintPreview(
                    PrintPreviewData(
                        billId = saved.id,
                        customerName = saved.customerName,
                        selectedGames = saved.games,
                        parsedEntries = saved.entries,
                        grandTotal = saved.grandTotal,
                        date = SimpleDateFormat(
                            "dd-MM-yyyy",
                            Locale.getDefault()
                        ).format(receiptDate),
                        time = SimpleDateFormat(
                            "hh:mm:ss a",
                            Locale.getDefault()
                        ).format(receiptDate)
                    )
                )
            },
            enabled =
                latestUpdateSaved &&
                        !latestSavedEntry.isPrinted &&
                        !isUpdating &&
                        !entry.isDayLocked &&
                        !entryResultLocked(),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "PRINT RECEIPT",
                fontSize = 17.sp
            )
        }

        if (!latestUpdateSaved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PRINT locked - pehle UPDATE / SAVE karein",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Latest changes saved - PRINT ready",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "BACK TO HISTORY")
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}


// =====================================================
// NEW ENTRY SCREEN
// =====================================================

@Composable
fun NewEntryScreen(

    savedEntries:
    SnapshotStateList<SavedEntry>,

    database: AppDatabase,

    currentUserId: String,

    currentMasterUid: String,

    initialSavedData: PrintPreviewData? = null,

    onPrintPreview:
        (PrintPreviewData) -> Unit,

    onBack: () -> Unit

) {


    var customerName by remember {
        mutableStateOf(initialSavedData?.customerName.orEmpty())
    }


    var gameInput by remember {
        mutableStateOf(initialSavedData?.selectedGames?.joinToString(" ").orEmpty())
    }


    var rawEntry by remember {
        mutableStateOf(
            initialSavedData?.parsedEntries
                ?.groupBy { it.actualAmount to it.entryType }
                ?.entries
                ?.joinToString("\n") { (key, items) ->
                    items.joinToString(" ") { it.number } + "=${displayAmount(key.first)}"
                }
                .orEmpty()
        )
    }

    var savedPreviewData by remember { mutableStateOf(initialSavedData) }
    var isEntrySaved by remember { mutableStateOf(initialSavedData != null && initialSavedData.billId > 0) }
    var quickPanaType by remember { mutableStateOf("SP") }
    var quickPanaAnk by remember { mutableStateOf("0") }
    var quickPanaAmount by remember { mutableStateOf("") }
    var quickPanaTypeMenuExpanded by remember { mutableStateOf(false) }
    var quickPanaAnkMenuExpanded by remember { mutableStateOf(false) }


    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    val resultPrefs = remember(currentMasterUid) {
        context.getSharedPreferences("lakshya_results_$currentMasterUid", android.content.Context.MODE_PRIVATE)
    }

    var cloudResultRefresh by remember { mutableIntStateOf(0) }

    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val registration =
                CloudAccountSyncManager.listenLiveResults(
                    masterUid = currentMasterUid,
                    onUpdate = { liveResults, resultTimes ->
                        val editor = resultPrefs.edit()

                        resultGames().forEach { game ->
                            val value = liveResults[game].orEmpty()
                            val time = resultTimes[game] ?: 0L

                            if (value.isBlank()) {
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                            } else {
                                editor.putString(game, value)
                                editor.putLong("RESULT_TIME_$game", time)
                            }
                        }

                        editor.apply()
                        cloudResultRefresh++
                    },
                    onError = { }
                )

            onDispose {
                registration?.remove()
            }
        }
    }

    fun lockedGames(games: List<String>): List<String> = games.filter { game ->
        cloudResultRefresh

        val pairedOpenGame =
            openGameForCloseGame(game)

        if (pairedOpenGame != null) {
            // CLOSE GAME:
            // MC/NC/RDC/KC/KNC/RC/MBC is closed ONLY when its paired
            // OPEN game currently has a FULL result such as 123-66-330.
            //
            // This intentionally ignores an old/stale RESULT_TIME_MC etc.
            // So if Admin corrects MO back to 123-6, MC immediately reopens.
            val openResult =
                resultPrefs.getString(
                    pairedOpenGame,
                    ""
                ).orEmpty().trim()

            openResult.split("-").size == 3
        } else {
            // OPEN GAME:
            // Once its open result (123-6) is declared, new OPEN entries stop.
            resultPrefs.getLong(
                "RESULT_TIME_$game",
                0L
            ) > 0L ||
                    !resultPrefs.getString(
                        game,
                        ""
                    ).isNullOrBlank()
        }
    }


    val selectedGames =
        remember(gameInput) {

            parseGames(
                gameInput
            )
        }


    val invalidGames =
        remember(gameInput) {

            findInvalidGames(
                gameInput
            )
        }

    // Customer name and a valid game are required before money/number fields
    // can be used.
    val canEnterNumbers =
        !isEntrySaved &&
                customerName.trim().isNotEmpty() &&
                selectedGames.isNotEmpty() &&
                invalidGames.isEmpty()


    // RESULT DECLARED GAMES - SHOW INVALID IMMEDIATELY WHILE TYPING
    val closedSelectedGames = lockedGames(selectedGames)


    // Large entries: do not run the full parser on every single key press.
    // Wait briefly after typing stops, then parse in background.
    var parseResult by remember {
        mutableStateOf(
            MixedParseResult(
                entries = emptyList(),
                invalidNumbers = emptyList(),
                invalidEntryTypes = emptyList()
            )
        )
    }

    var isParsingEntry by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(rawEntry) {
        isParsingEntry = true

        // Debounce typing so 100+ entries do not freeze Compose.
        kotlinx.coroutines.delay(300)

        val source = rawEntry

        parseResult =
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.Default
            ) {
                parseMixedEntries(source)
            }

        isParsingEntry = false
    }


    val parsedEntries =
        parseResult.entries


    val invalidNumbers =
        parseResult.invalidNumbers


    val invalidEntryTypes =
        parseResult.invalidEntryTypes


    val perGameTotal =

        kotlin.math.ceil(parsedEntries.sumOf { it.actualAmount }).toInt()


    val gameCount =

        selectedGames.size


    val grandTotal =

        perGameTotal *
                gameCount


    val typesUsed =

        parsedEntries
            .map {

                it.entryType

            }
            .distinct()


    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp)

    ) {


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Text(
            text = "NEW ENTRY",
            fontSize = 30.sp
        )


        Text(
            text =
                "Enter customer game details",
            fontSize = 16.sp
        )


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        OutlinedTextField(

            value =
                customerName,

            onValueChange = {

                customerName = it

            },

            label = {

                Text(
                    "Customer Name"
                )

            },

            modifier =
                Modifier.fillMaxWidth(),

            enabled = !isEntrySaved,

            singleLine = true

        )


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        OutlinedTextField(

            value =
                gameInput,

            onValueChange = {

                gameInput =
                    it.uppercase()

            },

            label = {

                Text(
                    "Game Code - Example: MO KO"
                )

            },

            placeholder = {

                Text(
                    "MO KO KC"
                )

            },

            modifier =
                Modifier.fillMaxWidth(),

            enabled = !isEntrySaved,

            singleLine = true

        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(

            text =
                "Games: MO, NO, RDO, KO, MC, NC, RDC, KC, " +
                        "KNO, RO, MBO, KNC, RC, MBC",

            fontSize = 12.sp

        )


        if (
            selectedGames.isNotEmpty()
        ) {


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            Text(

                text =
                    "Selected Games: " +
                            selectedGames
                                .joinToString(
                                    ", "
                                ),

                fontSize = 16.sp

            )
        }


        if (
            invalidGames.isNotEmpty()
        ) {


            Spacer(
                modifier = Modifier.height(5.dp)
            )


            Text(

                text =
                    "Invalid Game: " +
                            invalidGames
                                .joinToString(
                                    ", "
                                ),

                color =
                    MaterialTheme
                        .colorScheme
                        .error,

                fontSize = 14.sp

            )
        }


        if (closedSelectedGames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = closedSelectedGames.joinToString(", ") { "$it INVALID - RESULT DECLARED / ENTRY CLOSED" },
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Text(
            text = "Entry Type",
            fontSize = 17.sp
        )


        Spacer(
            modifier = Modifier.height(5.dp)
        )


        Text(
            text =
                "S / SINGLE = Single",
            fontSize = 14.sp
        )


        Text(
            text =
                "J / JODI = Jodi",
            fontSize = 14.sp
        )


        Text(
            text =
                "P / PANA / PANE = Pana",
            fontSize = 14.sp
        )

        // Chart controls are compact dropdowns so the entry screen stays clean.
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Quick Pana Chart", fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { quickPanaTypeMenuExpanded = true }, enabled = !isEntrySaved) {
                    Text("Type: $quickPanaType ▼")
                }
                DropdownMenu(expanded = quickPanaTypeMenuExpanded, onDismissRequest = { quickPanaTypeMenuExpanded = false }) {
                    listOf("SP", "DP", "TP").forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = {
                            quickPanaType = type
                            quickPanaTypeMenuExpanded = false
                        })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { quickPanaAnkMenuExpanded = true }, enabled = !isEntrySaved) {
                    Text("Ank: $quickPanaAnk ▼")
                }
                DropdownMenu(expanded = quickPanaAnkMenuExpanded, onDismissRequest = { quickPanaAnkMenuExpanded = false }) {
                    (0..9).forEach { ank ->
                        DropdownMenuItem(text = { Text(ank.toString()) }, onClick = {
                            quickPanaAnk = ank.toString()
                            quickPanaAnkMenuExpanded = false
                        })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = quickPanaAmount,
                onValueChange = { quickPanaAmount = it },
                label = { Text("Amount") },
                placeholder = { Text("12.5") },
                modifier = Modifier.weight(1f),
                enabled = canEnterNumbers
            )
            Button(onClick = {
                val amount = quickPanaAmount.trim()
                val numbers = quickPanaChart[quickPanaType]?.get(quickPanaAnk).orEmpty()
                if (amount.toDoubleOrNull()?.let { it > 0 } == true && numbers.isNotBlank()) {
                    rawEntry = (rawEntry.trimEnd() + "\nPANA\n$numbers=$amount").trimStart()
                } else Toast.makeText(context, "Enter a valid Pana amount first", Toast.LENGTH_SHORT).show()
            }, enabled = canEnterNumbers) { Text("ADD") }
        }


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        OutlinedTextField(

            value =
                rawEntry,

            onValueChange = {

                rawEntry =
                    it.uppercase()

            },

            label = {

                Text(
                    "Single / Jodi / Pana Entry"
                )

            },

            placeholder = {

                Text(
                    "SINGLE\n" +
                            "1 3 5=20\n" +
                            "JODI\n" +
                            "12 32 58=50\n" +
                            "PANA\n" +
                            "123 456 890=100"
                )

            },

            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),

            enabled = canEnterNumbers,

            isError = !isEntrySaved && !canEnterNumbers,

            supportingText = {
                if (!isEntrySaved && !canEnterNumbers) {
                    Text("Entry likhne se pehle Customer Name aur valid Game Code bharein.")
                }
            },

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Text
                )

        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (isParsingEntry) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "Checking entries...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        Text(
            text =
                "Short Form Example:",
            fontSize = 14.sp
        )


        Text(
            text = "S → 1 3 5=20",
            fontSize = 14.sp
        )


        Text(
            text = "J → 12 32 58=50",
            fontSize = 14.sp
        )


        Text(
            text = "P → 123 456 890=100",
            fontSize = 14.sp
        )


        if (
            invalidNumbers.isNotEmpty()
        ) {


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            Text(

                text =
                    "Invalid Number: " +
                            invalidNumbers
                                .joinToString(
                                    ", "
                                ),

                color =
                    MaterialTheme
                        .colorScheme
                        .error,

                fontSize = 16.sp

            )
        }


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        Text(
            text = "ENTRY PREVIEW",
            fontSize = 20.sp
        )


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        Text(

            text =
                "Customer: " +

                        if (
                            customerName.isBlank()
                        ) {

                            "-"

                        } else {

                            customerName
                        },

            fontSize = 17.sp

        )


        Text(

            text =
                "Games: " +

                        if (
                            selectedGames.isEmpty()
                        ) {

                            "-"

                        } else {

                            selectedGames
                                .joinToString(
                                    ", "
                                )
                        },

            fontSize = 17.sp

        )


        Text(

            text =
                "Types: " +

                        if (
                            typesUsed.isEmpty()
                        ) {

                            "-"

                        } else {

                            typesUsed
                                .joinToString(
                                    ", "
                                )
                        },

            fontSize = 17.sp

        )


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        if (
            parsedEntries.isEmpty()
        ) {


            Text(
                text =
                    "No valid entries"
            )


        } else {


            val previewLimit = 150
            val previewEntries = parsedEntries.take(previewLimit)

            listOf(
                "Single",
                "Jodi",
                "Pana"
            ).forEach {
                    type ->


                val typeEntries =

                    previewEntries
                        .filter {

                            it.entryType ==
                                    type

                        }


                if (
                    typeEntries.isNotEmpty()
                ) {


                    Text(

                        text =
                            type.uppercase(),

                        fontSize = 18.sp

                    )


                    val grouped =

                        typeEntries
                            .groupBy {

                                it.actualAmount

                            }


                    grouped.forEach {
                            (amount, entries) ->


                        val numbers =

                            entries
                                .joinToString(
                                    separator = " "
                                ) {

                                    it.number

                                }


                        Text(

                            text =
                                "$numbers = ₹${displayAmount(amount)}",

                            fontSize = 17.sp

                        )
                    }


                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )
                }
            }

            if (parsedEntries.size > previewLimit) {
                Text(
                    text = "Preview showing first $previewLimit of ${parsedEntries.size} entries. All entries will still be saved.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )
            }
        }


        HorizontalDivider()


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        Text(

            text =
                "Entries: ${parsedEntries.size}" +
                        "   |   Per Game: ₹$perGameTotal",

            fontSize = 16.sp

        )


        Spacer(
            modifier = Modifier.height(5.dp)
        )


        Text(

            text =
                "Games: $gameCount" +
                        "   |   Grand Total: ₹$grandTotal",

            fontSize = 19.sp

        )


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        // SAVE ENTRY

        Button(

            onClick = {


                when {


                    isParsingEntry -> {

                        Toast.makeText(
                            context,
                            "Entries are still being checked. Please wait a moment.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    customerName.isBlank() -> {


                        Toast.makeText(
                            context,
                            "Enter Customer Name",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    gameInput.isBlank() -> {


                        Toast.makeText(
                            context,
                            "Enter Game Code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    invalidGames.isNotEmpty() -> {


                        Toast.makeText(
                            context,
                            "Invalid Game Code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    selectedGames.isEmpty() -> {


                        Toast.makeText(
                            context,
                            "Enter valid Game",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    invalidEntryTypes.isNotEmpty() -> {


                        Toast.makeText(
                            context,
                            "Invalid Entry Type: ${invalidEntryTypes.joinToString(", ")}",
                            Toast.LENGTH_LONG
                        ).show()
                    }


                    invalidNumbers.isNotEmpty() -> {


                        Toast.makeText(
                            context,
                            "Please correct invalid numbers",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    parsedEntries.isEmpty() -> {


                        Toast.makeText(
                            context,
                            "Enter valid Single, Jodi or Pana entries",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    else -> {

                        val closed = lockedGames(selectedGames)
                        if (closed.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                "Entry Closed: ${closed.joinToString(", ") { resultDisplayName(it) }} result declared",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            val nowMs = System.currentTimeMillis()

                            // Copy once before background work so Compose state is not
                            // repeatedly read while a large entry is being saved.
                            val customerToSave = customerName.trim()
                            val gamesToSave = selectedGames.toList()
                            val entriesToSave = parsedEntries.toList()
                            val perGameTotalToSave = perGameTotal
                            val grandTotalToSave = grandTotal

                            val billId =
                                kotlinx.coroutines.withContext(
                                    kotlinx.coroutines.Dispatchers.IO
                                ) {
                                    database.billDao().insertBillAndGetId(
                                        BillEntity(
                                            masterUid = currentMasterUid,
                                            customerName = customerToSave,
                                            games = serializeGames(gamesToSave),
                                            entries = serializeEntries(entriesToSave),
                                            perGameTotal = perGameTotalToSave,
                                            grandTotal = grandTotalToSave,
                                            savedTime = nowMs,
                                            status = "ACTIVE",
                                            createdBy = currentUserId
                                        )
                                    ).toInt()
                                }

                            val now = Date(nowMs)
                            savedPreviewData = PrintPreviewData(
                                billId = billId,
                                customerName = customerToSave,
                                selectedGames = gamesToSave,
                                parsedEntries = entriesToSave,
                                grandTotal = grandTotalToSave,
                                date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(now),
                                time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(now)
                            )
                            isEntrySaved = true

                            // Do not reload the entire history after every save.
                            // Appending this one new bill is much faster for large histories.
                            val newSavedEntry = SavedEntry(
                                id = billId,
                                customerName = customerToSave,
                                games = gamesToSave,
                                entries = entriesToSave,
                                perGameTotal = perGameTotalToSave,
                                grandTotal = grandTotalToSave,
                                savedTime = nowMs,
                                status = "ACTIVE",
                                createdBy = currentUserId
                            )

                            savedEntries.add(0, newSavedEntry)

                            val cloudSavedEntry = newSavedEntry

                            if (currentMasterUid.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Entry sync failed. Please login again and retry.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                CloudBillManager.saveBill(
                                    entry = cloudSavedEntry,
                                    masterUid = currentMasterUid,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "Entry Saved Successfully",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { message ->
                                        Toast.makeText(
                                            context,
                                            "Entry sync failed: $message",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            },

            enabled = closedSelectedGames.isEmpty() && !isEntrySaved && !isParsingEntry,

            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)

        ) {


            Text(
                text = if (isEntrySaved) "ENTRY SAVED" else "SAVE ENTRY",
                fontSize = 17.sp
            )
        }


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        // PRINT PREVIEW - NEVER SAVES. It only opens the already-saved bill.
        Button(
            onClick = {
                val preview = savedPreviewData
                if (!isEntrySaved || preview == null || preview.billId <= 0) {
                    Toast.makeText(
                        context,
                        "Pehle SAVE ENTRY karein. Save ke baad hi Print unlock hoga.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    onPrintPreview(preview)
                }
            },
            enabled = isEntrySaved && savedPreviewData != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = if (isEntrySaved) "PRINT RECEIPT" else "PRINT LOCKED - SAVE FIRST",
                fontSize = 17.sp
            )
        }


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        OutlinedButton(

            onClick = onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "BACK TO DASHBOARD"
            )
        }


        Spacer(
            modifier = Modifier.height(30.dp)
        )
    }
}


// =====================================================
// BLUETOOTH PRINTER SETUP (58mm SPP / ESC-POS)
// =====================================================

private const val PRINTER_PREFS = "lakshya_printer"
private const val PRINTER_MAC = "printer_mac"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
fun hasBluetoothConnectPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun sendTextToSavedPrinter(context: android.content.Context, text: String): Result<Unit> {
    return runCatching {
        if (!hasBluetoothConnectPermission(context)) error("Bluetooth permission not allowed")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth not supported")
        if (!adapter.isEnabled) error("Bluetooth is OFF")
        val mac = context.getSharedPreferences(PRINTER_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(PRINTER_MAC, "").orEmpty()
        if (mac.isBlank()) error("Printer not connected. Open Printer Setup first")
        val device = adapter.getRemoteDevice(mac)
        adapter.cancelDiscovery()
        device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
            socket.connect()
            socket.outputStream.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.write(byteArrayOf(10, 10, 10))
                out.flush()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PrinterSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val prefs = remember { context.getSharedPreferences(PRINTER_PREFS, android.content.Context.MODE_PRIVATE) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedMac by remember { mutableStateOf(prefs.getString(PRINTER_MAC, "").orEmpty()) }
    var status by remember { mutableStateOf(if (selectedMac.isBlank()) "NO PRINTER CONNECTED" else "SAVED PRINTER: $selectedMac") }
    var busy by remember { mutableStateOf(false) }

    fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission(context)) {
            androidx.core.app.ActivityCompat.requestPermissions(
                context as android.app.Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                2002
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)
    ) {
        Text("PRINTER SETUP", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("58mm Bluetooth thermal printer", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("STATUS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(status, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                requestBluetoothPermission()
                if (adapter == null) {
                    status = "Bluetooth is not supported on this phone"
                } else if (!hasBluetoothConnectPermission(context)) {
                    status = "Allow Bluetooth permission, then press SEARCH again"
                } else if (!adapter.isEnabled) {
                    status = "Turn Bluetooth ON, pair the printer in phone Bluetooth settings, then SEARCH"
                } else {
                    devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
                    status = if (devices.isEmpty()) "No paired printer found" else "${devices.size} paired Bluetooth device(s) found"
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("SEARCH PRINTER") }

        Spacer(Modifier.height(14.dp))
        if (devices.isNotEmpty()) {
            Text("SELECT PRINTER", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            devices.forEach { device ->
                val mac = device.address
                OutlinedButton(
                    onClick = { selectedMac = mac; status = "Selected: ${device.name ?: "Bluetooth Device"}" },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(device.name ?: "Bluetooth Device", fontWeight = FontWeight.Bold)
                        Text(mac, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            enabled = selectedMac.isNotBlank() && !busy,
            onClick = {
                busy = true
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val result = runCatching {
                        if (!hasBluetoothConnectPermission(context)) error("Bluetooth permission not allowed")
                        val device = adapter?.getRemoteDevice(selectedMac) ?: error("Bluetooth unavailable")
                        adapter.cancelDiscovery()
                        device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket -> socket.connect() }
                        prefs.edit().putString(PRINTER_MAC, selectedMac).apply()
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        busy = false
                        status = if (result.isSuccess) "CONNECTED & SAVED" else "CONNECT FAILED: ${result.exceptionOrNull()?.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text(if (busy) "CONNECTING..." else "CONNECT") }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val result = sendTextToSavedPrinter(
                        context,
                        "LAKSHYA\nPRINTER TEST\n------------------------------\nPrinter Connected Successfully\n\n"
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        busy = false
                        status = if (result.isSuccess) "TEST PRINT SUCCESS" else "TEST PRINT FAILED: ${result.exceptionOrNull()?.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("TEST PRINT") }

        Spacer(Modifier.height(18.dp))
        Text("Note: First pair your thermal printer from the phone's Bluetooth settings. Then use SEARCH PRINTER here.", fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK") }
    }
}

// =====================================================
// PRINT PREVIEW SCREEN
// =====================================================

@Composable
fun PrintPreviewScreen(

    data:
    PrintPreviewData,

    database: AppDatabase,

    currentUserId: String,

    currentMasterUid: String,

    onPrinted: (Int, String, Long) -> Unit,

    onBack: () -> Unit

) {

    val context =
        LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    var printed by remember(data.billId) { mutableStateOf(false) }
    var printCount by remember(data.billId) { mutableIntStateOf(0) }

    LaunchedEffect(data.billId, currentMasterUid) {
        if (data.billId > 0 && currentMasterUid.isNotBlank()) {
            val bill = database.billDao().getBillById(data.billId, currentMasterUid)
            printed = bill?.isPrinted == true
            printCount = bill?.printCount ?: 0
        }
    }

    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally

    ) {


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Text(
            text =
                "PRINT PREVIEW",
            fontSize = 28.sp
        )
        Text(text = "Printed $printCount time${if (printCount == 1) "" else "s"}", fontSize = 14.sp)


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {


            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)

            ) {


                Text(

                    text =
                        "JAI SHREE MAHAKAL",

                    fontSize = 20.sp,

                    modifier =
                        Modifier.align(
                            Alignment.CenterHorizontally
                        )

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                HorizontalDivider()


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Text(

                    text = "LAKSHYA",

                    fontSize = 26.sp,

                    modifier =
                        Modifier.align(
                            Alignment.CenterHorizontally
                        )

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                HorizontalDivider()


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Text(

                    text =
                        "Date: ${data.date}",

                    fontSize = 17.sp

                )


                Text(

                    text =
                        "Time: ${data.time}",

                    fontSize = 17.sp

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                HorizontalDivider()


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Text(

                    text =
                        "Customer: ${data.customerName}",

                    fontSize = 17.sp

                )


                Text(

                    text =
                        "Games: ${
                            data.selectedGames
                                .joinToString(
                                    ", "
                                )
                        }",

                    fontSize = 17.sp

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                HorizontalDivider()


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                listOf(
                    "Single",
                    "Jodi",
                    "Pana"
                ).forEach {
                        type ->


                    val typeEntries =

                        data.parsedEntries
                            .filter {

                                it.entryType ==
                                        type

                            }


                    if (
                        typeEntries.isNotEmpty()
                    ) {


                        Text(

                            text =
                                type.uppercase(),

                            fontSize = 19.sp

                        )


                        Spacer(
                            modifier = Modifier.height(5.dp)
                        )


                        val grouped =

                            typeEntries
                                .groupBy {

                                    it.actualAmount

                                }


                        grouped.forEach {
                                (amount, entries) ->


                            val numbers =

                                entries
                                    .joinToString(
                                        " "
                                    ) {

                                        it.number

                                    }


                            Text(

                                text = "$numbers = Rs.${displayAmount(amount)}",

                                fontSize = 17.sp

                            )
                        }


                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )


                        HorizontalDivider()


                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )
                    }
                }


                Text(

                    text =
                        "Entries: ${data.parsedEntries.size}",

                    fontSize = 17.sp

                )


                Text(

                    text =
                        "Games: ${data.selectedGames.size}",

                    fontSize = 17.sp

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Text(

                    text =
                        "GRAND TOTAL: Rs.${data.grandTotal}",

                    fontSize = 22.sp

                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                HorizontalDivider()


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Text(

                    text = "Thank You",

                    fontSize = 18.sp,

                    modifier =
                        Modifier.align(
                            Alignment.CenterHorizontally
                        )

                )


                Text(

                    text =
                        "HAVE AA NICE DAY",

                    fontSize = 18.sp,

                    modifier =
                        Modifier.align(
                            Alignment.CenterHorizontally
                        )

                )

            }

        }


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Button(

            onClick = {


                val receiptText =

                    buildReceiptText(

                        customerName =
                            data.customerName,

                        selectedGames =
                            data.selectedGames,

                        parsedEntries =
                            data.parsedEntries,

                        grandTotal =
                            data.grandTotal,

                        date =
                            data.date,

                        time =
                            data.time

                    )


                coroutineScope.launch {
                    if (data.billId <= 0) {
                        Toast.makeText(context, "Invalid Slip ID", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val printedTime = System.currentTimeMillis()
                    // Lock first: duplicate taps cannot reach the printer.
                    val reserved = database.billDao().markBillPrinted(
                        billId = data.billId, masterUid = currentMasterUid,
                        printedBy = currentUserId, printedTime = printedTime
                    )
                    if (reserved <= 0) {
                        printed = true
                        Toast.makeText(context, "Slip already PRINTED / LOCKED", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    printed = true
                    val printResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        sendTextToSavedPrinter(context, receiptText)
                    }
                    if (printResult.isFailure) {
                        database.billDao().releasePrintReservation(
                            data.billId, currentMasterUid, currentUserId, printedTime
                        )
                        printed = false
                        Toast.makeText(context, "PRINT FAILED: ${printResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    onPrinted(data.billId, currentUserId, printedTime)
                    val localBill = database.billDao().getBillById(data.billId, currentMasterUid)
                    printCount = localBill?.printCount ?: printCount
                    if (localBill != null && currentMasterUid.isNotBlank()) {
                        CloudBillManager.markBillPrinted(
                            masterUid = currentMasterUid, localBillId = localBill.id,
                            savedTime = localBill.savedTime, printedBy = currentUserId,
                            printedTime = printedTime, printCount = localBill.printCount, onSuccess = {},
                            onError = { message -> Toast.makeText(context, "Print sync failed: $message", Toast.LENGTH_LONG).show() }
                        )
                    }
                    Toast.makeText(context, "Slip #${data.billId} PRINTED & LOCKED", Toast.LENGTH_LONG).show()
                }

            },

            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = !printed

        ) {


            Text(
                text = if (printed) "PRINTED / LOCKED" else "PRINT",
                fontSize = 17.sp
            )

        }


        Spacer(
            modifier = Modifier.height(15.dp)
        )


        OutlinedButton(

            onClick = onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {


            Text(
                text =
                    "BACK TO ENTRY"
            )

        }


        Spacer(
            modifier = Modifier.height(30.dp)
        )

    }

}


// =====================================================
// GAME PARSER
// =====================================================

fun parseGames(
    gameInput: String
): List<String> {

    return gameInput
        .uppercase()
        .replace(
            ",",
            " "
        )
        .split(
            Regex("\\s+")
        )
        .map {
            it.trim()
        }
        .filter {
            it.isNotEmpty()
        }
        .filter {
            it in gameList
        }
        .distinct()
}


// =====================================================
// INVALID GAME FINDER
// =====================================================

fun findInvalidGames(
    gameInput: String
): List<String> {

    return gameInput
        .uppercase()
        .replace(
            ",",
            " "
        )
        .split(
            Regex("\\s+")
        )
        .map {
            it.trim()
        }
        .filter {
            it.isNotEmpty()
        }
        .filter {
            it !in gameList
        }
        .distinct()
}


// =====================================================
// SINGLE VALIDATION
// =====================================================

fun isValidSingle(
    number: String
): Boolean {

    return number.length == 1 &&
            number.all {
                it.isDigit()
            }
}


// =====================================================
// JODI VALIDATION
// =====================================================

fun isValidJodi(
    number: String
): Boolean {

    return number.length == 2 &&
            number.all {
                it.isDigit()
            }
}


// =====================================================
// PANA VALIDATION
// =====================================================

fun isValidPana(
    number: String
): Boolean {

    if (
        number.length != 3 ||
        !number.all {
            it.isDigit()
        }
    ) {

        return false
    }


    val a =
        number[0]
            .digitToInt()


    val b =
        number[1]
            .digitToInt()


    val c =
        number[2]
            .digitToInt()


    val normalOrder =

        a <= b &&
                b <= c


    val zeroWrapOrder =

        a <= b &&
                c == 0


    // Double-zero pana is valid: 100, 200, ..., 900.
    val doubleZeroPana =
        b == 0 &&
                c == 0

    return normalOrder ||
            zeroWrapOrder ||
            doubleZeroPana
}


// =====================================================
// MIXED ENTRY PARSER
// =====================================================

fun parseMixedEntries(
    rawEntry: String
): MixedParseResult {

    val finalEntries =
        mutableListOf<NumberAmountEntry>()

    val invalidNumbers =
        mutableListOf<String>()

    val invalidEntryTypes =
        mutableListOf<String>()

    var currentType = ""

    val lines =
        rawEntry
            .uppercase()
            .lines()

    lines.forEach { originalLine ->

        val line =
            originalLine.trim()

        if (line.isBlank()) {
            return@forEach
        }

        // Valid entry type headings
        if (
            line == "S" ||
            line == "SINGLE"
        ) {
            currentType = "Single"
            return@forEach
        }

        if (
            line == "J" ||
            line == "JODI"
        ) {
            currentType = "Jodi"
            return@forEach
        }

        if (
            line == "P" ||
            line == "PANA" ||
            line == "PANE"
        ) {
            currentType = "Pana"
            return@forEach
        }

        // Any non-empty line without "=" that is not a valid heading
        // is treated as an invalid entry type, e.g. SIGLE, JOD, PANAA.
        if (!line.contains("=")) {
            invalidEntryTypes.add(line)
            currentType = ""
            return@forEach
        }

        // Entry data cannot be accepted until a valid type heading is entered.
        if (currentType.isBlank()) {
            invalidEntryTypes.add("TYPE MISSING BEFORE: $line")
            return@forEach
        }

        val cleanedLine =
            line.replace(
                ",",
                " "
            )

        val groupRegex =
            Regex(
                """((?:\d+\s*)+?)\s*=\s*(\d+(?:\.5)?)"""
            )

        val matches =
            groupRegex
                .findAll(cleanedLine)
                .toList()

        if (matches.isEmpty()) {
            invalidNumbers.add(line)
            return@forEach
        }

        matches.forEach { match ->

            val numbersPart =
                match
                    .groupValues[1]
                    .trim()

            val enteredAmount =
                match
                    .groupValues[2]
                    .toDoubleOrNull()

            if (
                enteredAmount == null ||
                enteredAmount <= 0
            ) {
                invalidNumbers.add(line)
                return@forEach
            }

            val numbers =
                numbersPart
                    .split(
                        Regex("\\s+")
                    )
                    .filter {
                        it.isNotBlank()
                    }

            val validNumbers = numbers.filter { number ->

                val valid =
                    when (currentType) {

                        "Single" ->
                            isValidSingle(
                                number
                            )

                        "Jodi" ->
                            isValidJodi(
                                number
                            )

                        "Pana" ->
                            isValidPana(
                                number
                            )

                        else ->
                            false
                    }

                if (!valid) {

                    invalidNumbers.add(
                        number
                    )
                }
                valid
            }

            // Preserve the entered amount on every number. The bill total is
            // rounded once, after all entries are summed (1132.5 -> 1133).
            validNumbers.forEach { number ->
                finalEntries.add(
                    NumberAmountEntry(
                        number = number,
                        amount = kotlin.math.ceil(enteredAmount).toInt(),
                        entryType = currentType,
                        actualAmount = enteredAmount
                    )
                )
            }
        }
    }

    return MixedParseResult(

        entries =
            finalEntries,

        invalidNumbers =
            invalidNumbers
                .distinct(),

        invalidEntryTypes =
            invalidEntryTypes
                .distinct()
    )
}

// Quick Pana chart used by the New Entry screen.  Selecting a type and Ank
// writes the complete chart line into the normal PANA entry box.
private val quickPanaChart = mapOf(
    "SP" to mapOf(
        "1" to "128 137 146 236 245 290 380 470 489 560 678 579",
        "2" to "129 138 147 156 237 246 345 390 480 570 679 589",
        "3" to "120 139 148 157 238 247 256 346 490 580 670 689",
        "4" to "130 149 158 167 239 248 257 347 356 590 680 789",
        "5" to "140 159 168 230 249 258 267 348 357 456 690 780",
        "6" to "123 150 169 178 240 259 268 349 358 457 367 790",
        "7" to "124 160 179 250 269 278 340 359 368 458 467 890",
        "8" to "125 134 170 189 260 279 350 369 378 459 567 468",
        "9" to "126 135 180 234 270 289 360 379 450 469 478 568",
        "0" to "127 136 190 235 280 279 370 479 460 569 389 578"
    ),
    "DP" to mapOf(
        "1" to "100 119 155 227 335 344 399 588 669", "2" to "200 110 228 255 336 499 660 688 778",
        "3" to "300 166 229 337 355 445 599 779 788", "4" to "400 112 220 266 338 446 455 699 770",
        "5" to "500 113 122 177 339 366 447 799 889", "6" to "600 114 277 330 448 466 556 880 899",
        "7" to "700 115 133 188 223 377 449 557 566", "8" to "800 116 224 233 288 440 477 558 990",
        "9" to "900 117 144 199 225 388 559 577 667", "0" to "550 668 244 299 226 488 677 118"
    ),
    "TP" to mapOf("1" to "777", "2" to "444", "3" to "111", "4" to "888", "5" to "555", "6" to "222", "7" to "999", "8" to "666", "9" to "333", "0" to "000")
)


// =====================================================
// ROOM DATABASE SERIALIZATION HELPERS
// =====================================================

fun serializeGames(
    games: List<String>
): String {

    return games.joinToString(
        separator = "|"
    )
}


fun deserializeGames(
    value: String
): List<String> {

    if (value.isBlank()) {
        return emptyList()
    }

    return value
        .split("|")
        .filter {
            it.isNotBlank()
        }
}


fun serializeEntries(
    entries: List<NumberAmountEntry>
): String {

    return entries.joinToString(
        separator = "\n"
    ) { entry ->

        "${entry.entryType}\t${entry.number}\t${displayAmount(entry.actualAmount)}"
    }
}


fun deserializeEntries(
    value: String
): List<NumberAmountEntry> {

    if (value.isBlank()) {
        return emptyList()
    }

    return value
        .lines()
        .mapNotNull { line ->

            val parts =
                line.split("\t")

            if (parts.size != 3) {

                null

            } else {

                val actualAmount = parts[2].toDoubleOrNull()

                if (actualAmount == null) {

                    null

                } else {

                    NumberAmountEntry(
                        number = parts[1],
                        amount = kotlin.math.ceil(actualAmount).toInt(),
                        entryType = parts[0],
                        actualAmount = actualAmount
                    )
                }
            }
        }
}


// =====================================================
// BUILD RECEIPT
// =====================================================

fun buildReceiptText(

    customerName: String,

    selectedGames:
    List<String>,

    parsedEntries:
    List<NumberAmountEntry>,

    grandTotal: Int,

    date: String,

    time: String

): String {


    val receipt =
        StringBuilder()


    val day = try {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(date)!!
        )
    } catch (_: Exception) { "" }
    receipt.append("\u001B\u004D\u0001") // ESC/POS condensed font
    receipt.appendLine("JAI SHREE MAHAKAL  $date $day")


    receipt.appendLine(
        "------------------------------"
    )


    receipt.appendLine(
        "Customer: $customerName"
    )


    receipt.appendLine(

        "Games: ${
            selectedGames
                .joinToString(
                    ", "
                )
        }"

    )


    receipt.appendLine(
        "------------------------------"
    )


    listOf(
        "Single",
        "Jodi",
        "Pana"
    ).forEach {
            type ->


        val typeEntries =

            parsedEntries
                .filter {

                    it.entryType ==
                            type

                }


        if (
            typeEntries.isNotEmpty()
        ) {


            receipt.appendLine(
                type.uppercase()
            )


            val grouped =

                typeEntries
                    .groupBy {

                        it.actualAmount

                    }


            grouped.forEach {
                    (amount, entries) ->


                val numbers =

                    entries
                        .joinToString(
                            " "
                        ) {

                            it.number

                        }


                receipt.appendLine(

                    "$numbers = Rs.${displayAmount(amount)}"

                )
            }


            receipt.appendLine(
                "------------------------------"
            )
        }
    }


    receipt.appendLine(
        "Grand Total: Rs.$grandTotal"
    )


    receipt.appendLine(
        "------------------------------"
    )


    receipt.appendLine(
        "Thank You"
    )


    receipt.appendLine(
        "JAI SHREE MAHAKAL"
    )


    return receipt.toString()
}

// =====================================================
// GAME WISE LIMIT SCREEN
// Shows total amount on each number for the selected game.
// Only ACTIVE entries are counted.
// =====================================================

@Composable
fun GameWiseLimitScreen(
    savedEntries: List<SavedEntry>,
    currentMasterUid: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resultPrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_results_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }
    val games = listOf(
        "MO", "NO", "RDO", "KO",
        "MC", "NC", "RDC", "KC",
        "KNO", "RO", "MBO", "KNC",
        "RC", "MBC"
    )

    var selectedGame by remember {
        mutableStateOf("MO")
    }

    val activeEntries =
        savedEntries.filter {
            it.status == "ACTIVE" &&
                    it.games.contains(selectedGame)
        }

    val pairedOpenGame = openGameForCloseGame(selectedGame)
    val declaredOpenAnk = pairedOpenGame
        ?.let { openGame ->
            // Only the declared open Ank decides which Jodi creates close risk.
            getResultJodi(resultPrefs.getString(openGame, "").orEmpty())
                .firstOrNull()
        }
    val closeSideJodiEntries = if (pairedOpenGame == null) {
        emptyList()
    } else {
        savedEntries.filter { it.status == "ACTIVE" && pairedOpenGame in it.games }
            .flatMap { it.entries }
            .filter { entry ->
                entry.entryType == "Jodi" &&
                        declaredOpenAnk != null &&
                        entry.number.firstOrNull() == declaredOpenAnk
            }
    }

    val singleTotals =
        activeEntries
            .flatMap { it.entries }
            .filter { it.entryType == "Single" }
            .groupBy { it.number }
            .mapValues { (_, items) ->
                items.sumOf { it.amount }
            }
            .toList()
            .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }

    val jodiTotals =
        activeEntries
            .flatMap { it.entries }
            .filter { it.entryType == "Jodi" }
            .groupBy { it.number }
            .mapValues { (_, items) ->
                items.sumOf { it.amount }
            }
            .toList()
            .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }

    val panaTotals =
        activeEntries
            .flatMap { it.entries }
            .filter { it.entryType == "Pana" }
            .groupBy { it.number }
            .mapValues { (_, items) ->
                items.sumOf { it.amount }
            }
            .toList()
            .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }

    val totalAmount =
        singleTotals.sumOf { it.second } +
                jodiTotals.sumOf { it.second } +
                panaTotals.sumOf { it.second }

    // Jodi 12=100 adds ₹100 to open digit 1 and ₹800 (8x risk) to close digit 2.
    val combinedLimitTotals =
        (0..9).associate { digit ->

            val digitText = digit.toString()

            val singleAmount =
                singleTotals
                    .filter { it.first == digitText }
                    .sumOf { it.second }

            val jodiOpenAmount =
                if (pairedOpenGame == null) {
                    jodiTotals
                        .filter {
                            it.first.isNotEmpty() &&
                                    it.first.first().toString() == digitText
                        }
                        .sumOf { it.second }
                } else 0

            val jodiCloseRiskAmount =
                closeSideJodiEntries
                    .filter {
                        it.number.length >= 2 &&
                                it.number.last().toString() == digitText
                    }
                    .sumOf { it.amount * 8 }

            digitText to
                    (singleAmount + jodiOpenAmount + jodiCloseRiskAmount)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp)
    ) {
        Text(
            text = "GAME WISE LIMIT",
            fontSize = 26.sp
        )

        Text(
            text = "Selected Game: $selectedGame",
            fontSize = 16.sp
        )

        Text(
            text = "Total Amount: ₹$totalAmount",
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        games.chunked(4).forEach { rowGames ->

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                rowGames.forEach { game ->

                    Button(
                        onClick = {
                            selectedGame = game
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = game,
                            fontSize = 12.sp
                        )
                    }
                }

                repeat(4 - rowGames.size) {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        fun sectionTitle(title: String) {
            // Local helper intentionally unused in Compose UI body.
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            // LEFT SIDE - EXACT SINGLE / JODI / PANA TOTALS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {

                Text(
                    text = "SINGLE",
                    fontSize = 19.sp
                )

                if (singleTotals.isEmpty()) {
                    Text(
                        text = "No Single entries",
                        fontSize = 13.sp
                    )
                } else {
                    singleTotals.forEach { (number, amount) ->
                        Text(
                            text = "$number  =  ₹$amount",
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    text = "JODI",
                    fontSize = 19.sp
                )

                if (jodiTotals.isEmpty()) {
                    Text(
                        text = "No Jodi entries",
                        fontSize = 13.sp
                    )
                } else {
                    jodiTotals.forEach { (number, amount) ->
                        Text(
                            text = "$number  =  ₹$amount",
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    text = "PANA",
                    fontSize = 19.sp
                )

                if (panaTotals.isEmpty()) {
                    Text(
                        text = "No Pana entries",
                        fontSize = 13.sp
                    )
                } else {
                    panaTotals.forEach { (number, amount) ->
                        Text(
                            text = "$number  =  ₹$amount",
                            fontSize = 15.sp
                        )
                    }
                }
            }


            // RIGHT SIDE - SINGLE + JODI OPEN AMOUNT + CLOSE-SIDE RISK
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {

                Text(
                    text = "TOTAL LIMIT",
                    fontSize = 19.sp
                )

                Text(
                    text = if (pairedOpenGame == null) "Single + Jodi Open" else "Single + Jodi Close Risk (8x)",
                    fontSize = 11.sp
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                combinedLimitTotals.forEach { (digit, amount) ->

                    if (amount > 0) {
                        Text(
                            text = "$digit  =  ₹$amount",
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "BACK"
            )
        }

        Spacer(
            modifier = Modifier.height(25.dp)
        )
    }
}



// =====================================================
// RESULT + CHUKARA HELPERS
// =====================================================

data class WinningChukara(
    val game: String,
    val entryType: String,
    val number: String,
    val playedAmount: Int,
    val chukaraAmount: Int,
    val paymentKey: String
)

fun digitTotalLastDigit(pana: String): String {
    val total = pana.filter { it.isDigit() }.sumOf { it.digitToInt() }
    return (total % 10).toString()
}

fun getWinningNumbers(result: String): Triple<String, String, String>? {
    val parts = result.trim().split("-")
    if (parts.size != 3) return null
    val openPana = parts[0]
    val jodi = parts[1]
    val closePana = parts[2]
    if (openPana.length != 3 || jodi.length != 2 || closePana.length != 3) return null
    return Triple(openPana, jodi, closePana)
}

fun calculateEntryChukara(
    savedEntry: SavedEntry,
    resultPrefs: android.content.SharedPreferences
): List<WinningChukara> {
    if (savedEntry.status != "ACTIVE") return emptyList()

    // BACKWARD COMPATIBILITY + NEW PRINT SECURITY
    // Existing old slips were migrated with isPrinted = false.
    // On the first Chukara calculation after this security update,
    // remember the activation time. Entries created before that time
    // remain eligible like before; entries created after that time
    // must be PRINTED before they can receive Chukara.
    var securityStartTime = resultPrefs.getLong("PRINT_SECURITY_START_TIME", 0L)
    if (securityStartTime == 0L) {
        securityStartTime = System.currentTimeMillis()
        resultPrefs.edit()
            .putLong("PRINT_SECURITY_START_TIME", securityStartTime)
            .apply()
    }

    val isLegacyEntry = savedEntry.savedTime < securityStartTime
    if (!savedEntry.isPrinted && !isLegacyEntry) return emptyList()

    val wins = mutableListOf<WinningChukara>()

    savedEntry.games.forEach { game ->
        val pairedOpenGame =
            openGameForCloseGame(game)

        val storedResult =
            resultPrefs.getString(game, "").orEmpty()

        val result =
            if (pairedOpenGame != null) {
                val pairedOpenResult =
                    resultPrefs.getString(
                        pairedOpenGame,
                        ""
                    ).orEmpty().trim()

                val pairedParts =
                    pairedOpenResult.split("-")

                // Close game has a valid result ONLY while paired open game
                // currently contains the full result.
                if (pairedParts.size == 3) {
                    digitTotalLastDigit(
                        pairedParts[2]
                    )
                } else {
                    ""
                }
            } else {
                storedResult
            }

        if (result.isBlank()) return@forEach

        val resultTime =
            resultPrefs.getLong(
                "RESULT_TIME_$game",
                0L
            )

        if (
            resultTime > 0L &&
            savedEntry.savedTime >= resultTime
        ) return@forEach

        val cleanResult = result.trim()

        // Paired CLOSE games are stored as one Akda only.
        // Example: MC = "6" after MO becomes 123-66-330.
        val isCloseAkdaOnly =
            cleanResult.length == 1 &&
                    cleanResult.all { it.isDigit() }

        val parts =
            if (isCloseAkdaOnly) emptyList()
            else cleanResult.split("-")

        if (
            !isCloseAkdaOnly &&
            parts.size != 2 &&
            parts.size != 3
        ) return@forEach

        val openPana =
            if (isCloseAkdaOnly) "" else parts[0]

        val openSingle =
            if (isCloseAkdaOnly) cleanResult
            else parts[1].firstOrNull()?.toString().orEmpty()

        val isFullResult =
            !isCloseAkdaOnly && parts.size == 3

        val jodi =
            if (isFullResult) parts[1] else ""

        val closePana =
            if (isFullResult) parts[2] else ""

        val closeSingle =
            if (isFullResult) {
                digitTotalLastDigit(closePana)
            } else {
                ""
            }

        savedEntry.entries.forEachIndexed { index, entry ->
            val isWin = when (entry.entryType) {
                // OPEN GAME: 123-6 gives Single 6.
                // CLOSE GAME: paired result like MC="6" gives Single 6.
                "Single" ->
                    entry.number == openSingle

                // Customer ko winning Jodi ka Chukara milta hai. Close-side
                // liability alag se Game Wise Limit mein include hoti hai.
                "Jodi" ->
                    isFullResult &&
                            entry.number.padStart(2, '0') == jodi

                "Pana" ->
                    !isCloseAkdaOnly &&
                            (
                                    entry.number == openPana ||
                                            (isFullResult && entry.number == closePana)
                                    )

                else -> false
            }

            if (isWin) {
                val chukaraAmount = when (entry.entryType) {
                    // SINGLE AKDA ONLY:
                    // ₹5.5 = ₹50, ₹11 = ₹100, ₹16.5 = ₹150,
                    // ₹50 = ₹450, ₹55 = ₹500.
                    // Formula: every ₹5.5 played gives ₹50 Chukara.
                    "Single" ->
                        (kotlin.math.floor(entry.actualAmount / 5.5) * 50.0).toInt()

                    // JODI and PANA calculation remains exactly unchanged.
                    "Jodi" -> entry.amount * 80
                    "Pana" -> entry.amount * 100
                    else -> 0
                }
                val key = "${savedEntry.id}|$game|${entry.entryType}|${entry.number}|$index|$result"
                wins.add(
                    WinningChukara(
                        game = game,
                        entryType = entry.entryType,
                        number = entry.number,
                        playedAmount = entry.amount,
                        chukaraAmount = chukaraAmount,
                        paymentKey = key
                    )
                )
            }
        }
    }

    return wins
}

// =====================================================
// RESULT SCREEN
// =====================================================

data class ResultHistoryItem(
    val result: String,
    val savedTime: Long
)

fun resultDisplayName(game: String): String {
    return when (game) {
        "MO" -> "MANIPUR"
        "NO" -> "NAGPUR"
        "RDO" -> "RAJDHANI DAY"
        "KO" -> "KALYAN"
        "KNO" -> "KALYAN NIGHT"
        "RO" -> "RAJDHANI NIGHT"
        "MBO" -> "MAIN BAZAR"
        else -> game
    }
}

fun resultGames(): List<String> = listOf(
    "MO", "NO", "RDO", "KO", "KNO", "RO", "MBO"
)


fun closeGameForOpenGame(openGame: String): String? {
    return when (openGame) {
        "MO" -> "MC"
        "NO" -> "NC"
        "RDO" -> "RDC"
        "KO" -> "KC"
        "KNO" -> "KNC"
        "RO" -> "RC"
        "MBO" -> "MBC"
        else -> null
    }
}


fun openGameForCloseGame(closeGame: String): String? {
    return when (closeGame) {
        "MC" -> "MO"
        "NC" -> "NO"
        "RDC" -> "RDO"
        "KC" -> "KO"
        "KNC" -> "KNO"
        "RC" -> "RO"
        "MBC" -> "MBO"
        else -> null
    }
}

fun isValidLakshyaResult(value: String): Boolean {
    val openOnly = Regex("^\\d{3}-\\d$")
    val complete = Regex("^\\d{3}-\\d{2}-\\d{3}$")
    return openOnly.matches(value) || complete.matches(value)
}

fun getResultJodi(result: String): String {
    val parts = result.split("-")
    return when (parts.size) {
        // OPEN only: 110-2 -> show 2 in JODI history
        2 -> parts[1]
        // FULL: 110-24-130 -> show final JODI 24
        3 -> parts[1]
        else -> ""
    }
}

fun getResultPanel(result: String): String {
    val parts = result.split("-")
    return when (parts.size) {
        3 -> "${parts[0]} - ${parts[2]}"
        2 -> parts[0]
        else -> ""
    }
}

fun saveResultHistory(
    prefs: android.content.SharedPreferences,
    game: String,
    result: String,
    savedTime: Long = System.currentTimeMillis()
) {
    val key = "HISTORY_$game"
    val oldHistory =
        prefs.getStringSet(key, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    oldHistory.add("$savedTime|$result")
    prefs.edit()
        .putStringSet(key, oldHistory)
        .apply()
}

fun archiveAndClearCurrentResults(
    prefs: android.content.SharedPreferences
) {
    val now = System.currentTimeMillis()
    val editor = prefs.edit()

    resultGames().forEach { openGame ->
        val currentResult =
            prefs.getString(openGame, "")
                .orEmpty()
                .trim()

        if (currentResult.isNotBlank()) {
            saveOrUpdateResultHistoryForDate(
                prefs = prefs,
                game = openGame,
                result = currentResult,
                targetTime = now
            )
        }

        // Clear current OPEN result + its declaration time.
        editor.remove(openGame)
        editor.remove("RESULT_TIME_$openGame")

        // Clear paired CLOSE result + its declaration time too.
        closeGameForOpenGame(openGame)?.let { closeGame ->
            editor.remove(closeGame)
            editor.remove("RESULT_TIME_$closeGame")
        }
    }

    editor.apply()
}


fun saveOrUpdateResultHistoryForDate(
    prefs: android.content.SharedPreferences,
    game: String,
    result: String,
    targetTime: Long
) {
    val key = "HISTORY_$game"
    val oldHistory =
        prefs.getStringSet(
            key,
            emptySet()
        )?.toMutableSet()
            ?: mutableSetOf()

    val dateFormat =
        SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.getDefault()
        )

    val targetDate =
        dateFormat.format(
            Date(targetTime)
        )

    // One result per game per date.
    // Existing same-date record is removed before saving new value.
    val filtered =
        oldHistory.filterNot { raw ->
            val parts =
                raw.split(
                    "|",
                    limit = 2
                )

            if (parts.size != 2) {
                false
            } else {
                val time =
                    parts[0].toLongOrNull()

                time != null &&
                        dateFormat.format(
                            Date(time)
                        ) == targetDate
            }
        }.toMutableSet()

    filtered.add(
        "$targetTime|$result"
    )

    prefs.edit()
        .putStringSet(
            key,
            filtered
        )
        .apply()
}


fun getResultHistory(
    prefs: android.content.SharedPreferences,
    game: String
): List<ResultHistoryItem> {
    return prefs.getStringSet("HISTORY_$game", emptySet()).orEmpty()
        .mapNotNull { value ->
            val split = value.split("|", limit = 2)
            if (split.size != 2) null
            else {
                val time = split[0].toLongOrNull() ?: return@mapNotNull null
                ResultHistoryItem(split[1], time)
            }
        }
        .sortedByDescending { it.savedTime }
}

@Composable
fun ResultChartCell(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    Box(
        modifier = modifier
            .border(
                width = 0.7.dp,
                color = Color.Gray
            )
            .padding(
                horizontal = 2.dp,
                vertical = 7.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize
        )
    }
}


@Composable
fun PanelResultChartCell(
    result: String,
    modifier: Modifier = Modifier
) {
    val parts =
        result.trim().split("-")

    val openPana =
        parts.getOrNull(0).orEmpty()

    val middle =
        parts.getOrNull(1).orEmpty()

    val closePana =
        if (parts.size == 3) {
            parts[2]
        } else {
            ""
        }

    Box(
        modifier = modifier
            .height(30.dp)
            .border(
                width = 0.7.dp,
                color = Color.Gray
            )
            .padding(
                horizontal = 1.dp,
                vertical = 2.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // OPEN PANA - vertical digits
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                openPana.forEach { digit ->
                    Text(
                        text = digit.toString(),
                        fontSize = 7.sp,
                        lineHeight = 8.sp
                    )
                }
            }

            // OPEN AKDA for open-only result, final JODI for full result.
            Text(
                text = middle,
                fontSize = 13.sp
            )

            // CLOSE PANA - vertical digits.
            // Blank until full result is declared.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                closePana.forEach { digit ->
                    Text(
                        text = digit.toString(),
                        fontSize = 7.sp,
                        lineHeight = 8.sp
                    )
                }
            }
        }
    }
}


fun lakshyaDayStart(time: Long): Long {
    return java.util.Calendar.getInstance().apply {
        timeInMillis = time
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun lakshyaDayEnd(time: Long): Long {
    return java.util.Calendar.getInstance().apply {
        timeInMillis = lakshyaDayStart(time)
        add(java.util.Calendar.DAY_OF_MONTH, 1)
        add(java.util.Calendar.MILLISECOND, -1)
    }.timeInMillis
}

fun lakshyaDateLabel(time: Long): String =
    SimpleDateFormat(
        "dd/MM/yyyy",
        Locale.getDefault()
    ).format(Date(time))

fun parseLakshyaDate(value: String): Long? =
    try {
        SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.getDefault()
        ).apply {
            isLenient = false
        }.parse(value)?.time
    } catch (_: Exception) {
        null
    }

fun <T> filterLakshyaDay(
    items: List<T>,
    timeOf: (T) -> Long,
    selectedDay: Long
): List<T> {
    val start = lakshyaDayStart(selectedDay)
    val end = lakshyaDayEnd(selectedDay)
    return items.filter {
        timeOf(it) in start..end
    }
}


@Composable
fun LakshyaDateSelector(
    selectedDay: Long,
    onDaySelected: (Long) -> Unit,
    title: String = "SELECT DATE"
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var dateText by remember(selectedDay) {
        mutableStateOf(
            lakshyaDateLabel(selectedDay)
        )
    }

    OutlinedButton(
        onClick = {
            dateText =
                lakshyaDateLabel(selectedDay)
            showDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$title : ${lakshyaDateLabel(selectedDay)}",
            fontSize = 11.sp
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text(title)
            },
            text = {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { value ->
                        dateText =
                            value.filter {
                                it.isDigit() || it == '/'
                            }.take(10)
                    },
                    label = {
                        Text("DD/MM/YYYY")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed =
                            parseLakshyaDate(dateText)

                        if (parsed != null) {
                            onDaySelected(parsed)
                            showDialog = false
                        }
                    }
                ) {
                    Text("OPEN")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}


@Composable
fun ResultScreen(
    currentUserRole: String,
    currentMasterUid: String,
    permissions: Map<String, Boolean>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun resultAllowed(key: String): Boolean =
        currentUserRole == "ADMIN" ||
                (permissions[key] ?: true)

    val prefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_results_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }
    // The `results` Firestore collection is the permanent result history.
    // Only rows created in the active business-day cycle may be used as a
    // fallback live result.  Otherwise a row from a closed day repopulates
    // the blank live-result screen after CLOSE DAY.
    val dayArchivePrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_day_archive_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }
    val games = remember { resultGames() }
    val results = remember(currentMasterUid) {
        mutableStateMapOf<String, String>().apply {
            games.forEach { game ->
                this[game] = prefs.getString(game, "").orEmpty()
            }
        }
    }
    var selectedGame by remember { mutableStateOf(games.first()) }
    var resultInput by remember { mutableStateOf(results[selectedGame].orEmpty()) }
    var gameMenuExpanded by remember { mutableStateOf(false) }
    var historyGame by remember { mutableStateOf<String?>(null) }
    var historyType by remember { mutableStateOf("") }
    var historyRefresh by remember { mutableIntStateOf(0) }
    var showAddHistoryDialog by remember { mutableStateOf(false) }
    var manualHistoryGame by remember { mutableStateOf(games.first()) }
    var manualHistoryResult by remember { mutableStateOf("") }
    var manualHistoryDate by remember {
        mutableStateOf(
            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date())
        )
    }
    var manualHistoryMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            // PRIMARY real-time source: shared live-result document.
            val liveRegistration =
                CloudAccountSyncManager.listenLiveResults(
                    masterUid = currentMasterUid,
                    onUpdate = { liveResults, resultTimes ->
                        val editor = prefs.edit()

                        games.forEach { game ->
                            val value = liveResults[game].orEmpty()
                            val time = resultTimes[game] ?: 0L

                            if (value.isBlank()) {
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                            } else {
                                editor.putString(game, value)
                                editor.putLong("RESULT_TIME_$game", time)
                            }

                            results[game] = value
                        }

                        editor.apply()
                        resultInput = results[selectedGame].orEmpty()
                        historyRefresh++
                    },
                    onError = {
                        // The results collection listener below is also kept active
                        // as a fallback for Employee IDs.
                    }
                )

            // IMPORTANT EMPLOYEE FIX:
            // Listen directly to masters/{masterUid}/results too.
            // This makes ADMIN -> EMPLOYEE declaration work even when the
            // separate live-result document is delayed/unavailable.
            val directResultRegistration =
                com.google.firebase.firestore.FirebaseFirestore
                    .getInstance()
                    .collection("masters")
                    .document(currentMasterUid)
                    .collection("results")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) {
                            return@addSnapshotListener
                        }

                        val latestByGame =
                            snapshot.documents
                                .mapNotNull { document ->
                                    val game =
                                        document.getString("game")
                                            .orEmpty()
                                            .trim()
                                            .uppercase()

                                    val result =
                                        document.getString("result")
                                            .orEmpty()
                                            .trim()

                                    val savedTime =
                                        document.getLong("savedTime") ?: 0L

                                    val currentBusinessDayStart =
                                        dayArchivePrefs.getLong(
                                            "CURRENT_DAY_START",
                                            0L
                                        )

                                    if (
                                        game.isBlank() ||
                                        result.isBlank() ||
                                        savedTime <= 0L ||
                                        (
                                                currentBusinessDayStart > 0L &&
                                                        savedTime < currentBusinessDayStart
                                                )
                                    ) {
                                        null
                                    } else {
                                        Triple(game, result, savedTime)
                                    }
                                }
                                .groupBy { it.first }
                                .mapValues { (_, rows) ->
                                    rows.maxByOrNull { it.third }
                                }

                        val editor = prefs.edit()

                        games.forEach { game ->
                            val row = latestByGame[game]

                            if (row != null) {
                                editor.putString(game, row.second)
                                editor.putLong(
                                    "RESULT_TIME_$game",
                                    row.third
                                )
                                results[game] = row.second
                            } else {
                                // `results` is permanent history. Once CLOSE DAY
                                // starts a new business cycle, old rows are filtered
                                // above; clear their stale value from the live Result
                                // screen instead of leaving yesterday's result visible.
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                                results[game] = ""
                            }
                        }

                        editor.apply()
                        resultInput =
                            results[selectedGame].orEmpty()
                        historyRefresh++
                    }

            val historyRegistration =
                CloudAccountSyncManager.listenResultHistory(
                    masterUid = currentMasterUid,
                    onUpdate = { historyByGame ->
                        val editor = prefs.edit()

                        resultGames().forEach { game ->
                            val values =
                                historyByGame[game]
                                    .orEmpty()
                                    .map { "${it.savedTime}|${it.result}" }
                                    .toSet()

                            editor.putStringSet("HISTORY_$game", values)
                        }

                        editor.apply()
                        historyRefresh++
                    },
                    onError = { }
                )

            onDispose {
                liveRegistration?.remove()
                directResultRegistration.remove()
                historyRegistration?.remove()
            }
        }
    }


    fun refreshGame(game: String) {
        results[game] = prefs.getString(game, "").orEmpty()
    }

    // =====================================================
    // FULL SCREEN JODI / PANEL WEEKLY HISTORY PAGE
    // =====================================================
    if (historyGame != null) {
        val game = historyGame!!
        val history = remember(game, historyRefresh) {
            getResultHistory(prefs, game)
        }

        val liveResult =
            results[game].orEmpty().trim()

        val allItems =
            buildList {
                if (liveResult.isNotBlank()) {
                    add(
                        ResultHistoryItem(
                            result = liveResult,
                            savedTime = System.currentTimeMillis()
                        )
                    )
                }
                addAll(history)
            }
                .groupBy {
                    SimpleDateFormat(
                        "dd-MM-yyyy",
                        Locale.getDefault()
                    ).format(Date(it.savedTime))
                }
                .mapNotNull { (_, sameDay) ->
                    sameDay.maxByOrNull {
                        it.savedTime
                    }
                }

        // Night games run Monday-Friday.
        // Day/open games run Monday-Saturday.
        val isNightGame =
            game == "KNO" ||
                    game == "RO" ||
                    game == "MBO"

        val dayColumns =
            if (isNightGame) {
                listOf(
                    java.util.Calendar.MONDAY,
                    java.util.Calendar.TUESDAY,
                    java.util.Calendar.WEDNESDAY,
                    java.util.Calendar.THURSDAY,
                    java.util.Calendar.FRIDAY
                )
            } else {
                listOf(
                    java.util.Calendar.MONDAY,
                    java.util.Calendar.TUESDAY,
                    java.util.Calendar.WEDNESDAY,
                    java.util.Calendar.THURSDAY,
                    java.util.Calendar.FRIDAY,
                    java.util.Calendar.SATURDAY
                )
            }

        val dayNames =
            if (isNightGame) {
                listOf("MON", "TUE", "WED", "THU", "FRI")
            } else {
                listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
            }

        fun weekStart(time: Long): Long {
            val cal =
                java.util.Calendar.getInstance()

            cal.timeInMillis = time
            cal.set(
                java.util.Calendar.HOUR_OF_DAY,
                0
            )
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)

            while (
                cal.get(java.util.Calendar.DAY_OF_WEEK) !=
                java.util.Calendar.MONDAY
            ) {
                cal.add(
                    java.util.Calendar.DAY_OF_MONTH,
                    -1
                )
            }

            return cal.timeInMillis
        }

        val weeklyItems =
            allItems
                .groupBy {
                    weekStart(it.savedTime)
                }
                .toSortedMap(
                    compareByDescending { it }
                )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    if (historyType == "JODI") {
                        "${resultDisplayName(game)} JODI CHART"
                    } else {
                        "${resultDisplayName(game)} PANEL RECORD"
                    },
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // -------------------------------------------------
            // BOXED WEEKLY CHART
            // -------------------------------------------------
            if (historyType == "JODI") {

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dayNames.forEach { day ->
                        ResultChartCell(
                            text = day,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (weeklyItems.isEmpty()) {
                    ResultChartCell(
                        text = "No result history found",
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    weeklyItems.forEach { (_, items) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dayColumns.forEach { dayOfWeek ->
                                val item =
                                    items
                                        .filter {
                                            val cal =
                                                java.util.Calendar
                                                    .getInstance()
                                            cal.timeInMillis =
                                                it.savedTime
                                            cal.get(
                                                java.util.Calendar.DAY_OF_WEEK
                                            ) == dayOfWeek
                                        }
                                        .maxByOrNull {
                                            it.savedTime
                                        }

                                ResultChartCell(
                                    text =
                                        item?.let {
                                            getResultJodi(
                                                it.result
                                            )
                                        }.orEmpty(),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

            } else {

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResultChartCell(
                        text = "DATE",
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1.15f)
                    )

                    dayNames.forEach { day ->
                        ResultChartCell(
                            text = day,
                            fontSize = 8.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (weeklyItems.isEmpty()) {
                    ResultChartCell(
                        text = "No result history found",
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    weeklyItems.forEach { (weekStartTime, items) ->

                        val endCal =
                            java.util.Calendar
                                .getInstance()
                                .apply {
                                    timeInMillis =
                                        weekStartTime
                                    add(
                                        java.util.Calendar.DAY_OF_MONTH,
                                        if (isNightGame) 4 else 5
                                    )
                                }

                        val dateFormat =
                            SimpleDateFormat(
                                "dd/MM/yy",
                                Locale.getDefault()
                            )

                        val dateText =
                            dateFormat.format(
                                Date(weekStartTime)
                            ) +
                                    " TO " +
                                    dateFormat.format(
                                        Date(endCal.timeInMillis)
                                    )

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(30.dp)
                                    .border(
                                        width = 0.7.dp,
                                        color = Color.Gray
                                    )
                                    .padding(horizontal = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dateText,
                                    fontSize = 5.sp,
                                    maxLines = 1
                                )
                            }

                            dayColumns.forEach { dayOfWeek ->
                                val item =
                                    items
                                        .filter {
                                            val cal =
                                                java.util.Calendar
                                                    .getInstance()
                                            cal.timeInMillis =
                                                it.savedTime
                                            cal.get(
                                                java.util.Calendar.DAY_OF_WEEK
                                            ) == dayOfWeek
                                        }
                                        .maxByOrNull {
                                            it.savedTime
                                        }

                                // Screenshot-style PANEL cell:
                                //
                                // 350-84-130 becomes:
                                // 3       1
                                // 5  84   3
                                // 0       0
                                //
                                // Open-only 350-8 keeps close side blank.
                                PanelResultChartCell(
                                    result =
                                        item?.result
                                            ?.trim()
                                            .orEmpty(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentUserRole == "ADMIN") {
                Button(
                    onClick = {
                        manualHistoryGame = game
                        manualHistoryResult = ""
                        manualHistoryDate =
                            SimpleDateFormat(
                                "dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(Date())
                        showAddHistoryDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "ADD / EDIT HISTORY",
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    historyGame = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "BACK",
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Manual admin history dialog can still open over this full page.
        if (
            showAddHistoryDialog &&
            currentUserRole == "ADMIN"
        ) {
            AlertDialog(
                onDismissRequest = {
                    showAddHistoryDialog = false
                },
                title = {
                    Text("ADD / EDIT RESULT HISTORY")
                },
                text = {
                    Column {
                        Text(
                            resultDisplayName(
                                manualHistoryGame
                            )
                        )

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        OutlinedTextField(
                            value = manualHistoryDate,
                            onValueChange = { value ->
                                manualHistoryDate =
                                    value.filter {
                                        it.isDigit() ||
                                                it == '/'
                                    }.take(10)
                            },
                            label = {
                                Text("Date (DD/MM/YYYY)")
                            },
                            placeholder = {
                                Text("23/07/2026")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        OutlinedTextField(
                            value = manualHistoryResult,
                            onValueChange = { value ->
                                manualHistoryResult =
                                    value.filter {
                                        it.isDigit() ||
                                                it == '-'
                                    }.take(10)
                            },
                            label = {
                                Text("Result")
                            },
                            placeholder = {
                                Text(
                                    "123-6 or 123-66-330"
                                )
                            },
                            singleLine = true,
                            modifier =
                                Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            "Same date already exists = UPDATE, otherwise ADD",
                            fontSize = 10.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean =
                                manualHistoryResult
                                    .trim()

                            if (
                                !isValidLakshyaResult(
                                    clean
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Invalid result",
                                    Toast.LENGTH_LONG
                                ).show()

                                return@Button
                            }

                            val parts =
                                clean.split("-")

                            val valid =
                                if (parts.size == 2) {
                                    parts[1] ==
                                            digitTotalLastDigit(
                                                parts[0]
                                            )
                                } else {
                                    parts[1] ==
                                            "${
                                                digitTotalLastDigit(
                                                    parts[0]
                                                )
                                            }${
                                                digitTotalLastDigit(
                                                    parts[2]
                                                )
                                            }"
                                }

                            if (!valid) {
                                Toast.makeText(
                                    context,
                                    "Result Akda/Jodi calculation is not correct",
                                    Toast.LENGTH_LONG
                                ).show()

                                return@Button
                            }

                            val parsedDate =
                                try {
                                    SimpleDateFormat(
                                        "dd/MM/yyyy",
                                        Locale.getDefault()
                                    ).apply {
                                        isLenient = false
                                    }.parse(
                                        manualHistoryDate
                                    )
                                } catch (_: Exception) {
                                    null
                                }

                            if (parsedDate == null) {
                                Toast.makeText(
                                    context,
                                    "Date DD/MM/YYYY format me dalo",
                                    Toast.LENGTH_LONG
                                ).show()

                                return@Button
                            }

                            saveOrUpdateResultHistoryForDate(
                                prefs = prefs,
                                game = manualHistoryGame,
                                result = clean,
                                targetTime = parsedDate.time
                            )

                            historyRefresh++
                            showAddHistoryDialog = false
                            manualHistoryResult = ""

                            Toast.makeText(
                                context,
                                "History saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("SAVE")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showAddHistoryDialog = false
                        }
                    ) {
                        Text("CANCEL")
                    }
                }
            )
        }

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "LIVE RESULT", fontSize = 28.sp)
        Spacer(modifier = Modifier.height(18.dp))

        if (currentUserRole == "ADMIN") {
            Text(text = "UPDATE RESULT", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Box {
                Button(onClick = { gameMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = resultDisplayName(selectedGame))
                }
                DropdownMenu(
                    expanded = gameMenuExpanded,
                    onDismissRequest = { gameMenuExpanded = false }
                ) {
                    games.forEach { game ->
                        DropdownMenuItem(
                            text = { Text(resultDisplayName(game)) },
                            onClick = {
                                selectedGame = game
                                resultInput = results[game].orEmpty()
                                gameMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = resultInput,
                onValueChange = { value ->
                    resultInput = value.filter { it.isDigit() || it == '-' }.take(10)
                },
                label = { Text("Result") },
                placeholder = { Text("Open: 123-6   Full: 123-65-230") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val clean = resultInput.trim()
                    if (!isValidLakshyaResult(clean)) {
                        Toast.makeText(context, "Open: 123-6 OR Full: 123-65-230", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val parts = clean.split("-")
                    if (parts.size == 2) {
                        val expectedOpenAkda = digitTotalLastDigit(parts[0])
                        if (parts[1] != expectedOpenAkda) {
                            Toast.makeText(context, "Open Akda should be $expectedOpenAkda", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                    } else if (parts.size == 3) {
                        val expectedJodi = "${digitTotalLastDigit(parts[0])}${digitTotalLastDigit(parts[2])}"
                        if (parts[1] != expectedJodi) {
                            Toast.makeText(context, "Jodi should be $expectedJodi", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                    }

                    val oldResult = results[selectedGame].orEmpty()

                    // Do NOT create duplicate history rows while today's
                    // result changes from OPEN to FULL.
                    // Today's live result is shown as the current dated row.
                    // CLOSE DAY archives the final result permanently.
                    results[selectedGame] = clean

                    val now = System.currentTimeMillis()
                    val existingResultTime =
                        prefs.getLong("RESULT_TIME_$selectedGame", 0L)

                    val editor =
                        prefs.edit()
                            .putString(selectedGame, clean)

                    // Open result time is saved only once.
                    // Example first save: MO = 123-6
                    if (existingResultTime == 0L) {
                        editor.putLong(
                            "RESULT_TIME_$selectedGame",
                            now
                        )
                    }

                    // OPEN/CLOSE STATE SECURITY
                    //
                    // 123-6:
                    // Open result declared, but paired CLOSE game stays OPEN.
                    //
                    // 123-66-330:
                    // Full result declared, paired CLOSE game gets Akda and locks.
                    //
                    // IMPORTANT:
                    // If a full result was entered by mistake and Admin changes it
                    // back to 123-6, the paired CLOSE result/time are removed so
                    // new CLOSE entries can be taken again.
                    val closeGame =
                        closeGameForOpenGame(selectedGame)

                    if (closeGame != null) {
                        if (parts.size == 3) {
                            val closeAkda =
                                digitTotalLastDigit(parts[2])

                            editor.putString(
                                closeGame,
                                closeAkda
                            )

                            if (
                                prefs.getLong(
                                    "RESULT_TIME_$closeGame",
                                    0L
                                ) == 0L
                            ) {
                                editor.putLong(
                                    "RESULT_TIME_$closeGame",
                                    now
                                )
                            }
                        } else {
                            // Admin corrected full result back to OPEN-only.
                            // Completely reopen the paired CLOSE game.
                            editor.remove(closeGame)
                            editor.remove(
                                "RESULT_TIME_$closeGame"
                            )
                        }
                    }

                    editor.apply()

                    // Keep today's Result History in sync immediately.
                    // OPEN result (123-6) and later FULL result (123-66-330)
                    // update the SAME game/date row instead of creating duplicates.
                    val resultHistoryTime =
                        if (existingResultTime > 0L) {
                            existingResultTime
                        } else {
                            now
                        }

                    saveOrUpdateResultHistoryForDate(
                        prefs = prefs,
                        game = selectedGame,
                        result = clean,
                        targetTime = resultHistoryTime
                    )

                    // Result is also saved under this Master's cloud account.
                    CloudResultManager.saveResult(
                        masterUid = currentMasterUid,
                        game = selectedGame,
                        result = clean,
                        savedTime = resultHistoryTime,
                        onSuccess = {
                            // FULL result also declares the paired CLOSE game
                            // for every Employee ID under the same Master UID.
                            if (parts.size == 3 && closeGame != null) {
                                val closeAkda =
                                    digitTotalLastDigit(parts[2])

                                CloudResultManager.saveResult(
                                    masterUid = currentMasterUid,
                                    game = closeGame,
                                    result = closeAkda,
                                    savedTime = now,
                                    onError = { message ->
                                        Toast.makeText(
                                            context,
                                            "Close result cloud sync: $message",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        },
                        onError = { message ->
                            Toast.makeText(
                                context,
                                "Local result saved. Cloud: $message",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    historyRefresh++
                    Toast.makeText(
                        context,
                        "${resultDisplayName(selectedGame)} $clean Updated",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth().height(55.dp)
            ) { Text("SAVE / UPDATE RESULT") }
            if (results[selectedGame].orEmpty().isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val closeGame = closeGameForOpenGame(selectedGame)
                        prefs.edit()
                            .remove(selectedGame)
                            .remove("RESULT_TIME_$selectedGame")
                            .apply {
                                if (closeGame != null) {
                                    remove(closeGame)
                                    remove("RESULT_TIME_$closeGame")
                                }
                            }
                            .apply()
                        results[selectedGame] = ""
                        closeGame?.let { results[it] = "" }
                        resultInput = ""
                        CloudResultManager.deleteResult(
                            masterUid = currentMasterUid,
                            game = selectedGame,
                            onError = { message ->
                                Toast.makeText(context, "Local result deleted. Cloud: $message", Toast.LENGTH_LONG).show()
                            }
                        )
                        if (closeGame != null) {
                            CloudResultManager.deleteResult(currentMasterUid, closeGame)
                        }
                        historyRefresh++
                        Toast.makeText(context, "${resultDisplayName(selectedGame)} result deleted", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("DELETE RESULT") }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }

        games.forEach { game ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = resultDisplayName(game), fontSize = 21.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (results[game].isNullOrBlank()) "NOT DECLARED" else results[game].orEmpty(),
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { historyGame = game; historyType = "JODI" },
                            modifier = Modifier.weight(1f)
                        ) { Text("JODI") }
                        Button(
                            onClick = { historyGame = game; historyType = "PANEL" },
                            modifier = Modifier.weight(1f)
                        ) { Text("PANEL") }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { refreshGame(game) }) { Text("Refresh") }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK") }
        Spacer(modifier = Modifier.height(25.dp))
    }

    if (showAddHistoryDialog && currentUserRole == "ADMIN") {
        AlertDialog(
            onDismissRequest = {
                showAddHistoryDialog = false
            },
            title = {
                Text("ADD RESULT HISTORY")
            },
            text = {
                Column {
                    Text("Game")
                    Spacer(modifier = Modifier.height(6.dp))

                    Box {
                        Button(
                            onClick = {
                                manualHistoryMenuExpanded = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(resultDisplayName(manualHistoryGame))
                        }

                        DropdownMenu(
                            expanded = manualHistoryMenuExpanded,
                            onDismissRequest = {
                                manualHistoryMenuExpanded = false
                            }
                        ) {
                            games.forEach { game ->
                                DropdownMenuItem(
                                    text = {
                                        Text(resultDisplayName(game))
                                    },
                                    onClick = {
                                        manualHistoryGame = game
                                        manualHistoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualHistoryResult,
                        onValueChange = { value ->
                            manualHistoryResult =
                                value.filter {
                                    it.isDigit() || it == '-'
                                }.take(10)
                        },
                        label = {
                            Text("Old Result")
                        },
                        placeholder = {
                            Text("123-6 or 123-66-330")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean =
                            manualHistoryResult.trim()

                        if (!isValidLakshyaResult(clean)) {
                            Toast.makeText(
                                context,
                                "Open: 123-6 OR Full: 123-65-230",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }

                        val parts = clean.split("-")

                        val valid =
                            if (parts.size == 2) {
                                parts[1] ==
                                        digitTotalLastDigit(parts[0])
                            } else {
                                parts[1] ==
                                        "${digitTotalLastDigit(parts[0])}${digitTotalLastDigit(parts[2])}"
                            }

                        if (!valid) {
                            Toast.makeText(
                                context,
                                "Result Akda/Jodi calculation is not correct",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }

                        // HISTORY ONLY:
                        // This never declares/locks the live game.
                        saveOrUpdateResultHistoryForDate(
                            prefs = prefs,
                            game = manualHistoryGame,
                            result = clean,
                            targetTime = System.currentTimeMillis()
                        )

                        historyRefresh++
                        showAddHistoryDialog = false
                        manualHistoryResult = ""

                        Toast.makeText(
                            context,
                            "Result added to history only",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("ADD")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddHistoryDialog = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

}


// =====================================================
// PROFIT / LOSS
// =====================================================

@Composable
fun ProfitLossScreen(
    savedEntries: List<SavedEntry>,
    currentMasterUid: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resultPrefs = remember(currentMasterUid) {
        context.getSharedPreferences(
            "lakshya_results_$currentMasterUid",
            android.content.Context.MODE_PRIVATE
        )
    }

    var resultRefresh by remember(currentMasterUid) {
        mutableIntStateOf(0)
    }

    DisposableEffect(currentMasterUid) {
        if (currentMasterUid.isBlank()) {
            onDispose { }
        } else {
            val registration =
                CloudAccountSyncManager.listenLiveResults(
                    masterUid = currentMasterUid,
                    onUpdate = { liveResults, resultTimes ->
                        val editor = resultPrefs.edit()

                        resultGames().forEach { game ->
                            val value = liveResults[game].orEmpty()
                            val time = resultTimes[game] ?: 0L

                            if (value.isBlank()) {
                                editor.remove(game)
                                editor.remove("RESULT_TIME_$game")
                            } else {
                                editor.putString(game, value)
                                editor.putLong("RESULT_TIME_$game", time)
                            }
                        }

                        editor.apply()
                        resultRefresh++
                    },
                    onError = { }
                )

            onDispose {
                registration?.remove()
            }
        }
    }

    // Recalculate P&L immediately whenever cloud result changes.
    resultRefresh

    val activeEntries = savedEntries.filter { it.status == "ACTIVE" }
    val totalCollection = activeEntries.sumOf { it.grandTotal }
    val allWins = activeEntries.flatMap { calculateEntryChukara(it, resultPrefs) }
    val totalChukara = allWins.sumOf { it.chukaraAmount }
    val netAmount = totalCollection - totalChukara

    val result = when {
        netAmount > 0 -> "PROFIT"
        netAmount < 0 -> "LOSS"
        else -> "NO PROFIT / NO LOSS"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(25.dp))
        Text(text = "PROFIT / LOSS", fontSize = 28.sp)
        Spacer(modifier = Modifier.height(25.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "TOTAL COLLECTION", fontSize = 16.sp)
                Text(text = "₹$totalCollection", fontSize = 30.sp)
            }
        }

        Spacer(modifier = Modifier.height(15.dp))
        1
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "TOTAL CHUKARA", fontSize = 16.sp)
                Text(text = "₹$totalChukara", fontSize = 30.sp)
                Spacer(modifier = Modifier.height(15.dp))
                Spacer(modifier = Modifier.height(15.dp))
                Text(text = result, fontSize = 18.sp)
                Text(text = "₹${kotlin.math.abs(netAmount)}", fontSize = 30.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "BACK TO DASHBOARD")
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

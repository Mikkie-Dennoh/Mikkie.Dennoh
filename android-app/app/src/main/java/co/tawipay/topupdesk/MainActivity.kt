package co.tawipay.topupdesk

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private lateinit var callPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private val syncClient = DeviceSyncClient()
    private val syncHandler = Handler(Looper.getMainLooper())
    private var pendingRun: Pair<String, String>? = null
    private var pendingRunId = ""
    private var setupComplete by mutableStateOf(false)
    private var editingSetup by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var runStatus by mutableStateOf("")
    private var dashboardStatus by mutableStateOf("")
    private var sellingRate by mutableStateOf("91.9")
    private var orderSyncInProgress = false

    private val syncTick = object : Runnable {
        override fun run() {
            syncDashboard()
            syncHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(applicationContext)
        setupComplete = preferences.isSetupComplete
        sellingRate = preferences.sellingRate
        callPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingRun
            pendingRun = null
            if (granted && request != null) executeRun(request.first, request.second)
            else runStatus = "Phone permission is required to start the USSD request."
        }

        setContent {
            TopUpDeskTheme {
                if (!setupComplete || editingSetup) {
                    key(editingSetup) {
                        SetupWizard(
                            isAccessibilityEnabled = accessibilityEnabled,
                            initialStep = if (editingSetup) 1 else 0,
                            initialOwnerName = preferences.ownerName,
                            initialBusinessName = preferences.businessName,
                            initialOwnerPhone = preferences.ownerPhone,
                            initialExecutionCode = preferences.executionCode,
                            initialMode = preferences.executionMode,
                            onOpenAccessibilitySettings = { openAccessibilitySettings() },
                            onComplete = { name, business, phone, code, mode, pin ->
                                preferences.saveSetup(name, business, phone, code, mode, pin)
                                setupComplete = true
                                editingSetup = false
                                runStatus = "Setup saved securely on this device."
                            },
                        )
                    }
                } else {
                    DashboardScreen(
                        ownerName = preferences.ownerName,
                        businessName = preferences.businessName,
                        isAccessibilityEnabled = accessibilityEnabled,
                        runStatus = runStatus,
                        executionCode = preferences.executionCode,
                        executionPin = preferences.executionPin(),
                        executionMode = preferences.executionMode,
                        initialSellingRate = sellingRate,
                        isDashboardPaired = preferences.isDashboardPaired,
                        dashboardStatus = dashboardStatus,
                        initialDashboardUrl = preferences.dashboardBaseUrl,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onEditExecutionSettings = { editingSetup = true },
                        onRunUssd = { phone, amount -> requestUssdPermission(phone, amount) },
                        onStopUssd = {
                            preferences.clearPendingRun()
                            runStatus = "Automatic replies stopped. Close the USSD dialog if it remains open."
                        },
                        onSaveSellingRate = { rate -> saveSellingRate(rate) },
                        onPairDashboard = { baseUrl, code -> pairDashboard(baseUrl, code) },
                        onDisconnectDashboard = { disconnectDashboard() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::preferences.isInitialized) {
            accessibilityEnabled = isUssdAccessibilityEnabled()
            syncHandler.removeCallbacks(syncTick)
            syncHandler.post(syncTick)
        }
    }

    override fun onPause() {
        syncHandler.removeCallbacks(syncTick)
        super.onPause()
    }

    override fun onDestroy() {
        syncHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun disconnectDashboard() {
        if (!preferences.isDashboardPaired) return
        dashboardStatus = "Revoking dashboard access…"
        lifecycleScope.launch {
            val revoked = runCatching {
                withContext(Dispatchers.IO) {
                    syncClient.disconnect(preferences.dashboardBaseUrl, preferences.dashboardToken)
                }
            }.isSuccess
            preferences.clearDashboardConnection()
            dashboardStatus = if (revoked) {
                "Dashboard access revoked for this phone."
            } else {
                "Local pairing cleared. Revoke this phone from the website when it reconnects."
            }
        }
    }

    private fun pairDashboard(baseUrl: String, code: String) {
        dashboardStatus = "Pairing with your TawiPay dashboard…"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    syncClient.pair(baseUrl, code, preferences.businessName.ifBlank { "Android phone" })
                }
            }.onSuccess { result ->
                preferences.saveDashboardConnection(baseUrl, result.deviceToken)
                dashboardStatus = "Phone paired. Syncing the daily rate and status."
                syncDashboard()
            }.onFailure { error ->
                dashboardStatus = error.message ?: "Pairing failed. Check the address and one-time code."
            }
        }
    }

    private fun syncDashboard() {
        if (!::preferences.isInitialized || !preferences.isDashboardPaired) return
        lifecycleScope.launch {
            val queuedRate = preferences.pendingDashboardRate
            runCatching {
                withContext(Dispatchers.IO) {
                    if (queuedRate.isNotBlank()) {
                        syncClient.updateRate(preferences.dashboardBaseUrl, preferences.dashboardToken, queuedRate)
                    }
                    syncClient.sync(preferences.dashboardBaseUrl, preferences.dashboardToken)
                }
            }.onSuccess { result ->
                if (queuedRate.isNotBlank()) preferences.clearPendingDashboardRate(queuedRate)
                val latestRate = preferences.pendingDashboardRate.ifBlank { result.rate }
                preferences.saveSellingRate(latestRate)
                sellingRate = latestRate
                dashboardStatus = if (queuedRate.isNotBlank()) "Rate saved and synced to the dashboard." else result.status
                flushPendingOrderEvents()
            }.onFailure { error ->
                dashboardStatus = error.message ?: "Dashboard sync is waiting for a connection."
            }
        }
    }

    private fun saveSellingRate(rate: String) {
        preferences.saveSellingRate(rate)
        sellingRate = rate
        if (!preferences.isDashboardPaired) return
        preferences.savePendingDashboardRate(rate)
        syncDashboard()
    }

    private fun requestUssdPermission(phone: String, amount: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            executeRun(phone, amount)
        } else {
            pendingRun = phone to amount
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun executeRun(phone: String, amount: String) {
        val sequence = buildUssdSequence(preferences.executionCode, phone, amount, preferences.executionPin())
        if (sequence == null) {
            runStatus = "The saved USSD template could not be filled. Check the code in Execution USSD & PIN settings."
            return
        }
        pendingRunId = UUID.randomUUID().toString()

        if (preferences.executionMode == ExecutionMode.SIMPLE) {
            runStatus = "Sending the complete USSD request…"
            runCatching {
                launchSimpleUssd(
                    context = this,
                    code = sequence.fullCode,
                    onResult = {
                        runStatus = "USSD response received on this phone."
                        reportOrder(phone, amount, "completed", "USSD response received on phone")
                    },
                    onError = { message ->
                        runStatus = message
                        reportOrder(phone, amount, "failed", "USSD request failed on phone")
                    },
                )
            }.onFailure {
                runStatus = "Could not send USSD: ${it.message ?: "unknown error"}"
                reportOrder(phone, amount, "failed", "USSD request could not start")
            }
            return
        }

        if (!isUssdAccessibilityEnabled()) {
            accessibilityEnabled = false
            runStatus = "Turn on the TopUpDesk accessibility service before using Advanced mode."
            openAccessibilitySettings()
            return
        }

        runCatching {
            launchAdvancedUssd(this, sequence)
            runStatus = "USSD started. Keep the phone awake while the configured menu replies run."
            reportOrder(phone, amount, "started", "USSD session started on phone")
        }.onFailure {
            preferences.clearPendingRun()
            runStatus = "Could not start the USSD session: ${it.message ?: "unknown error"}"
            reportOrder(phone, amount, "failed", "USSD session could not start")
        }
    }

    private fun reportOrder(phone: String, amount: String, status: String, message: String) {
        if (!preferences.isDashboardPaired) return
        preferences.queueOrderEvent(pendingRunId, phone, amount, status, message)
        flushPendingOrderEvents()
    }

    private fun flushPendingOrderEvents() {
        if (!preferences.isDashboardPaired || orderSyncInProgress) return
        val pendingEvents = preferences.pendingOrderEvents()
        if (pendingEvents.isEmpty()) return
        orderSyncInProgress = true
        lifecycleScope.launch {
            try {
                for (event in pendingEvents) {
                    val eventId = event.optString("eventId")
                    val sent = runCatching {
                        withContext(Dispatchers.IO) {
                            syncClient.reportOrder(
                                preferences.dashboardBaseUrl,
                                preferences.dashboardToken,
                                eventId,
                                event.optString("recipientPhone"),
                                event.optString("airtimeAmount"),
                                event.optString("status"),
                                event.optString("message"),
                            )
                        }
                    }.isSuccess
                    if (!sent) {
                        dashboardStatus = "Phone records are queued securely and will sync when the connection returns."
                        break
                    }
                    preferences.removePendingOrderEvent(eventId)
                    dashboardStatus = "Phone records synced to the dashboard."
                }
            } finally {
                orderSyncInProgress = false
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isUssdAccessibilityEnabled(): Boolean {
        val enabled = Secure.getInt(contentResolver, Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val services = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val expected = ComponentName(this, UssdAccessibilityService::class.java).flattenToString()
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

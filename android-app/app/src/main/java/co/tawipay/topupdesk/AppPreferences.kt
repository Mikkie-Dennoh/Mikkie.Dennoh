package co.tawipay.topupdesk

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

class AppPreferences(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "topupdesk_secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val isSetupComplete: Boolean get() = preferences.getBoolean(KEY_SETUP_COMPLETE, false)
    val ownerName: String get() = preferences.getString(KEY_OWNER_NAME, "") ?: ""
    val businessName: String get() = preferences.getString(KEY_BUSINESS_NAME, "") ?: ""
    val ownerPhone: String get() = preferences.getString(KEY_OWNER_PHONE, "") ?: ""
    val executionCode: String get() = preferences.getString(KEY_EXECUTION_CODE, DEFAULT_EXECUTION_CODE) ?: DEFAULT_EXECUTION_CODE
    val executionMode: ExecutionMode
        get() = runCatching {
            ExecutionMode.valueOf(preferences.getString(KEY_EXECUTION_MODE, ExecutionMode.ADVANCED.name) ?: ExecutionMode.ADVANCED.name)
        }.getOrDefault(ExecutionMode.ADVANCED)

    fun saveSetup(
        ownerName: String,
        businessName: String,
        ownerPhone: String,
        executionCode: String,
        executionMode: ExecutionMode,
        executionPin: String,
    ) {
        preferences.edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .putString(KEY_OWNER_NAME, ownerName.trim())
            .putString(KEY_BUSINESS_NAME, businessName.trim())
            .putString(KEY_OWNER_PHONE, ownerPhone.filter(Char::isDigit))
            .putString(KEY_EXECUTION_CODE, executionCode.trim())
            .putString(KEY_EXECUTION_MODE, executionMode.name)
            .putString(KEY_EXECUTION_PIN, executionPin)
            .apply()
    }

    fun saveExecution(code: String, mode: ExecutionMode, executionPin: String) {
        preferences.edit()
            .putString(KEY_EXECUTION_CODE, code.trim())
            .putString(KEY_EXECUTION_MODE, mode.name)
            .putString(KEY_EXECUTION_PIN, executionPin)
            .apply()
    }

    fun executionPin(): String = preferences.getString(KEY_EXECUTION_PIN, "") ?: ""

    val sellingRate: String get() = preferences.getString(KEY_SELLING_RATE, "91.9") ?: "91.9"
    val dashboardBaseUrl: String get() = preferences.getString(KEY_DASHBOARD_BASE_URL, "") ?: ""
    val dashboardToken: String get() = preferences.getString(KEY_DASHBOARD_TOKEN, "") ?: ""
    val isDashboardPaired: Boolean get() = dashboardToken.isNotBlank()
    val pendingDashboardRate: String get() = preferences.getString(KEY_PENDING_DASHBOARD_RATE, "") ?: ""

    fun savePendingDashboardRate(rate: String) {
        preferences.edit().putString(KEY_PENDING_DASHBOARD_RATE, rate).apply()
    }

    fun clearPendingDashboardRate(expectedRate: String) {
        if (pendingDashboardRate == expectedRate) {
            preferences.edit().remove(KEY_PENDING_DASHBOARD_RATE).apply()
        }
    }

    fun saveDashboardConnection(baseUrl: String, token: String) {
        preferences.edit()
            .putString(KEY_DASHBOARD_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_DASHBOARD_TOKEN, token)
            .apply()
    }

    fun clearDashboardConnection() {
        preferences.edit().remove(KEY_DASHBOARD_BASE_URL).remove(KEY_DASHBOARD_TOKEN).apply()
    }

    fun saveSellingRate(rate: String) {
        preferences.edit().putString(KEY_SELLING_RATE, rate).apply()
    }

    fun stageReplies(replies: List<String>) {
        val json = JSONArray().apply { replies.forEach { put(it) } }
        preferences.edit()
            .putString(KEY_PENDING_REPLIES, json.toString())
            .putInt(KEY_PENDING_INDEX, 0)
            .putLong(KEY_RUN_STARTED_AT, System.currentTimeMillis())
            .putBoolean(KEY_RUN_ACTIVE, true)
            .apply()
    }

    fun takeNextReply(): String? {
        if (!preferences.getBoolean(KEY_RUN_ACTIVE, false)) return null
        if (System.currentTimeMillis() - preferences.getLong(KEY_RUN_STARTED_AT, 0L) > USSD_RUN_TIMEOUT_MS) {
            clearPendingRun()
            return null
        }
        val values = try {
            JSONArray(preferences.getString(KEY_PENDING_REPLIES, "[]") ?: "[]")
        } catch (_: Exception) {
            clearPendingRun()
            return null
        }
        val index = preferences.getInt(KEY_PENDING_INDEX, 0)
        if (index !in 0 until values.length()) {
            clearPendingRun()
            return null
        }
        val reply = values.optString(index)
        preferences.edit().putInt(KEY_PENDING_INDEX, index + 1).apply()
        return reply
    }

    fun clearPendingRun() {
        preferences.edit()
            .remove(KEY_PENDING_REPLIES)
            .remove(KEY_PENDING_INDEX)
            .remove(KEY_RUN_STARTED_AT)
            .putBoolean(KEY_RUN_ACTIVE, false)
            .apply()
    }

    fun isRunActive(): Boolean = preferences.getBoolean(KEY_RUN_ACTIVE, false)

    fun queueOrderEvent(eventId: String, recipientPhone: String, amount: String, status: String, message: String) {
        val events = runCatching { JSONArray(preferences.getString(KEY_PENDING_ORDER_EVENTS, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        val event = org.json.JSONObject()
            .put("eventId", eventId)
            .put("recipientPhone", recipientPhone)
            .put("airtimeAmount", amount)
            .put("status", status)
            .put("message", message)
        val updated = JSONArray()
        var replaced = false
        for (index in 0 until events.length()) {
            val current = events.optJSONObject(index) ?: continue
            if (current.optString("eventId") == eventId) {
                updated.put(event)
                replaced = true
            } else {
                updated.put(current)
            }
        }
        if (!replaced) updated.put(event)
        while (updated.length() > 50) {
            val trimmed = JSONArray()
            for (index in 1 until updated.length()) trimmed.put(updated.opt(index))
            preferences.edit().putString(KEY_PENDING_ORDER_EVENTS, trimmed.toString()).apply()
            return
        }
        preferences.edit().putString(KEY_PENDING_ORDER_EVENTS, updated.toString()).apply()
    }

    fun pendingOrderEvents(): List<org.json.JSONObject> {
        val events = runCatching { JSONArray(preferences.getString(KEY_PENDING_ORDER_EVENTS, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        return (0 until events.length()).mapNotNull { events.optJSONObject(it) }
    }

    fun removePendingOrderEvent(eventId: String) {
        val remaining = pendingOrderEvents().filterNot { it.optString("eventId") == eventId }
        val events = JSONArray().apply { remaining.forEach { put(it) } }
        preferences.edit().putString(KEY_PENDING_ORDER_EVENTS, events.toString()).apply()
    }

    companion object {
        const val DEFAULT_EXECUTION_CODE = "*180*6*5*Pn*Am*1*Pin#"

        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_BUSINESS_NAME = "business_name"
        private const val KEY_OWNER_PHONE = "owner_phone"
        private const val KEY_EXECUTION_CODE = "execution_code"
        private const val KEY_EXECUTION_MODE = "execution_mode"
        private const val KEY_EXECUTION_PIN = "execution_pin"
        private const val KEY_PENDING_REPLIES = "pending_replies"
        private const val KEY_PENDING_INDEX = "pending_index"
        private const val KEY_RUN_ACTIVE = "run_active"
        private const val KEY_RUN_STARTED_AT = "run_started_at"
        private const val KEY_SELLING_RATE = "selling_rate"
        private const val KEY_PENDING_ORDER_EVENTS = "pending_order_events"
        private const val KEY_DASHBOARD_BASE_URL = "dashboard_base_url"
        private const val KEY_DASHBOARD_TOKEN = "dashboard_token"
        private const val KEY_PENDING_DASHBOARD_RATE = "pending_dashboard_rate"
        private const val USSD_RUN_TIMEOUT_MS = 60_000L
    }
}

enum class ExecutionMode {
    SIMPLE,
    ADVANCED,
}

data class UssdSequence(val fullCode: String, val firstCode: String, val replies: List<String>)

fun buildUssdSequence(template: String, recipient: String, amount: String, pin: String): UssdSequence? {
    val resolved = template
        .replace("Pn", recipient.filter(Char::isDigit), ignoreCase = true)
        .replace("Am", amount.filter(Char::isDigit), ignoreCase = true)
        .replace("Pin", pin, ignoreCase = true)
        .trim()

    if (!resolved.matches(Regex("^\\*[0-9*]+#$"))) return null
    val segments = resolved.removePrefix("*").removeSuffix("#").split('*')
    if (segments.size < 2 || segments.any(String::isBlank)) return null

    return UssdSequence(
        fullCode = resolved,
        firstCode = "*${segments.first()}#",
        replies = segments.drop(1),
    )
}

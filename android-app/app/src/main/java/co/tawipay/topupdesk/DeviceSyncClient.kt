package co.tawipay.topupdesk

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class DeviceSyncClient {
    data class PairResult(val deviceId: String, val deviceToken: String)
    data class SyncResult(val rate: String, val status: String)

    fun pair(baseUrl: String, code: String, deviceName: String): PairResult {
        val response = request(
            baseUrl = baseUrl,
            path = "/api/device/register",
            method = "POST",
            body = JSONObject().put("code", code).put("name", deviceName),
        )
        return PairResult(response.getString("deviceId"), response.getString("token"))
    }

    fun sync(baseUrl: String, token: String): SyncResult {
        val response = request(baseUrl, "/api/device/sync", "GET", token = token)
        val rate = response.optDouble("rate", 91.9).toString()
        return SyncResult(rate, "Connected · rate synced")
    }

    fun disconnect(baseUrl: String, token: String) {
        request(baseUrl, "/api/device/sync", "DELETE", token = token)
    }

    fun updateRate(baseUrl: String, token: String, rate: String) {
        request(
            baseUrl = baseUrl,
            path = "/api/device/sync",
            method = "POST",
            token = token,
            body = JSONObject().put("status", "Daily rate updated from phone").put("rate", rate.toDouble()),
        )
    }

    fun reportOrder(
        baseUrl: String,
        token: String,
        eventId: String,
        recipientPhone: String,
        amount: String,
        status: String,
        message: String,
    ) {
        val order = JSONObject()
            .put("eventId", eventId)
            .put("recipientPhone", recipientPhone)
            .put("airtimeAmount", amount.toDouble())
            .put("status", status)
            .put("message", message)
        request(
            baseUrl = baseUrl,
            path = "/api/device/sync",
            method = "POST",
            token = token,
            body = JSONObject().put("status", message).put("order", order),
        )
    }

    private fun request(
        baseUrl: String,
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val normalized = normalizeBaseUrl(baseUrl)
        val connection = URL("$normalized$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                throw IllegalStateException(message?.takeIf(String::isNotBlank) ?: "Dashboard request failed ($status).")
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid dashboard address.")
        val localHttp = uri.scheme == "http" && (uri.host == "localhost" || uri.host == "10.0.2.2")
        if (uri.host.isNullOrBlank() || (uri.scheme != "https" && !localHttp) || uri.rawQuery != null || uri.rawFragment != null) {
            throw IllegalArgumentException("Use the secure HTTPS dashboard address.")
        }
        return normalized
    }
}

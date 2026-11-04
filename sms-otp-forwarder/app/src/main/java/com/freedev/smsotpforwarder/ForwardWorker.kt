package com.freedev.smsotpforwarder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts the OTP from a message body and POSTs a JSON payload to the
 * configured API. Runs off the main thread via WorkManager, which retries
 * automatically on transient failures (no network, 5xx).
 *
 * Payload shape:
 * {
 *   "sender":    "VERIFY",
 *   "message":   "Your code is 123456",
 *   "otp":       "123456",
 *   "timestamp": 1730731200000,
 *   "device":    "Pixel 7"
 * }
 */
class ForwardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sender = inputData.getString(KEY_SENDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val time = inputData.getLong(KEY_TIME, System.currentTimeMillis())

        val prefs = Prefs(applicationContext)
        val url = prefs.apiUrl
        if (url.isBlank()) return@withContext Result.failure()

        val otp = extractOtp(body, prefs.otpRegex)
        if (otp == null && !prefs.forwardWhenNoOtp) {
            Log.d(TAG, "No OTP found and 'forward all' is off; skipping.")
            return@withContext Result.success()
        }

        val payload = JSONObject()
            .put("sender", sender)
            .put("message", body)
            .put("otp", otp ?: JSONObject.NULL)
            .put("timestamp", time)
            .put("device", android.os.Build.MODEL ?: "")
            .toString()

        try {
            val code = post(url, payload, prefs.authHeaderName, prefs.authHeaderValue)
            when {
                code in 200..299 -> {
                    LogStore.add(applicationContext, LogEntry(time, sender, otp ?: "—", "sent ($code)"))
                    Result.success()
                }
                code in 500..599 -> {
                    LogStore.add(applicationContext, LogEntry(time, sender, otp ?: "—", "retry ($code)"))
                    Result.retry()
                }
                else -> {
                    LogStore.add(applicationContext, LogEntry(time, sender, otp ?: "—", "failed ($code)"))
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST failed, will retry", e)
            LogStore.add(applicationContext, LogEntry(time, sender, otp ?: "—", "retry (${e.javaClass.simpleName})"))
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun post(
        urlStr: String,
        jsonBody: String,
        headerName: String,
        headerValue: String
    ): Int {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (headerName.isNotBlank() && headerValue.isNotBlank()) {
                setRequestProperty(headerName, headerValue)
            }
        }
        return try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun extractOtp(body: String, regex: String): String? {
        return try {
            val m = Regex(regex).find(body) ?: return null
            // Prefer the first capture group if present, else the whole match.
            (m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: m.value)
        } catch (e: Exception) {
            Log.w(TAG, "Bad OTP regex, forwarding without OTP", e)
            null
        }
    }

    companion object {
        private const val TAG = "ForwardWorker"
        private const val MAX_ATTEMPTS = 5
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIME = "time"
    }
}

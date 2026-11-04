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
        try {
            val m = Regex(regex).find(body)
            if (m != null) {
                val v = m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: m.value
                if (v.isNotEmpty()) return v
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bad OTP regex, trying spelled-out words", e)
        }
        return extractSpelledOutOtp(body)
    }

    private fun extractSpelledOutOtp(body: String, minLen: Int = 4): String? {
        val tokens = body.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        var best = StringBuilder()
        var current = StringBuilder()
        for (t in tokens) {
            val digit = WORD_TO_DIGIT[t]
            if (digit != null) {
                current.append(digit)
            } else {
                if (current.length > best.length) best = StringBuilder(current)
                current = StringBuilder()
            }
        }
        if (current.length > best.length) best = current
        return best.toString().takeIf { it.length >= minLen }
    }

    companion object {
        private const val TAG = "ForwardWorker"
        private const val MAX_ATTEMPTS = 5
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIME = "time"

        private val WORD_TO_DIGIT = mapOf(
            "zero" to '0',
            "one" to '1',
            "two" to '2',
            "three" to '3',
            "four" to '4',
            "five" to '5',
            "six" to '6',
            "seven" to '7',
            "eight" to '8',
            "nine" to '9'
        )
    }
}
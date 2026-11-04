package com.freedev.smsotpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fires on every incoming SMS (manifest-registered, so it runs even when the
 * app UI is closed). It reassembles multipart messages per sender and hands
 * each one to [ForwardWorker], which does the network call with retries.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = Prefs(context)
        if (!prefs.enabled) return
        if (prefs.apiUrl.isBlank()) {
            Log.w(TAG, "SMS received but no API URL configured; skipping.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A long SMS arrives as several parts; join bodies per originating address.
        val bySender = LinkedHashMap<String, StringBuilder>()
        var timestamp = System.currentTimeMillis()
        for (part in messages) {
            val sender = part.displayOriginatingAddress ?: part.originatingAddress ?: "unknown"
            bySender.getOrPut(sender) { StringBuilder() }.append(part.messageBody ?: "")
            timestamp = part.timestampMillis.takeIf { it > 0 } ?: timestamp
        }

        for ((sender, builder) in bySender) {
            val body = builder.toString()
            if (!passesSenderFilter(sender, prefs.senderFilter)) {
                Log.d(TAG, "Sender '$sender' filtered out.")
                continue
            }

            val data = Data.Builder()
                .putString(ForwardWorker.KEY_SENDER, sender)
                .putString(ForwardWorker.KEY_BODY, body)
                .putLong(ForwardWorker.KEY_TIME, timestamp)
                .build()

            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    private fun passesSenderFilter(sender: String, filter: String): Boolean {
        if (filter.isBlank()) return true
        val needles = filter.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (needles.isEmpty()) return true
        val s = sender.lowercase()
        return needles.any { s.contains(it) }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}

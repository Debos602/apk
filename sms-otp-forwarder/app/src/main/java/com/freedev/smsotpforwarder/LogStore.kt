package com.freedev.smsotpforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One forwarding attempt, shown in the activity list on the main screen. */
data class LogEntry(
    val time: Long,
    val sender: String,
    val otp: String,
    val status: String
)

/**
 * Keeps the last [MAX] forwarding attempts in SharedPreferences so the user
 * can see what happened without any server/database.
 */
object LogStore {

    private const val MAX = 50
    private const val FILE = "otp_forwarder_log"
    private const val KEY = "entries"

    fun add(context: Context, entry: LogEntry) {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(KEY, "[]"))
        val obj = JSONObject()
            .put("time", entry.time)
            .put("sender", entry.sender)
            .put("otp", entry.otp)
            .put("status", entry.status)
        // newest first
        val out = JSONArray()
        out.put(obj)
        for (i in 0 until minOf(arr.length(), MAX - 1)) out.put(arr.get(i))
        sp.edit().putString(KEY, out.toString()).apply()
    }

    fun all(context: Context): List<LogEntry> {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(KEY, "[]"))
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    LogEntry(
                        time = o.optLong("time"),
                        sender = o.optString("sender"),
                        otp = o.optString("otp"),
                        status = o.optString("status")
                    )
                )
            }
        }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}

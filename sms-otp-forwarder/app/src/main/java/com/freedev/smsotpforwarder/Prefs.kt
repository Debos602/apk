package com.freedev.smsotpforwarder

import android.content.Context

/**
 * Thin wrapper over SharedPreferences holding everything the user configures
 * on the settings screen. Read from both the UI and the background worker.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("otp_forwarder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Full endpoint the OTP/SMS is POSTed to, e.g. https://api.example.com/otp */
    var apiUrl: String
        get() = sp.getString(KEY_API_URL, "").orEmpty()
        set(v) = sp.edit().putString(KEY_API_URL, v.trim()).apply()

    /** Optional auth header name, e.g. Authorization or X-Api-Key. Blank = none. */
    var authHeaderName: String
        get() = sp.getString(KEY_AUTH_NAME, "").orEmpty()
        set(v) = sp.edit().putString(KEY_AUTH_NAME, v.trim()).apply()

    /** Optional auth header value, e.g. "Bearer xxxxx" or an API key. */
    var authHeaderValue: String
        get() = sp.getString(KEY_AUTH_VALUE, "").orEmpty()
        set(v) = sp.edit().putString(KEY_AUTH_VALUE, v.trim()).apply()

    /**
     * Comma-separated list of sender fragments to keep (case-insensitive,
     * substring match). Blank = forward messages from every sender.
     */
    var senderFilter: String
        get() = sp.getString(KEY_SENDER_FILTER, "").orEmpty()
        set(v) = sp.edit().putString(KEY_SENDER_FILTER, v.trim()).apply()

    /** Regex used to pull the OTP out of the message body. */
    var otpRegex: String
        get() = sp.getString(KEY_OTP_REGEX, DEFAULT_OTP_REGEX).orEmpty()
        set(v) = sp.edit().putString(KEY_OTP_REGEX, v.trim()).apply()

    /** When true, forward every message even if no OTP is matched. */
    var forwardWhenNoOtp: Boolean
        get() = sp.getBoolean(KEY_FORWARD_NO_OTP, true)
        set(v) = sp.edit().putBoolean(KEY_FORWARD_NO_OTP, v).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_URL = "api_url"
        private const val KEY_AUTH_NAME = "auth_name"
        private const val KEY_AUTH_VALUE = "auth_value"
        private const val KEY_SENDER_FILTER = "sender_filter"
        private const val KEY_OTP_REGEX = "otp_regex"
        private const val KEY_FORWARD_NO_OTP = "forward_no_otp"

        /** Matches the first run of 4–8 digits (optionally grouped like 123-456). */
        const val DEFAULT_OTP_REGEX = "\\b(\\d{4,8})\\b"
    }
}

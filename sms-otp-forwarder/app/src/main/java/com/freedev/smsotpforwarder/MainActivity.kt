package com.freedev.smsotpforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    onTest = { url, name, value, onResult -> runTest(url, name, value, onResult) }
                )
            }
        }
    }

    /** Fire a sample payload at the endpoint so the user can verify connectivity. */
    private fun runTest(
        url: String,
        headerName: String,
        headerValue: String,
        onResult: (String) -> Unit
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val body = JSONObject()
                        .put("sender", "TEST")
                        .put("message", "This is a test from SMS OTP Forwarder")
                        .put("otp", "123456")
                        .put("timestamp", System.currentTimeMillis())
                        .put("device", Build.MODEL ?: "")
                        .toString()
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        if (headerName.isNotBlank() && headerValue.isNotBlank()) {
                            setRequestProperty(headerName, headerValue)
                        }
                    }
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    "Server responded: HTTP $code"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            onResult(result)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onTest: (String, String, String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var apiUrl by remember { mutableStateOf(prefs.apiUrl) }
    var authName by remember { mutableStateOf(prefs.authHeaderName) }
    var authValue by remember { mutableStateOf(prefs.authHeaderValue) }
    var senderFilter by remember { mutableStateOf(prefs.senderFilter) }
    var otpRegex by remember { mutableStateOf(prefs.otpRegex) }
    var forwardAll by remember { mutableStateOf(prefs.forwardWhenNoOtp) }
    var testResult by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(LogStore.all(context)) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun saveAll() {
        prefs.apiUrl = apiUrl
        prefs.authHeaderName = authName
        prefs.authHeaderValue = authValue
        prefs.senderFilter = senderFilter
        prefs.otpRegex = otpRegex
        prefs.forwardWhenNoOtp = forwardAll
        prefs.enabled = enabled
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SMS OTP Forwarder") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permission card
            if (!hasSmsPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "SMS permission is required to read incoming messages.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text("Grant SMS permission") }
                    }
                }
            } else {
                Text("✓ SMS permission granted", style = MaterialTheme.typography.bodyMedium)
            }

            // Master switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Forwarding enabled", style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    prefs.enabled = it
                })
            }

            Divider()

            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API URL (POST endpoint)") },
                placeholder = { Text("https://api.example.com/otp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = authName,
                onValueChange = { authName = it },
                label = { Text("Auth header name (optional)") },
                placeholder = { Text("Authorization") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = authValue,
                onValueChange = { authValue = it },
                label = { Text("Auth header value (optional)") },
                placeholder = { Text("Bearer xxxxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = senderFilter,
                onValueChange = { senderFilter = it },
                label = { Text("Sender filter (optional, comma-separated)") },
                placeholder = { Text("VERIFY, 1234, bKash") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = otpRegex,
                onValueChange = { otpRegex = it },
                label = { Text("OTP regex") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Forward even if no OTP matched")
                Switch(checked = forwardAll, onCheckedChange = { forwardAll = it })
            }

            Button(
                onClick = { saveAll() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save settings") }

            OutlinedButton(
                onClick = {
                    saveAll()
                    testResult = "Sending test…"
                    onTest(apiUrl, authName, authValue) { testResult = it }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save & send test request") }

            if (testResult.isNotBlank()) {
                Text(testResult, style = MaterialTheme.typography.bodyMedium)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    LogStore.clear(context)
                    logs = emptyList()
                }) { Text("Clear") }
            }
            OutlinedButton(
                onClick = { logs = LogStore.all(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh log") }

            if (logs.isEmpty()) {
                Text("No forwards yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                logs.forEach { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${e.sender}  •  OTP: ${e.otp}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${fmt.format(Date(e.time))}  —  ${e.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

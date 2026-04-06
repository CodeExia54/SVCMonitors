package moe.fuqiuluo.mamu.ui.screen

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.mamu.service.FloatingWindowService
import moe.fuqiuluo.mamu.svc.repo.SvcRuntimeManager

@Composable
fun SvcMonitorScreen() {
    val state by SvcRuntimeManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SVC Runtime", style = MaterialTheme.typography.titleMedium)
                Text("Running: ${state.running}")
                Text("Events total: ${state.eventsTotal}")
                Text("Enabled: ${state.status?.enabled ?: false}")
                Text("Target UID: ${state.status?.targetUid ?: -1}")
                if (state.lastError != null) {
                    Text("Last error: ${state.lastError}", color = MaterialTheme.colorScheme.error)
                }
                if (opMessage != null) {
                    Text(opMessage!!, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { SvcRuntimeManager.enable() }
                    opMessage = if (ok) "Enable success" else "Enable failed"
                }
            }) { Text("Enable") }
            Button(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { SvcRuntimeManager.disable() }
                    opMessage = if (ok) "Disable success" else "Disable failed"
                }
            }) { Text("Disable") }
            Button(onClick = { SvcRuntimeManager.reset() }) { Text("Reset") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = Intent(context, FloatingWindowService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                opMessage = "Floating service start requested"
            }) { Text("Start Floating") }
        }
    }
}

@Composable
fun SvcFilterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uidText by remember { mutableStateOf("-1") }
    var pkgNameText by remember { mutableStateOf("") }
    var expandPreset by remember { mutableStateOf(false) }
    var opMessage by remember { mutableStateOf<String?>(null) }
    val presets = listOf("re_basic", "re_full", "net_basic", "net_full", "ab_basic", "ab_full")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SVC Filters", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = uidText,
            onValueChange = { uidText = it },
            label = { Text("Target UID (-1 = all)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pkgNameText,
            onValueChange = { pkgNameText = it.trim() },
            label = { Text("Package Name (resolve UID)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    val uid = uidText.toIntOrNull() ?: -1
                    val ok = withContext(Dispatchers.IO) { SvcRuntimeManager.setUid(uid) }
                    opMessage = if (ok) "UID applied: $uid" else "Failed to apply UID: $uid"
                }
            }) { Text("Apply UID") }
            Button(onClick = {
                scope.launch {
                    if (pkgNameText.isBlank()) {
                        opMessage = "Please input package name first"
                        return@launch
                    }
                    val uid = withContext(Dispatchers.IO) {
                        kotlin.runCatching {
                            context.packageManager.getApplicationInfo(pkgNameText, 0).uid
                        }.getOrNull()
                    }
                    if (uid == null) {
                        opMessage = "Package not found: $pkgNameText"
                        return@launch
                    }
                    val ok = withContext(Dispatchers.IO) { SvcRuntimeManager.setUid(uid) }
                    if (ok) uidText = uid.toString()
                    opMessage = if (ok) "Package UID applied: $uid" else "Failed to apply package UID: $uid"
                }
            }) { Text("Apply Package") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { expandPreset = true }) { Text("Select Preset") }
            DropdownMenu(expanded = expandPreset, onDismissRequest = { expandPreset = false }) {
                presets.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p) },
                        onClick = {
                            expandPreset = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { SvcRuntimeManager.setPreset(p) }
                                opMessage = if (ok) "Preset applied: $p" else "Preset failed: $p"
                            }
                        }
                    )
                }
            }
        }

        if (opMessage != null) {
            Text(opMessage!!, color = MaterialTheme.colorScheme.primary)
        }

        Text("Includes reverse/network/AB preset shortcuts + package UID resolve.")
    }
}

@Composable
fun SvcEventsScreen() {
    val state by SvcRuntimeManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Latest SVC Events", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }

        items(state.latestEvents.reversed(), key = { it.seq }) { ev ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("#${ev.seq}  ${ev.name} (nr=${ev.nr})", style = MaterialTheme.typography.labelLarge)
                    Text("pid=${ev.pid} tgid=${ev.tgid} uid=${ev.uid} ret=${ev.ret}")
                    if (ev.desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(ev.desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

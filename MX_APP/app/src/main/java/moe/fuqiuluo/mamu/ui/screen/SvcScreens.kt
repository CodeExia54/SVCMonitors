package moe.fuqiuluo.mamu.ui.screen

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.fuqiuluo.mamu.svc.repo.SvcRuntimeManager

@Composable
fun SvcMonitorScreen() {
    val state by SvcRuntimeManager.state.collectAsState()

    LaunchedEffect(Unit) {
        SvcRuntimeManager.start()
    }

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
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { SvcRuntimeManager.enable() }) { Text("Enable") }
            Button(onClick = { SvcRuntimeManager.disable() }) { Text("Disable") }
            Button(onClick = { SvcRuntimeManager.reset() }) { Text("Reset") }
        }
    }
}

@Composable
fun SvcFilterScreen() {
    var uidText by remember { mutableStateOf("-1") }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val uid = uidText.toIntOrNull() ?: -1
                SvcRuntimeManager.setUid(uid)
            }) { Text("Apply UID") }
            Button(onClick = { SvcRuntimeManager.setPreset("re_basic") }) { Text("Preset re_basic") }
            Button(onClick = { SvcRuntimeManager.setPreset("re_full") }) { Text("Preset re_full") }
        }

        Text("NR-specific selector UI will replace this quick filter in next pass.")
    }
}

@Composable
fun SvcEventsScreen() {
    val state by SvcRuntimeManager.state.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { SvcRuntimeManager.start() }
    }

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

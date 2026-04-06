package moe.fuqiuluo.mamu.ui.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.mmkv.MMKV
import moe.fuqiuluo.mamu.data.settings.autoStartFloatingWindow
import moe.fuqiuluo.mamu.data.settings.keepFloatingServiceAlive
import moe.fuqiuluo.mamu.service.FloatingWindowService
import moe.fuqiuluo.mamu.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SvcMainScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mmkv = remember { MMKV.defaultMMKV() }
    var uidInput by remember { mutableStateOf("-1") }
    var customNrs by remember { mutableStateOf("openat,read,write,mmap,mprotect,clone,execve,connect") }
    var selectedPreset by remember { mutableStateOf("re_basic") }
    var keepAlive by remember { mutableStateOf(mmkv.keepFloatingServiceAlive) }
    var autoStart by remember { mutableStateOf(mmkv.autoStartFloatingWindow) }
    val presets = listOf("re_basic", "re_full", "file", "net", "proc", "mem", "security", "all")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SVC Workspace (MX UI)") },
                actions = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Status", fontWeight = FontWeight.Bold)
                    Text("Root: ${if (uiState.hasRootAccess) "Granted" else "Missing"}")
                    Text("Floating: ${if (uiState.isFloatingWindowActive) "Running" else "Stopped"}")
                    Text("Driver: ${uiState.dashboardDriverInfo?.status ?: "Unknown"}")
                }
            }

            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Icon(Icons.Default.Window, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Floating Controls", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto start floating service")
                        Switch(checked = autoStart, onCheckedChange = {
                            autoStart = it
                            mmkv.autoStartFloatingWindow = it
                        })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Keep alive on task remove")
                        Switch(checked = keepAlive, onCheckedChange = {
                            keepAlive = it
                            mmkv.keepFloatingServiceAlive = it
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { toggleFloating(context, false) }) { Text("Start Floating") }
                        Button(onClick = { toggleFloating(context, true) }) { Text("Stop Floating") }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("SVC Main-Screen Controls", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = uidInput,
                        onValueChange = { uidInput = it },
                        label = { Text("Target UID (-1 for all)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Preset")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { item ->
                            FilterChip(
                                selected = selectedPreset == item,
                                onClick = { selectedPreset = item },
                                label = { Text(item) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customNrs,
                        onValueChange = { customNrs = it },
                        label = { Text("Custom NRs / names") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Text(
                        "This replaces old MX-first entry flow with an SVC-first workspace. " +
                            "Next step is wiring these controls to your SVC bridge APIs.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("What was changed in this iteration", fontWeight = FontWeight.Bold)
                    }
                    Text("• MX home flow is replaced at activity entry with this SVC workspace UI.")
                    Text("• Floating lifecycle controls are now explicit on this screen.")
                    Text("• Keep-alive and auto-start toggles are persisted via MMKV.")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Window class: ${windowSizeClass.widthSizeClass}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun toggleFloating(context: Context, isActive: Boolean) {
    val intent = Intent(context, FloatingWindowService::class.java)
    if (isActive) {
        context.stopService(intent)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

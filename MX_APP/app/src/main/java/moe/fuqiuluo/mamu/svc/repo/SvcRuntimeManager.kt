package moe.fuqiuluo.mamu.svc.repo

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.fuqiuluo.mamu.svc.bridge.SvcKpmBridge
import moe.fuqiuluo.mamu.svc.model.SvcEvent
import moe.fuqiuluo.mamu.svc.model.SvcModuleStatus

/**
 * Service-safe runtime manager for SVC monitoring inside MX.
 *
 * It is lifecycle-independent from Activity and can run inside floating/background service.
 */
object SvcRuntimeManager {
    private const val TAG = "SvcRuntimeManager"

    data class RuntimeState(
        val running: Boolean = false,
        val status: SvcModuleStatus? = null,
        val eventsTotal: Long = 0,
        val latestEvents: List<SvcEvent> = emptyList(),
        val lastError: String? = null
    )

    private val collector = SvcCollector()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    fun start(pollMs: Long = 500L) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            _state.update { it.copy(running = true, lastError = null) }

            while (isActive) {
                try {
                    val (status, events) = collector.pollOnce()
                    if (status?.ok == true) {
                        _state.update {
                            it.copy(
                                status = status,
                                eventsTotal = it.eventsTotal + events.size,
                                latestEvents = events.takeLast(64),
                                lastError = null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                eventsTotal = it.eventsTotal + events.size,
                                latestEvents = events.takeLast(64)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "poll loop error", e)
                    _state.update { it.copy(lastError = e.message ?: "unknown error") }
                }

                delay(pollMs)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(running = false) }
    }

    fun reset() {
        collector.resetOffsets()
        _state.update { RuntimeState() }
    }

    fun enable(): Boolean = SvcKpmBridge.enable().success
    fun disable(): Boolean = SvcKpmBridge.disable().success
    fun setUid(uid: Int): Boolean = SvcKpmBridge.setUid(uid).success
    fun setPreset(name: String): Boolean = SvcKpmBridge.preset(name).success
}

package moe.fuqiuluo.mamu.svc.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.fuqiuluo.mamu.svc.bridge.SvcKpmBridge
import moe.fuqiuluo.mamu.svc.model.SvcEvent
import moe.fuqiuluo.mamu.svc.model.SvcModuleStatus
import moe.fuqiuluo.mamu.svc.parser.SvcBinEventParser
import moe.fuqiuluo.mamu.svc.parser.SvcStatusParser

/**
 * Lightweight collector that can be driven by MX service/viewmodel timer.
 *
 * This is intentionally UI-agnostic so it can run in foreground/background service.
 */
class SvcCollector(
    private val nameResolver: (Int) -> String = { "nr_$it" }
) {
    private var offset: Long = 0L
    private var tailBuf: ByteArray = ByteArray(0)
    private var useJsonFallback = false
    private var emptyBinPolls = 0

    suspend fun pollOnce(maxBytes: Int = 256 * 1024): Pair<SvcModuleStatus?, List<SvcEvent>> {
        return withContext(Dispatchers.IO) {
            val events = ArrayList<SvcEvent>()

            if (!useJsonFallback) {
                val chunk = SvcKpmBridge.readEventFileChunk(offset, maxBytes)
                if (chunk.isNotEmpty()) {
                    offset += chunk.size.toLong()
                    val merged = ByteArray(tailBuf.size + chunk.size)
                    System.arraycopy(tailBuf, 0, merged, 0, tailBuf.size)
                    System.arraycopy(chunk, 0, merged, tailBuf.size, chunk.size)

                    val parsed = SvcBinEventParser.parse(merged, nameResolver)
                    if (parsed.consumedBytes > 0 && parsed.consumedBytes < merged.size) {
                        tailBuf = merged.copyOfRange(parsed.consumedBytes, merged.size)
                    } else if (parsed.consumedBytes >= merged.size) {
                        tailBuf = ByteArray(0)
                    }

                    if (parsed.events.isNotEmpty()) {
                        emptyBinPolls = 0
                        events.addAll(parsed.events)
                    } else {
                        emptyBinPolls++
                    }
                } else {
                    emptyBinPolls++
                }

                if (emptyBinPolls >= 8) {
                    useJsonFallback = true
                }
            }

            if (useJsonFallback) {
                val drain = SvcKpmBridge.drain(1024)
                if (drain.success && drain.output.isNotBlank()) {
                    val parsed = SvcStatusParser.parseDrain(drain.output)
                    if (parsed.ok && parsed.events.isNotEmpty()) {
                        events.addAll(parsed.events)
                    }
                }
            }

            val status = SvcKpmBridge.status()
            val moduleStatus = if (status.success && status.output.isNotBlank()) {
                SvcStatusParser.parseStatus(status.output)
            } else {
                null
            }

            moduleStatus to events
        }
    }

    fun resetOffsets() {
        offset = 0L
        tailBuf = ByteArray(0)
        useJsonFallback = false
        emptyBinPolls = 0
    }
}

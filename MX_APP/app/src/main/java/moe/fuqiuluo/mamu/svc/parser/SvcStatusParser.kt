package moe.fuqiuluo.mamu.svc.parser

import moe.fuqiuluo.mamu.svc.model.SvcDrainResult
import moe.fuqiuluo.mamu.svc.model.SvcEvent
import moe.fuqiuluo.mamu.svc.model.SvcHookInfo
import moe.fuqiuluo.mamu.svc.model.SvcModuleStatus
import org.json.JSONObject

object SvcStatusParser {
    fun parseStatus(json: String): SvcModuleStatus {
        return try {
            val j = JSONObject(json)
            if (!j.optBoolean("ok", false)) {
                return SvcModuleStatus(false, error = j.optString("error", "unknown"))
            }

            val loggingNrs = buildList {
                val arr = j.optJSONArray("logging_nrs") ?: return@buildList
                for (i in 0 until arr.length()) add(arr.optInt(i))
            }

            val hooks = buildList {
                val arr = j.optJSONArray("hooks") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val h = arr.optJSONObject(i) ?: continue
                    add(
                        SvcHookInfo(
                            nr = h.optInt("nr"),
                            name = h.optString("name", ""),
                            method = h.optString("method", "")
                        )
                    )
                }
            }

            SvcModuleStatus(
                ok = true,
                version = j.optString("version", ""),
                enabled = j.optBoolean("enabled", false),
                targetUid = j.optInt("target_uid", -1),
                hooksInstalled = j.optInt("hooks_installed", 0),
                nrsLogging = j.optInt("nrs_logging", 0),
                eventsTotal = j.optInt("events_total", 0),
                eventsBuffered = j.optInt("events_buffered", 0),
                tier2 = j.optBoolean("tier2", false),
                loggingNrs = loggingNrs,
                hooks = hooks
            )
        } catch (e: Exception) {
            SvcModuleStatus(false, error = "Parse error: ${e.message}")
        }
    }

    fun parseDrain(json: String): SvcDrainResult {
        return try {
            val j = JSONObject(json)
            if (!j.optBoolean("ok", false)) {
                return SvcDrainResult(false, error = j.optString("error", ""))
            }

            val events = buildList {
                val arr = j.optJSONArray("events") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    add(parseEventObject(e))
                }
            }

            SvcDrainResult(
                ok = true,
                count = j.optInt("count", 0),
                total = j.optInt("total", 0),
                events = events
            )
        } catch (e: Exception) {
            SvcDrainResult(false, error = "Parse error: ${e.message}")
        }
    }

    fun parseEventLines(text: String): List<SvcEvent> {
        val out = ArrayList<SvcEvent>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || !line.startsWith("{")) return@forEach
            runCatching { parseEventObject(JSONObject(line)) }
                .onSuccess { out.add(it) }
        }
        return out
    }

    private fun parseEventObject(e: JSONObject): SvcEvent {
        val bt = buildList {
            val btArr = e.optJSONArray("bt") ?: return@buildList
            for (k in 0 until btArr.length()) {
                add(btArr.optLong(k, 0))
            }
        }

        return SvcEvent(
            seq = e.optLong("seq", 0),
            nr = e.optInt("nr"),
            name = e.optString("name", ""),
            tgid = e.optInt("tgid", e.optInt("pid", 0)),
            pid = e.optInt("pid", e.optInt("tid", e.optInt("tgid", 0))),
            uid = e.optInt("uid"),
            comm = e.optString("comm", ""),
            pc = e.optLong("pc", 0),
            caller = e.optLong("caller", 0),
            fp = e.optLong("fp", 0),
            sp = e.optLong("sp", 0),
            bt = bt,
            cloneFn = e.optLong("clone_fn", 0),
            ret = e.optLong("ret", 0),
            a0 = e.optLong("a0"),
            a1 = e.optLong("a1"),
            a2 = e.optLong("a2"),
            a3 = e.optLong("a3"),
            a4 = e.optLong("a4"),
            a5 = e.optLong("a5"),
            desc = e.optString("desc", "")
        )
    }
}

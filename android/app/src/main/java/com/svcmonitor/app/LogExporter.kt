package com.svcmonitor.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogExporter v8.1 — Export events to CSV or JSON files into app tmp/cache directory.
 */
class LogExporter(private val ctx: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private fun exportDir(): File {
        val dir = File(ctx.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun deviceTmpDir(): File = File("/data/local/tmp")

    private fun canWriteToDir(dir: File): Boolean {
        return try {
            if (!dir.exists()) return false
            val probe = File(dir, ".svc_export_probe_${System.currentTimeMillis()}")
            probe.writeText("ok")
            probe.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun toJsonlLine(ev: StatusParser.SvcEvent): String {
        val bt = ev.bt.joinToString(",")
        return buildString {
            append("{\"seq\":").append(ev.seq)
            append(",\"nr\":").append(ev.nr)
            append(",\"name\":\"").append(escapeJson(ev.name)).append("\"")
            append(",\"pid\":").append(ev.pid)
            append(",\"uid\":").append(ev.uid)
            append(",\"comm\":\"").append(escapeJson(ev.comm)).append("\"")
            append(",\"pc\":").append(ev.pc)
            append(",\"caller\":").append(ev.caller)
            append(",\"fp\":").append(ev.fp)
            append(",\"sp\":").append(ev.sp)
            append(",\"bt\":[").append(bt).append("]")
            append(",\"clone_fn\":").append(ev.cloneFn)
            append(",\"a0\":").append(ev.a0)
            append(",\"a1\":").append(ev.a1)
            append(",\"a2\":").append(ev.a2)
            append(",\"a3\":").append(ev.a3)
            append(",\"a4\":").append(ev.a4)
            append(",\"a5\":").append(ev.a5)
            append(",\"desc\":\"").append(escapeJson(ev.desc)).append("\"")
            append("}\n")
        }
    }

    fun exportCsv(events: List<StatusParser.SvcEvent>): File {
        val ts = dateFormat.format(Date())
        val dir = exportDir()
        val file = File(dir, "svc_events_$ts.csv")

        file.bufferedWriter().use { w ->
            w.write("seq,nr,name,pid,uid,comm,pc,caller,fp,sp,bt,clone_fn,a0,a1,a2,a3,a4,a5,desc")
            w.newLine()
            for (ev in events) {
                val desc = ev.desc.replace("\"", "\"\"")
                w.write("${ev.seq},${ev.nr},${ev.name},${ev.pid},${ev.uid},${ev.comm},")
                val bt = ev.bt.joinToString("|")
                w.write("${ev.pc},${ev.caller},${ev.fp},${ev.sp},\"$bt\",${ev.cloneFn},")
                w.write("${ev.a0},${ev.a1},${ev.a2},${ev.a3},${ev.a4},${ev.a5},")
                w.write("\"$desc\"")
                w.newLine()
            }
        }
        return file
    }

    fun exportJson(events: List<StatusParser.SvcEvent>): File {
        val ts = dateFormat.format(Date())
        val dir = exportDir()
        val file = File(dir, "svc_events_$ts.json")

        val arr = JSONArray()
        for (ev in events) {
            arr.put(JSONObject().apply {
                put("seq", ev.seq)
                put("nr", ev.nr)
                put("name", ev.name)
                put("pid", ev.pid)
                put("uid", ev.uid)
                put("comm", ev.comm)
                put("pc", ev.pc)
                put("caller", ev.caller)
                put("fp", ev.fp)
                put("sp", ev.sp)
                put("bt", JSONArray(ev.bt))
                put("clone_fn", ev.cloneFn)
                put("a0", ev.a0)
                put("a1", ev.a1)
                put("a2", ev.a2)
                put("a3", ev.a3)
                put("a4", ev.a4)
                put("a5", ev.a5)
                put("desc", ev.desc)
            })
        }

        file.writeText(arr.toString(2))
        return file
    }

    fun exportJsonlForPc(events: List<StatusParser.SvcEvent>): File {
        val target = if (canWriteToDir(deviceTmpDir())) {
            File(deviceTmpDir(), "svc_events.jsonl")
        } else {
            val ts = dateFormat.format(Date())
            File(exportDir(), "svc_events_$ts.jsonl")
        }

        target.bufferedWriter().use { w ->
            for (ev in events) {
                w.write(toJsonlLine(ev))
            }
        }
        return target
    }
}

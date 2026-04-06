package moe.fuqiuluo.mamu.svc.bridge

import moe.fuqiuluo.mamu.svc.model.SvcCommandResult
import moe.fuqiuluo.mamu.utils.RootConfigManager
import moe.fuqiuluo.mamu.utils.RootShellExecutor
import moe.fuqiuluo.mamu.utils.ShellResult

/**
 * MX-side bridge for controlling svc_monitor KPM module.
 *
 * This is the first extraction step so SVC monitoring logic can live in MX app
 * lifecycle/services instead of the legacy android/ app.
 */
object SvcKpmBridge {
    private const val MODULE = "svc_monitor"

    private data class KpmCli(val bin: String, val style: String)

    @Volatile
    private var cachedCli: KpmCli? = null

    @Volatile
    private var superKey: String = "XiaoLu0129"

    fun setSuperKey(key: String) {
        superKey = key.trim()
    }

    fun status(): SvcCommandResult = ctl0("status")
    fun sysnames(): SvcCommandResult = ctl0("sysnames")
    fun clear(): SvcCommandResult = ctl0("clear")
    fun enable(): SvcCommandResult = ctl0("enable")
    fun disable(): SvcCommandResult = ctl0("disable")
    fun setUid(uid: Int): SvcCommandResult = ctl0("uid $uid")
    fun setNrs(nrs: List<Int>): SvcCommandResult = ctl0("set_nrs ${nrs.joinToString(",")}")
    fun enableNr(nr: Int): SvcCommandResult = ctl0("enable_nr $nr")
    fun disableNr(nr: Int): SvcCommandResult = ctl0("disable_nr $nr")
    fun preset(name: String): SvcCommandResult = ctl0("preset $name")
    fun drain(max: Int = 1024): SvcCommandResult = ctl0("drain $max")

    fun readEventFileChunk(offset: Long, maxBytes: Int = 262_144): ByteArray {
        resolveKpmCli() ?: return ByteArray(0)
        val path = discoverEventPath()
        val cmd = "if [ -f '$path' ]; then dd if='$path' bs=1 skip=$offset count=$maxBytes 2>/dev/null | base64; fi"
        return when (val r = shellExec(cmd)) {
            is ShellResult.Success -> android.util.Base64.decode(r.output.trim(), android.util.Base64.DEFAULT)
            else -> ByteArray(0)
        }
    }

    private fun ctl0(command: String): SvcCommandResult {
        val cli = resolveKpmCli()
            ?: return SvcCommandResult(false, "", "kpatch/ksud not found")

        val shellCmd = when (cli.style) {
            "ksud" -> "${cli.bin} kpm control $MODULE '$command'"
            else -> "${cli.bin} $superKey kpm ctl0 $MODULE '$command'"
        }

        return when (val r = shellExec(shellCmd)) {
            is ShellResult.Success -> SvcCommandResult(true, r.output)
            is ShellResult.Error -> SvcCommandResult(false, r.message, r.message)
            is ShellResult.Timeout -> SvcCommandResult(false, "", "timeout=${r.duration}")
        }
    }

    private fun discoverEventPath(): String {
        val status = ctl0("status")
        if (status.success) {
            val output = status.output
            val key = "\"event_path\":\""
            val idx = output.indexOf(key)
            if (idx > 0) {
                val begin = idx + key.length
                val end = output.indexOf('"', begin)
                if (end > begin) return output.substring(begin, end)
            }
        }
        return "/data/local/tmp/svc_events.bin"
    }

    private fun resolveKpmCli(): KpmCli? {
        cachedCli?.let { return it }

        val kpatchCandidates = listOf(
            "/data/adb/ap/bin/kpatch",
            "/data/adb/ksu/bin/kpatch",
            "/data/adb/ksud/bin/kpatch",
            "/data/adb/kpatch/bin/kpatch"
        )
        for (path in kpatchCandidates) {
            if (pathExists(path)) return KpmCli(path, "kpatch").also { cachedCli = it }
        }

        val ksudCandidates = listOf(
            "/data/adb/ksu/bin/ksud",
            "/data/adb/ksud/bin/ksud"
        )
        for (path in ksudCandidates) {
            if (pathExists(path)) return KpmCli(path, "ksud").also { cachedCli = it }
        }

        commandV("kpatch-android")?.let {
            return KpmCli(it, "kpatch").also { cachedCli = it }
        }
        commandV("kpatch")?.let {
            return KpmCli(it, "kpatch").also { cachedCli = it }
        }
        commandV("ksud")?.let {
            return KpmCli(it, "ksud").also { cachedCli = it }
        }

        return null
    }

    private fun pathExists(path: String): Boolean {
        val cmd = "if [ -x '$path' ]; then echo ok; fi"
        return when (val r = shellExec(cmd)) {
            is ShellResult.Success -> r.output.trim() == "ok"
            else -> false
        }
    }

    private fun commandV(bin: String): String? {
        val cmd = "command -v $bin 2>/dev/null"
        return when (val r = shellExec(cmd)) {
            is ShellResult.Success -> r.output.trim().ifEmpty { null }
            else -> null
        }
    }

    private fun shellExec(cmd: String): ShellResult {
        val suCmd = RootConfigManager.getCustomRootCommand()
        return RootShellExecutor.exec(suCmd, cmd)
    }
}

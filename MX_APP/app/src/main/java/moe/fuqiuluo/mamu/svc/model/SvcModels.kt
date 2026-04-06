package moe.fuqiuluo.mamu.svc.model

data class SvcHookInfo(
    val nr: Int,
    val name: String,
    val method: String
)

data class SvcModuleStatus(
    val ok: Boolean,
    val version: String = "",
    val enabled: Boolean = false,
    val targetUid: Int = -1,
    val hooksInstalled: Int = 0,
    val nrsLogging: Int = 0,
    val eventsTotal: Int = 0,
    val eventsBuffered: Int = 0,
    val tier2: Boolean = false,
    val loggingNrs: List<Int> = emptyList(),
    val hooks: List<SvcHookInfo> = emptyList(),
    val error: String = ""
)

data class SvcEvent(
    val seq: Long = 0,
    val nr: Int,
    val name: String,
    val tgid: Int,
    val pid: Int,
    val uid: Int,
    val comm: String,
    val pc: Long = 0,
    val caller: Long = 0,
    val fp: Long = 0,
    val sp: Long = 0,
    val bt: List<Long> = emptyList(),
    val cloneFn: Long = 0,
    val ret: Long = 0,
    val a0: Long,
    val a1: Long,
    val a2: Long,
    val a3: Long,
    val a4: Long,
    val a5: Long,
    val desc: String
)

data class SvcDrainResult(
    val ok: Boolean,
    val count: Int = 0,
    val total: Int = 0,
    val events: List<SvcEvent> = emptyList(),
    val error: String = ""
)

data class SvcCommandResult(
    val success: Boolean,
    val output: String,
    val error: String = ""
)

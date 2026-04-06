package moe.fuqiuluo.mamu

import android.app.Application
import android.util.Log
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

private const val TAG = "MamuApplication"

class MamuApplication : Application() {
    companion object {
        lateinit var instance: MamuApplication
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化 MMKV
        MMKV.initialize(this)

        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, throwable: Throwable ->
            if (throwable.message != null && throwable.message!!.contains("agent.so")) {
                clearCodeCache()
                Log.w(TAG, "FUck Xiaomi!!!!!!!!!!!!!")
            } else {
                Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            }
        }

        Log.d(TAG, "SVC application bootstrap initialized")
    }

    private fun clearCodeCache() {
        val codeCacheDir = File(applicationInfo.dataDir, "code_cache")
        codeCacheDir.deleteRecursively()
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

}

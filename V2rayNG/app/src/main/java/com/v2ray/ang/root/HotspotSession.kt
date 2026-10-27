package com.v2ray.ang.root

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.AtomicFile
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Ownership ends with the UI task or process, while the VPN may continue running. */
object HotspotSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun owner(context: Context) = File(context.filesDir, "${AppConfig.ROOT_RUNTIME_DIR}/hotspot.owner")

    @Volatile private var resetJob: kotlinx.coroutines.Job? = null
    fun isResetting(): Boolean = resetJob?.isActive == true
    suspend fun claim(context: Context): Boolean {
        resetJob?.join()
        return writeOwner(context)
    }

    @Synchronized private fun writeOwner(context: Context): Boolean = try {
        val file = owner(context).apply { parentFile?.mkdirs() }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write("${Process.myPid()} ${startTime(Process.myPid())}".toByteArray())
            atomic.finishWrite(output)
            true
        } catch (e: Exception) { atomic.failWrite(output); throw e }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Hotspot: recording the UI owner failed", e)
        release(context)
        false
    }

    @Synchronized fun release(context: Context) {
        MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, false)
        val file = owner(context)
        if (file.exists() && !file.delete()) LogUtil.e(AppConfig.TAG, "Hotspot: removing the UI owner failed")
    }

    fun releaseAsync(context: Context) { val app = context.applicationContext; scope.launch { release(app) } }

    fun resetOnFreshUiProcess(context: Context) {
        if (Application.getProcessName() == context.packageName) resetJob = scope.launch { release(context.applicationContext) }
    }

    fun startTime(pid: Int): String = File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')[19]
}

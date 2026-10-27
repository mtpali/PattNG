package com.v2ray.ang.service

import android.app.Application
import android.app.Service
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Decisions for one Android service instance; no framework or native dependencies. */
internal class ServiceLifecycleGate {
    private var starting = false
    private var stopping = false
    private var restartPending = false
    @Synchronized fun start(): Boolean = if (starting || stopping) false else { starting = true; true }
    @Synchronized fun stop(restart: Boolean = false): Boolean {
        if (stopping) {
            if (!restart) restartPending = false
            return false
        }
        stopping = true
        restartPending = restart
        return true
    }
    @Synchronized fun consumeRestart(): Boolean = restartPending.also { restartPending = false }
    @Synchronized fun mayStart(): Boolean = starting && !stopping
}

/** Owns setup and teardown in every run mode, including their cancellation ordering. */
internal class ServiceLifecycle(private val service: Service, private val cleanup: () -> Unit) {
    private val gate = ServiceLifecycleGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var setup: Job? = null
    private var shutdown: Job? = null
    @Volatile private var cleanupSucceeded = false

    fun start(block: () -> Boolean) {
        if (!gate.start()) return
        CoreServiceManager.markStarting()
        setup = scope.launch {
            try {
                if (gate.mayStart() && !block()) stop()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "${service.javaClass.simpleName}: setup failed", e)
                stop()
            }
        }
    }

    fun restart() { beginStop(restart = true) }

    fun stop() { beginStop(restart = false) }

    private fun beginStop(restart: Boolean) {
        if (!gate.stop(restart)) return
        CoreServiceManager.markStopping()
        val watchdog = DaemonStopWatchdog.arm(service.packageName)
        shutdown = scope.launch {
            try {
                setup?.cancelAndJoin()
                cleanup()
                cleanupSucceeded = true
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "${service.javaClass.simpleName}: teardown failed", e)
            } finally {
                if (cleanupSucceeded) watchdog.cancel(false)
                service.stopSelf()
                scope.cancel()
            }
        }
    }

    fun destroy() {
        if (shutdown == null) beginStop(restart = false)
        // Android may destroy a revoked VPN before its asynchronous stop finishes.
        // Bound this wait so owned routes are removed before returning, without an
        // unbounded main-thread wait. The independent watchdog handles stuck JNI.
        runBlocking { withTimeoutOrNull(1_000L) { shutdown?.join() } }
        if (cleanupSucceeded && shutdown?.isCompleted == true) {
            CoreServiceManager.completeStop(service, success = true)
        }
        if (cleanupSucceeded && gate.consumeRestart()) {
            val context = service.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).post { com.v2ray.ang.core.LauncherManager.startService(context) }
        }
    }
}

/** JNI may ignore coroutine cancellation; this deadline runs on a separate Java thread. */
private object DaemonStopWatchdog {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "vpn-stop-watchdog").apply { isDaemon = true }
    }
    fun arm(packageName: String) = scheduler.schedule({
        if (Application.getProcessName() == "$packageName:daemon") Process.killProcess(Process.myPid())
    }, 4, TimeUnit.SECONDS)
}

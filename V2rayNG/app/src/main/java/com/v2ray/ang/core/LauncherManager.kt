package com.v2ray.ang.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

object LauncherManager {

    // Application-owned commands must finish even when a shortcut activity closes.
    private val commandScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val toggleMutex = kotlinx.coroutines.sync.Mutex()
    private val dispatchMutex = kotlinx.coroutines.sync.Mutex()
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val commandLock = Any()
    @Volatile private var reservedStart = false
    @Volatile private var stopping = false
    @Volatile private var startJob: kotlinx.coroutines.Job? = null
    private val stopCallbacks = mutableListOf<() -> Unit>()

    internal suspend fun planToggle(context: Context): TogglePlan = toggleMutex.withLock {
        val state = if (reservedStart || startJob?.isActive == true || stopping) null else MessageHelper.queryServiceState(context)
        synchronized(commandLock) {
            val action = ServiceToggle.decide(state, reservedStart || startJob?.isActive == true, stopping)
            val ticket = if (action == ToggleAction.Start) epoch.incrementAndGet().also { reservedStart = true } else 0L
            TogglePlan(action, ticket)
        }
    }

    internal fun cancelStart(ticket: Long) {
        synchronized(commandLock) {
            if (epoch.get() == ticket) { epoch.incrementAndGet(); reservedStart = false; startJob?.cancel() }
        }
    }

    fun startServiceFromToggle(context: Context): Boolean {
        startService(context)
        return true
    }

    fun startService(context: Context, guid: String? = null): kotlinx.coroutines.Job = dispatchStart(context, guid, null)

    internal suspend fun startReservedService(context: Context, ticket: Long) { dispatchStart(context, null, ticket).join() }

    private fun dispatchStart(context: Context, guid: String?, reservedTicket: Long?): kotlinx.coroutines.Job = synchronized(commandLock) {
        if (!ServiceToggle.mayStart(reservedTicket, epoch.get(), reservedStart, stopping)) {
            return@synchronized kotlinx.coroutines.Job().apply { complete() }
        }
        startJob?.takeIf { it.isActive }?.let { return@synchronized it }
        val ticket = reservedTicket ?: epoch.incrementAndGet()
        reservedStart = true
        val app = context.applicationContext
        commandScope.launch {
            try {
                guid?.let(MmkvManager::setSelectServer)
                dispatchMutex.withLock {
                    if (epoch.get() == ticket && !stopping) {
                        startContextService(app, Utils.getUuid()) { epoch.get() == ticket && !stopping }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Launcher: service dispatch failed", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post { app.toast(R.string.toast_services_failure) }
            } finally {
                if (epoch.get() == ticket) reservedStart = false
            }
        }.also { startJob = it }
    }

    fun stopService(context: Context, onFinished: (() -> Unit)? = null) {
        val app = context.applicationContext
        val pendingStart = synchronized(commandLock) {
            onFinished?.let(stopCallbacks::add)
            if (stopping) return
            val pending = reservedStart || startJob?.isActive == true
            stopping = true
            epoch.incrementAndGet()
            reservedStart = false
            startJob?.cancel()
            pending
        }
        commandScope.launch {
            var stopped = false
            try {
                dispatchMutex.withLock {
                    check(MmkvManager.encodeSettings(AppConfig.CACHE_SERVICE_START_TOKEN, ""))
                    MessageHelper.sendMsg2Service(app, AppConfig.MSG_STATE_STOP, "")
                }
                var original = daemonProcess(app)
                var originalStart = original?.let { processStart(it.pid) }
                val deadline = android.os.SystemClock.elapsedRealtime() + 3_000L
                var absentSince: Long? = null
                while (android.os.SystemClock.elapsedRealtime() < deadline && !stopped) {
                    val state = MessageHelper.queryServiceState(app)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (state == 0 || state == AppConfig.MSG_STATE_NOT_RUNNING) {
                        if (absentSince == null) absentSince = now
                        stopped = !pendingStart || now - absentSince >= 500L
                    } else {
                        absentSince = null
                        MessageHelper.sendMsg2Service(app, AppConfig.MSG_STATE_STOP, "")
                        if (original == null) {
                            original = daemonProcess(app)
                            originalStart = original?.let { processStart(it.pid) }
                        }
                    }
                    if (!stopped) kotlinx.coroutines.delay(100L)
                }
                val victim = original
                val birth = originalStart
                if (!stopped && victim != null && birth != null) {
                    val current = daemonProcess(app)
                    if (current?.pid == victim.pid && processStart(current.pid) == birth) {
                        android.os.Process.killProcess(current.pid)
                        repeat(5) { if (processStart(current.pid) != birth) stopped = true else kotlinx.coroutines.delay(100L) }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Launcher: bounded daemon stop failed", e)
            } finally {
                MessageHelper.sendMsg2UI(app, if (stopped) AppConfig.MSG_STATE_STOP_SUCCESS else AppConfig.MSG_STATE_RUNNING, "")
                val callbacks = synchronized(commandLock) { stopping = false; stopCallbacks.toList().also { stopCallbacks.clear() } }
                android.os.Handler(android.os.Looper.getMainLooper()).post { callbacks.forEach { it() } }
            }
        }
    }

    private fun daemonProcess(context: Context): android.app.ActivityManager.RunningAppProcessInfo? =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.runningAppProcesses?.firstOrNull {
            DaemonIdentity.owned(it.pid, it.uid, it.processName, android.os.Process.myPid(), android.os.Process.myUid(), context.packageName)
        }

    private fun processStart(pid: Int): String? = try {
        java.io.File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ').getOrNull(19)
    } catch (_: java.io.IOException) { null }

    /** Restarts the active daemon without starting a stopped service. */
    fun restartService(context: Context) {
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_RESTART, "")
    }

    /** Restarts the active daemon, or delegates to the caller's permission-aware start flow. */
    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        MessageHelper.sendMsg2ServiceForResult(context, AppConfig.MSG_STATE_RESTART, "") { handled ->
            if (!handled) startIfStopped()
        }
    }

    internal fun hasUsableServer(config: ProfileItem): Boolean =
        config.configType.isComplexType()
            || config.configType == EConfigType.AETHER
            || Utils.isPureIpAddress(config.server.orEmpty())
            || Utils.isValidUrl(config.server)

    @Throws(Exception::class)
    private fun startContextService(context: Context, token: String, mayStart: () -> Boolean) {
        // Note: isRunning check is removed here to avoid loading Native libraries in the UI process.
        // The check is performed in CoreServiceManager when the service starts in the daemon process.

        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!hasUsableServer(config)) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(R.string.toast_allow_insecure_deprecated)
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            if (mayStart()) {
                check(MmkvManager.encodeSettings(AppConfig.CACHE_SERVICE_START_TOKEN, token))
                intent.putExtra(AppConfig.EXTRA_SERVICE_START_TOKEN, token)
                ContextCompat.startForegroundService(context, intent)
            }
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}

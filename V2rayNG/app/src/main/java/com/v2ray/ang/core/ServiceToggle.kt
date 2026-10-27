package com.v2ray.ang.core

import com.v2ray.ang.AppConfig

internal enum class ToggleAction { Start, Stop, Ignore }
internal data class TogglePlan(val action: ToggleAction, val ticket: Long = 0)

/** Unknown daemon state requires a bounded stop; it never authorizes another start. */
internal object ServiceToggle {
    fun decide(state: Int?, starting: Boolean, stopping: Boolean): ToggleAction = when {
        stopping -> ToggleAction.Ignore
        starting -> ToggleAction.Stop
        state == 0 || state == AppConfig.MSG_STATE_NOT_RUNNING -> ToggleAction.Start
        else -> ToggleAction.Stop
    }
}

internal object DaemonIdentity {
    fun owned(pid: Int, uid: Int, name: String, callerPid: Int, callerUid: Int, packageName: String): Boolean =
        pid > 0 && pid != callerPid && uid == callerUid && name == "$packageName:daemon"
}

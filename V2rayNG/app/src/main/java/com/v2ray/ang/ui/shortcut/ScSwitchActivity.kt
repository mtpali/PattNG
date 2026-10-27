package com.v2ray.ang.ui.shortcut

import android.app.Application
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.ToggleAction
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface SwitchState {
    data object Waiting : SwitchState
    data class Permission(val vpn: Boolean) : SwitchState
    data object Finished : SwitchState
}

/** Owns the operation across activity recreation and permission handoff. */
class SwitchViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<SwitchState>(SwitchState.Waiting)
    internal val state = mutableState.asStateFlow()
    private var ticket = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val plan = LauncherManager.planToggle(app)
            ticket = plan.ticket
            when (plan.action) {
                ToggleAction.Start -> mutableState.value = SwitchState.Permission(SettingsManager.isVpnMode() && !SettingsManager.isRootMode())
                ToggleAction.Stop -> LauncherManager.stopService(app) { mutableState.value = SwitchState.Finished }
                ToggleAction.Ignore -> mutableState.value = SwitchState.Finished
            }
        }
    }

    fun start() {
        mutableState.value = SwitchState.Waiting
        viewModelScope.launch(Dispatchers.IO) {
            LauncherManager.startReservedService(getApplication(), ticket)
            mutableState.value = SwitchState.Finished
        }
    }

    fun denied() {
        LauncherManager.cancelStart(ticket)
        mutableState.value = SwitchState.Finished
    }

    override fun onCleared() {
        if (mutableState.value is SwitchState.Permission) LauncherManager.cancelStart(ticket)
        super.onCleared()
    }
}

class ScSwitchActivity : BaseComponentActivity() {
    private val viewModel: SwitchViewModel by viewModels()
    private var permissionPending = false
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionPending = false
        if (it.resultCode == RESULT_OK) viewModel.start() else viewModel.denied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        permissionPending = savedInstanceState?.getBoolean("permissionPending") ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionPending", permissionPending)
        super.onSaveInstanceState(outState)
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(state) {
            when (val step = state) {
                is SwitchState.Permission -> if (!permissionPending) {
                    val request = if (step.vpn) VpnService.prepare(this@ScSwitchActivity) else null
                    if (request == null) viewModel.start() else {
                        permissionPending = true
                        permission.launch(request)
                    }
                }
                SwitchState.Finished -> if (!isFinishing && !isDestroyed) {
                    moveTaskToBack(true)
                    finish()
                }
                SwitchState.Waiting -> Unit
            }
        }
    }
}

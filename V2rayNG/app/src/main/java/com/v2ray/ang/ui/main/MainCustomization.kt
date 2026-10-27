package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

// PullToRefreshBox is experimental in the pinned Material 3 BOM. Its state stays
// inside this presentation boundary; remove the opt-in when the API is stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionRefreshContainer(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    PullToRefreshBox(refreshing, onRefresh, modifier = Modifier.fillMaxSize()) { content() }
}

@Composable
internal fun CustomizationDialogs(state: MainUiState, onAction: (MainAction) -> Unit) {
    val dialog = state.groupDialog ?: return
    key(dialog.groupId, dialog.kind) {
        var text by rememberSaveable { mutableStateOf(if (dialog.kind == GroupDialogKind.Rename) dialog.title else "") }
        val dismiss = { onAction(MainAction.DismissGroupDialog) }
        val input = dialog.kind == GroupDialogKind.Rename || dialog.kind == GroupDialogKind.Profiles
        val title = when (dialog.kind) {
            GroupDialogKind.Menu -> R.string.group_operations
            GroupDialogKind.Rename -> R.string.group_rename
            GroupDialogKind.Delete -> R.string.action_delete
            GroupDialogKind.Link -> R.string.group_link
            GroupDialogKind.Profiles -> R.string.title_rename_configs
            GroupDialogKind.ResetUsage -> R.string.title_reset_profile_usage
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .consumeWindowInsets(WindowInsets.navigationBars).imePadding()) {
                    when (dialog.kind) {
                        GroupDialogKind.Menu -> {
                            Text(dialog.title)
                            listOf(
                                R.string.group_rename to GroupDialogKind.Rename,
                                R.string.title_rename_configs to GroupDialogKind.Profiles,
                                R.string.group_link to GroupDialogKind.Link,
                                R.string.action_delete to GroupDialogKind.Delete,
                            ).forEach { (label, kind) ->
                                TextButton(onClick = { onAction(MainAction.ShowGroupDialog(dialog.groupId, kind)) }) { Text(stringResource(label)) }
                            }
                            TextButton(onClick = { onAction(MainAction.MoveGroup(dialog.groupId, -1)) }) { Text(stringResource(R.string.group_move_left)) }
                            TextButton(onClick = { onAction(MainAction.MoveGroup(dialog.groupId, 1)) }) { Text(stringResource(R.string.group_move_right)) }
                        }
                        GroupDialogKind.Rename, GroupDialogKind.Profiles -> OutlinedTextField(
                            value = text, onValueChange = { text = it }, singleLine = true,
                            label = { Text(stringResource(if (dialog.kind == GroupDialogKind.Profiles) R.string.hint_profile_name_prefix else R.string.group_rename)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GroupDialogKind.Delete -> Text(stringResource(R.string.group_delete_confirm))
                        GroupDialogKind.ResetUsage -> Text(stringResource(R.string.reset_usage_confirm))
                        GroupDialogKind.Link -> {
                            Text(dialog.link.ifBlank { stringResource(R.string.group_missing_link) })
                            if (dialog.link.isNotBlank()) {
                                TextButton(onClick = { onAction(MainAction.CopyGroupLink(dialog.groupId, false)) }) { Text(stringResource(R.string.group_copy_link)) }
                                TextButton(onClick = { onAction(MainAction.CopyGroupLink(dialog.groupId, true)) }) { Text(stringResource(R.string.group_qr)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (input || dialog.kind == GroupDialogKind.Delete || dialog.kind == GroupDialogKind.ResetUsage) {
                    TextButton(enabled = dialog.kind != GroupDialogKind.Rename || text.isNotBlank(), onClick = {
                        onAction(when (dialog.kind) {
                            GroupDialogKind.Rename -> MainAction.RenameGroup(dialog.groupId, text)
                            GroupDialogKind.Profiles -> MainAction.RenameProfiles(dialog.groupId, text)
                            GroupDialogKind.Delete -> MainAction.DeleteGroup(dialog.groupId)
                            else -> MainAction.ResetProfileUsage
                        })
                    }) { Text(stringResource(android.R.string.ok)) }
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

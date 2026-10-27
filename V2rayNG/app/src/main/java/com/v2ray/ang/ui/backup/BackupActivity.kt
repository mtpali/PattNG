package com.v2ray.ang.ui.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseComponentActivity() {

    private val viewModel: BackupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    when (event) {
                        is BackupViewModel.BackupViewModelEvent.ShareFile -> {
                            handleShareFile(event.filePath)
                        }

                        is BackupViewModel.BackupViewModelEvent.ExportLocal -> {
                            handleExportLocal(event.cachePath, event.targetUri)
                        }

                        is BackupViewModel.BackupViewModelEvent.RestoreSuccess -> {
                            withContext(Dispatchers.IO) { SettingsManager.initApp(this@BackupActivity) }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        BackupScreen(
            isLoading = isLoading,
            onBackup = ::backupViaLocal,
            onShareClick = { viewModel.shareBackup(cacheDir, getString(R.string.app_name)) },
            onRestore = ::restoreViaLocal,
            onCleanupProfiles = viewModel::cleanupProfileStorage,
            onBackClick = { finish() }
        )
    }

    private fun handleShareFile(filePath: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".cache",
                            File(filePath)
                        )
                    ),
                getString(R.string.title_configuration_share)
            )
        )
    }

    private fun handleExportLocal(cachePath: String, targetUri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(contentResolver.openOutputStream(targetUri)).use { output ->
                        File(cachePath).inputStream().use { input -> input.copyTo(output) }
                    }
                    File(cachePath).delete()
                }
                toastSuccess(R.string.toast_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Backup: copying the local archive failed", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                viewModel.prepareBackupForUri(cacheDir, getString(R.string.app_name), uri)
            }
        }
    }

    private fun restoreViaLocal() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            lifecycleScope.launch {
                try {
                    val targetFile = withContext(Dispatchers.IO) {
                        val target = File(cacheDir, "${System.nanoTime()}.zip")
                        try {
                            checkNotNull(contentResolver.openInputStream(uri)).use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                            target
                        } catch (error: Exception) {
                            target.delete()
                            throw error
                        }
                    }
                    viewModel.restoreConfiguration(cacheDir, targetFile)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Backup: reading the local restore archive failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }
}

@Composable
fun BackupScreen(
    isLoading: Boolean,
    onBackup: () -> Unit,
    onShareClick: () -> Unit,
    onRestore: () -> Unit,
    onCleanupProfiles: () -> Unit,
    onBackClick: () -> Unit
) {
    var showCleanupDialog by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_configuration_backup_restore),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_backup_24dp),
                title = stringResource(R.string.title_configuration_backup),
                onClick = onBackup
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_share_24dp),
                title = stringResource(R.string.title_configuration_share),
                onClick = onShareClick
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_restore_24dp),
                title = stringResource(R.string.title_configuration_restore),
                onClick = onRestore
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_delete_24dp),
                title = stringResource(R.string.title_profile_storage_cleanup),
                subtitle = stringResource(R.string.summary_profile_storage_cleanup),
                onClick = { showCleanupDialog = true }
            )
            NavigationBarsSpacer()
        }
    }
    if (showCleanupDialog) {
        DeleteConfirmDialog(
            message = stringResource(R.string.message_profile_storage_cleanup),
            onConfirm = onCleanupProfiles,
            onDismiss = { showCleanupDialog = false }
        )
    }
}

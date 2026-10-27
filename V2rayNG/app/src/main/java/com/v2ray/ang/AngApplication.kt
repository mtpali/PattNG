package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AngApplication : Application() {
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MmkvManager.initialize(this)

        AppLocaleManager.initialize(this)
        com.v2ray.ang.root.HotspotSession.resetOnFreshUiProcess(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)
        initializationScope.launch {
            try {
                MmkvManager.clearRetiredPreferences()
            } catch (error: Exception) {
                com.v2ray.ang.util.LogUtil.e(AppConfig.TAG, "Application: retired backup preference migration failed", error)
            }
            MmkvManager.encodeSettings(AppConfig.PREF_IS_BOOTED, false)
            MmkvManager.encodeSettings(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
            MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, "en")
        }

        // PattNG: the Aether cores of this process listen on the port of the settings.
        AetherCoreManager.listenPortSource = SettingsManager::getAetherListenPort

        // Initialize theme state from MMKV
        ThemeManager.refresh()
    }
}

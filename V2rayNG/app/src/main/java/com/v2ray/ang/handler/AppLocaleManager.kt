package com.v2ray.ang.handler

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.v2ray.ang.AppConfig
import java.util.Locale

object AppLocaleManager {
    fun initialize(context: Context) {
        Locale.setDefault(Locale.ENGLISH)
        MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, "en")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales != LocaleList(Locale.ENGLISH)) manager.applicationLocales = LocaleList(Locale.ENGLISH)
        }
    }

    fun onActivityCreated(context: Context) {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != "en") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }

    fun setApplicationLanguage(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
    }

    fun localizedContext(context: Context): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH); setLayoutDirection(Locale.ENGLISH) }
    )
}

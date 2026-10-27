package com.v2ray.ang.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object AppManagerUtil {
    private data class LabelKey(val source: String?, val labelRes: Int, val label: String?, val locale: String)
    private data class CachedLabel(val key: LabelKey, val value: String)
    private val labels = ConcurrentHashMap<String, CachedLabel>()
    private val labelDispatcher = Dispatchers.IO.limitedParallelism(4)
    /**
     * Load the list of network applications.
     *
     * @param context The context to use.
     * @return A list of AppInfo objects representing the network applications.
     */
    suspend fun loadNetworkAppList(context: Context): ArrayList<AppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            // Permissions were not used here. Query lightweight ApplicationInfo records instead.
            val applications = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                packageManager.getInstalledApplications(0)
            }
            labels.keys.retainAll(applications.map { it.packageName }.toSet())
            val locale = Locale.getDefault().toLanguageTag()
            val apps = applications.map { applicationInfo ->
                async(labelDispatcher) {
                    val name = applicationInfo.packageName
                    val key = LabelKey(applicationInfo.sourceDir, applicationInfo.labelRes,
                        applicationInfo.nonLocalizedLabel?.toString(), locale)
                    val cached = labels[name]?.takeIf { it.key == key }
                    val label = cached?.value ?: try {
                        applicationInfo.loadLabel(packageManager).toString().also {
                            labels[name] = CachedLabel(key, it)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        LogUtil.e(AppConfig.TAG, "AppManager: label lookup failed package=$name", error)
                        name
                    }
                    AppInfo(label, name, applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0, 0)
                }
            }.awaitAll()
            ArrayList(apps)
        }

    fun getLastUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

}

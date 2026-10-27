package com.v2ray.ang.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppManagerUtilTest {
    @Test fun reusesLabelsButStillQueriesTheCurrentlyInstalledApplications() = runBlocking<Unit> {
        val manager = mock<PackageManager>()
        val context = mock<Context>()
        val app = mock<ApplicationInfo>().apply {
            packageName = "test.cached.label"
            sourceDir = "/data/app/old/base.apk"
            labelRes = 42
        }
        whenever(context.packageManager).thenReturn(manager)
        whenever(manager.getInstalledApplications(0)).thenReturn(listOf(app))
        whenever(app.loadLabel(manager)).thenReturn("Old name", "New name")
        assertEquals("Old name", AppManagerUtil.loadNetworkAppList(context).single().appName)
        assertEquals("Old name", AppManagerUtil.loadNetworkAppList(context).single().appName)
        verify(app, times(1)).loadLabel(manager)
        app.sourceDir = "/data/app/updated/base.apk"
        assertEquals("New name", AppManagerUtil.loadNetworkAppList(context).single().appName)
        verify(app, times(2)).loadLabel(manager)
        verify(manager, times(3)).getInstalledApplications(0)
    }

    @Test fun anUninstalledPackageDisappearsWithoutKeepingAStaleList() = runBlocking<Unit> {
        val manager = mock<PackageManager>()
        val context = mock<Context>()
        val app = mock<ApplicationInfo>().apply { packageName = "test.removed.app" }
        whenever(context.packageManager).thenReturn(manager)
        whenever(manager.getInstalledApplications(0)).thenReturn(listOf(app), emptyList())
        whenever(app.loadLabel(manager)).thenReturn("App")
        assertEquals(listOf("test.removed.app"), AppManagerUtil.loadNetworkAppList(context).map { it.packageName })
        assertEquals(emptyList<String>(), AppManagerUtil.loadNetworkAppList(context).map { it.packageName })
    }
}

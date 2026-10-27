package com.v2ray.ang.ui.perappproxy

import com.v2ray.ang.dto.AppInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerAppListTest {
    private val apps = listOf(
        AppInfo("System Z", "system.z", true, 0),
        AppInfo("Browser", "user.browser", false, 0),
        AppInfo("Selected system", "selected.system", true, 0),
        AppInfo("Calendar", "user.calendar", false, 0),
    )

    @Test fun searchesNamesAndPackagesWithoutMutatingSelectionOrInput() {
        assertEquals(apps, filterPerAppApps(apps, "  "))
        assertEquals(listOf(apps[1]), filterPerAppApps(apps, " BROWSER "))
        assertEquals(listOf(apps[3]), filterPerAppApps(apps, "user.calendar"))
        assertEquals(emptyList<AppInfo>(), filterPerAppApps(apps, "not-installed"))
        assertEquals(emptyList<AppInfo>(), filterPerAppApps(emptyList(), "Browser"))
        assertEquals("system.z", apps.first().packageName)
    }

    @Test fun chosenPackagesComeFirstThenUserAppsThenSystemApps() {
        assertEquals(listOf("selected.system", "user.browser", "user.calendar", "system.z"),
            sortPerAppApps(apps, setOf("selected.system")).map { it.packageName })
    }
}

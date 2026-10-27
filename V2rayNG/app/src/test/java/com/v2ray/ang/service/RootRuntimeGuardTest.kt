package com.v2ray.ang.service

import com.v2ray.ang.root.RootRuntimeGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class RootRuntimeGuardTest {
    private fun scenario(captureDevice: Boolean, staleToken: Boolean = false, block: (java.io.File, Process) -> Unit) {
        val dir = Files.createTempDirectory("hotspot-guard").toFile()
        var guard: Process? = null
        try {
            val proc = java.io.File(dir, "proc")
            listOf(1234 to "core-born", 5678 to "ui-born").forEach { (pid, birth) ->
                java.io.File(proc, "$pid/stat").apply { parentFile.mkdirs() }
                    .writeText((List(21) { "0" } + birth).joinToString(" "))
            }
            java.io.File(dir, "runtime.token").writeText(if (staleToken) "new-session" else "session")
            java.io.File(dir, "hotspot.owner").writeText("5678 ui-born")
            java.io.File(dir, "guard_cleanup.sh").writeText("echo full >> '${dir.path}/events'; rm -f '${dir.path}/runtime.token'\n")
            java.io.File(dir, "guard_lan_cleanup.sh").writeText("echo lan >> '${dir.path}/events'\n")
            // All process reads, oom writes and simulated deaths stay in this temporary tree.
            val script = RootRuntimeGuard.script(1234, "core-born", dir.path, "session", captureDevice)
                .replace("/proc/", "${proc.path}/")
            guard = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
            block(dir, guard)
        } finally {
            guard?.destroyForcibly()?.waitFor(2, TimeUnit.SECONDS)
            dir.deleteRecursively()
        }
    }

    private fun awaitEvents(dir: java.io.File, expected: List<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val file = java.io.File(dir, "events")
        while (System.nanoTime() < deadline) {
            if (file.exists() && file.readLines() == expected) return
            Thread.sleep(20)
        }
        assertEquals(expected, if (file.exists()) file.readLines() else emptyList<String>())
    }

    @Test fun endingTheUiSessionPreservesRootCaptureUntilTheCoreEnds() = scenario(true) { dir, process ->
        java.io.File(dir, "proc/5678/stat").delete()
        awaitEvents(dir, listOf("lan"))
        assertTrue(process.isAlive)
        java.io.File(dir, "proc/1234/stat").delete()
        awaitEvents(dir, listOf("lan", "full"))
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
    }

    @Test fun endingTheUiSessionStopsTheDedicatedLanHelper() = scenario(false) { dir, process ->
        java.io.File(dir, "proc/5678/stat").delete()
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        awaitEvents(dir, listOf("lan", "full"))
    }

    @Test fun coreDeathCleansTheRuntimeAndAnOldGuardCannotTouchANewSession() {
        scenario(true) { dir, process ->
            java.io.File(dir, "proc/1234/stat").delete()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            awaitEvents(dir, listOf("full"))
        }
        scenario(false, staleToken = true) { dir, process ->
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            assertTrue(!java.io.File(dir, "events").exists())
        }
    }
}

package com.arthou.ntranslator.client.transcribers.browser.bridge

import com.arthou.ntranslator.NTranslator
import net.minecraft.client.Minecraft
import net.minecraft.Util
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

object BrowserBridgeLauncher {
    private const val RESOURCE_PATH = "/native/windows/nexel-browser-bridge.bin"
    private val processRef = AtomicReference<Process?>()

    fun open(url: String, browser: String): Boolean {
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            return false
        }

        return try {
            val previous = processRef.getAndSet(null)
            stopProcessTree(previous)

            val executable = extractExecutable()
            val parentPid = ProcessHandle.current().pid().toString()

            val process = ProcessBuilder(
                executable.absolutePath,
                "--url", url,
                "--browser", browser,
                "--parent-pid", parentPid
            )
                .directory(executable.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            processRef.set(process)
            true
        } catch (error: Throwable) {
            NTranslator.logger.warn("Failed to start NEXEL hidden browser bridge.", error)
            false
        }
    }

    fun restart(url: String, browser: String) {
        stop()
        open(url, browser)
    }

    fun stop() {
        val process = processRef.getAndSet(null)
        stopProcessTree(process)

        val targetDir = File(Minecraft.getInstance().gameDirectory, ".nexel/browser-bridge")
        killExtractedBridgeProcesses(targetDir)
    }

    private fun stopProcessTree(process: Process?) {
        if (process == null) {
            return
        }

        runCatching {
            process.toHandle()
                .descendants()
                .sorted(Comparator.comparingLong<ProcessHandle> { it.pid() }.reversed())
                .forEach { child ->
                    child.destroy()
                    child.onExit().get(500, TimeUnit.MILLISECONDS)
                }
        }

        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(2500, TimeUnit.MILLISECONDS) && process.isAlive) {
                process.destroyForcibly()
                process.waitFor(1000, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun extractExecutable(): File {
        val targetDir = File(Minecraft.getInstance().gameDirectory, ".nexel/browser-bridge")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        killExtractedBridgeProcesses(targetDir)

        val target = File(targetDir, "nexel-browser-bridge.exe")
        val sidecar = findSidecarExecutable()
        if (sidecar != null) {
            Files.copy(sidecar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target.setExecutable(true)
            return target
        }

        val resource = BrowserBridgeLauncher::class.java.getResourceAsStream(RESOURCE_PATH)
        if (resource != null) {
            resource.use { extractZip(it, targetDir) }
            target.setExecutable(true)
            return target
        }

        throw IllegalStateException("Missing NEXEL browser bridge archive.")
    }

    private fun killExtractedBridgeProcesses(targetDir: File) {
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            return
        }

        runCatching {
            val root = targetDir.canonicalPath.replace("'", "''")
            val script = """
                ${'$'}ErrorActionPreference='SilentlyContinue'
                ${'$'}root='$root'
                ${'$'}all=@(Get-CimInstance Win32_Process)
                ${'$'}roots=@(${ '$' }all | Where-Object {
                  (${'$'}_.ExecutablePath -and ${'$'}_.ExecutablePath.StartsWith(${'$'}root, [System.StringComparison]::OrdinalIgnoreCase)) -or
                  (${'$'}_.CommandLine -and (
                    ${'$'}_.CommandLine.IndexOf('nexel-browser-bridge', [System.StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                    ${'$'}_.CommandLine.IndexOf(${'$'}root, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
                  ))
                })
                ${'$'}ids=New-Object 'System.Collections.Generic.HashSet[int]'
                function Add-Tree([int]${'$'}pid) {
                  if (-not ${'$'}ids.Add(${'$'}pid)) { return }
                  foreach (${'$'}child in ${'$'}all | Where-Object { ${'$'}_.ParentProcessId -eq ${'$'}pid }) {
                    Add-Tree ([int]${'$'}child.ProcessId)
                  }
                }
                foreach (${'$'}rootProcess in ${'$'}roots) { Add-Tree ([int]${'$'}rootProcess.ProcessId) }
                ${'$'}ids | Sort-Object -Descending | ForEach-Object {
                  Stop-Process -Id ${'$'}_ -Force -ErrorAction SilentlyContinue
                }
            """.trimIndent()

            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden",
                "-Command", script
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(3500, TimeUnit.MILLISECONDS)
        }.onFailure {
            NTranslator.logger.debug("Could not pre-clean existing NEXEL browser bridge process.", it)
        }
    }

    private fun extractZip(input: java.io.InputStream, targetDir: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(targetDir, entry.name).canonicalFile
                val root = targetDir.canonicalFile

                if (!target.path.startsWith(root.path + File.separator) && target != root) {
                    throw IllegalStateException("Unsafe zip entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    Files.copy(zip, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                zip.closeEntry()
            }
        }
    }

    private fun findSidecarExecutable(): File? {
        val codeSource = BrowserBridgeLauncher::class.java.protectionDomain.codeSource?.location ?: return null
        val sourceFile = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return null
        val folder = if (sourceFile.isFile) sourceFile.parentFile else sourceFile

        return listOf(
            File(folder, "nexel-browser-bridge.exe"),
            File(folder, "native/windows/nexel-browser-bridge.exe")
        ).firstOrNull { it.isFile }
    }
}

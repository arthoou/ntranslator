package com.arthou.ntranslator.client.tts

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

object LocalTtsSpeaker {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NEXEL-Local-TTS").apply { isDaemon = true }
    }
    private val currentProcess = AtomicReference<Process?>()

    fun speak(text: String, language: Language) {
        if (text.isBlank()) {
            return
        }

        val player = Minecraft.getInstance().player
        player?.displayClientMessage(message("ntranslator.tts.reading"), true)

        executor.execute {
            stop()

            if (!System.getProperty("os.name").lowercase().contains("windows")) {
                return@execute
            }

            val command = buildEncodedCommand(text, language, preferredAudioDevice())
            runCatching {
                ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .also { currentProcess.set(it) }
                    .waitFor()
            }.onFailure {
                NTranslator.logger.warn("Failed to run local TTS.")
                it.printStackTrace()
            }.also {
                currentProcess.set(null)
            }
        }
    }

    fun stop() {
        currentProcess.getAndSet(null)?.runCatching {
            destroyForcibly()
        }
    }

    private fun message(key: String): Component {
        return runCatching {
            Component::class.java.getMethod("translatable", String::class.java).invoke(null, key) as Component
        }.getOrElse {
            Class.forName("net.minecraft.network.chat.TranslatableComponent")
                .getConstructor(String::class.java)
                .newInstance(key) as Component
        }
    }
    private fun buildEncodedCommand(text: String, language: Language, preferredDevice: String?): String {
        val encodedText = Base64.getEncoder().encodeToString(normalizeText(text).toByteArray(StandardCharsets.UTF_8))
        val encodedDevice = Base64.getEncoder().encodeToString((preferredDevice ?: "").toByteArray(StandardCharsets.UTF_8))
        val locale = localeFor(language)
        val script = """
${'$'}ErrorActionPreference = 'Stop'
${'$'}text = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String('$encodedText'))
${'$'}preferredDevice = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String('$encodedDevice'))
${'$'}locale = '$locale'

function Normalize-DeviceName([string]${'$'}value) {
    if ([string]::IsNullOrWhiteSpace(${'$'}value)) { return '' }
    # O parenteses fica: e nele que mora a diferenca entre "Alto-falantes
    # (Senary Audio)" e "Alto-falantes (2- VB-Audio Virtual Cable)". O prefixo
    # "OpenAL Soft on" so existe do lado do Minecraft e nao ajuda a casar.
    ${'$'}v = ${'$'}value.ToLowerInvariant() -replace '^openal soft on ', ''
    return (${'$'}v -replace '[^\p{L}\p{Nd}]+', ' ').Trim()
}

function Select-SapiVoice([object]${'$'}speaker, [string]${'$'}cultureName) {
    try {
        ${'$'}culture = [System.Globalization.CultureInfo]::GetCultureInfo(${'$'}cultureName)
        ${'$'}lcid = ('{0:x}' -f ${'$'}culture.LCID).ToLowerInvariant()
        foreach (${'$'}voiceToken in @(${'$'}speaker.GetVoices())) {
            try {
                ${'$'}languages = @(${'$'}voiceToken.GetAttribute('Language').Split(';') | ForEach-Object { ${'$'}_.Trim().ToLowerInvariant() })
                if (${'$'}languages -contains ${'$'}lcid) {
                    ${'$'}speaker.Voice = ${'$'}voiceToken
                    return
                }
            } catch {}
        }
    } catch {}
}

function Select-SapiOutput([object]${'$'}speaker, [string]${'$'}minecraftDevice) {
    if ([string]::IsNullOrWhiteSpace(${'$'}minecraftDevice)) { return }
    ${'$'}wanted = Normalize-DeviceName ${'$'}minecraftDevice
    if ([string]::IsNullOrWhiteSpace(${'$'}wanted) -or ${'$'}wanted -match '^(default|system default|padrao|padrão)$') { return }

    ${'$'}bestOutput = ${'$'}null
    ${'$'}bestScore = 0
    foreach (${'$'}output in @(${'$'}speaker.GetAudioOutputs())) {
        try {
            ${'$'}candidate = Normalize-DeviceName ${'$'}output.GetDescription()
            if ([string]::IsNullOrWhiteSpace(${'$'}candidate)) { continue }

            ${'$'}score = 0
            if (${'$'}candidate -eq ${'$'}wanted) {
                ${'$'}score = 1000
            } elseif (${'$'}candidate.Contains(${'$'}wanted) -or ${'$'}wanted.Contains(${'$'}candidate)) {
                ${'$'}score = 900
            } else {
                foreach (${'$'}part in ${'$'}wanted.Split(' ')) {
                    if (${'$'}part.Length -ge 3 -and ${'$'}candidate.Contains(${'$'}part)) {
                        ${'$'}score += 1
                    }
                }
            }

            if (${'$'}score -gt ${'$'}bestScore) {
                ${'$'}bestScore = ${'$'}score
                ${'$'}bestOutput = ${'$'}output
            }
        } catch {}
    }

    if (${'$'}bestOutput -ne ${'$'}null -and ${'$'}bestScore -gt 0) {
        ${'$'}speaker.AudioOutput = ${'$'}bestOutput
    }
}

try {
    ${'$'}speaker = New-Object -ComObject SAPI.SpVoice
    Select-SapiVoice ${'$'}speaker ${'$'}locale
    Select-SapiOutput ${'$'}speaker ${'$'}preferredDevice
    ${'$'}speaker.Rate = 0
    [void]${'$'}speaker.Speak(${'$'}text, 0)
    [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject(${'$'}speaker)
} catch {
    Add-Type -AssemblyName System.Speech
    ${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try { ${'$'}synth.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female, [System.Speech.Synthesis.VoiceAge]::Adult, 0, [System.Globalization.CultureInfo]::GetCultureInfo(${'$'}locale)) } catch {}
    ${'$'}synth.Rate = 0
    ${'$'}synth.Speak(${'$'}text)
    ${'$'}synth.Dispose()
}
""".trimIndent()

        return Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
    }

    private fun normalizeText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun localeFor(language: Language): String {
        return when (language.name) {
            "PORTUGUESE" -> "pt-BR"
            "PORTUGUESE_PORTUGAL" -> "pt-PT"
            "SPANISH" -> "es-ES"
            "FRENCH" -> "fr-FR"
            "GERMAN" -> "de-DE"
            "ITALIAN" -> "it-IT"
            "POLISH" -> "pl-PL"
            "RUSSIAN" -> "ru-RU"
            "JAPANESE" -> "ja-JP"
            "KOREAN" -> "ko-KR"
            "CHINESE" -> "zh-CN"
            "CHINESE_TRADITIONAL" -> "zh-TW"
            else -> "en-US"
        }
    }

    private fun preferredAudioDevice(): String? {
        System.getProperty("nexel.ttsDevice")?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        System.getenv("NEXEL_TTS_DEVICE")?.trim()?.takeIf(String::isNotBlank)?.let { return it }

        val optionsFile = File(Minecraft.getInstance().gameDirectory, "options.txt")
        if (!optionsFile.isFile) {
            return null
        }

        return runCatching {
            optionsFile.useLines { lines ->
                lines.firstOrNull { it.startsWith("soundDevice:") }
                    ?.substringAfter("soundDevice:")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

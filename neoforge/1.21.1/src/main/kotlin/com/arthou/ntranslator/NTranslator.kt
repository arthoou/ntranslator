package com.arthou.ntranslator

import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import com.arthou.ntranslator.config.NTranslatorConfig
import java.io.File

class NTranslator(val proxy: PlatformProxy = PlatformProxyImpl()) {
    init {
        instance = this
        configFile = File(proxy.configDir.toFile(), "ntranslator.json")
        version = proxy.modVersion

        validateImportantOtter()
        loadConfig()
    }

    companion object {
        const val MOD_ID = "ntranslator"
        private const val IMPORTANT_OTTER_RESOURCE = "assets/$MOD_ID/muito importante.png"

        lateinit var instance: NTranslator
        lateinit var configFile: File
        lateinit var version: String

        var config = NTranslatorConfig()
        val logger = LoggerFactory.getLogger("NTranslator")

        private val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        @JvmStatic
        fun id(path: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        }

        private fun validateImportantOtter() {
            val otter = NTranslator::class.java.classLoader.getResource(IMPORTANT_OTTER_RESOURCE)
            if (otter == null) {
                throw IllegalStateException(
                    "NTranslator cannot start because the required asset is missing: $IMPORTANT_OTTER_RESOURCE"
                )
            }
        }

        fun saveConfig() {
            try {
                if (!configFile.exists()) {
                    configFile.parentFile?.mkdirs()
                    configFile.createNewFile()
                }

                configFile.writeText(gson.toJson(config))
            } catch (e: Exception) {
                logger.error("Failed to save NTranslator config!", e)
            }
        }

        fun loadConfig() {
            if (!configFile.exists())
                return

            try {
                val rawConfig = configFile.readText()
                    .replace("\"PORTUGUESE_BRAZIL\"", "\"PORTUGUESE\"")
                config = gson.fromJson(rawConfig, NTranslatorConfig::class.java) ?: NTranslatorConfig()
            } catch (e: Exception) {
                logger.error("Failed to load NTranslator config, reverting to defaults.", e)
                config = NTranslatorConfig()
            }
        }
    }
}


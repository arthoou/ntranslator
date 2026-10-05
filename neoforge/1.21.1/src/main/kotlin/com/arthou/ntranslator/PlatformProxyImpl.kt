package com.arthou.ntranslator

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

class PlatformProxyImpl : PlatformProxy {
    companion object {
        private val BETA_VERSION_REGEX = Regex("""(\d+)\.(\d+)(?:\.(\d+))?-beta""")
    }

    override val isDev: Boolean
        get() = !FMLLoader.isProduction()

    override val gameDir: Path
        get() = FMLPaths.GAMEDIR.get()

    override val configDir: Path
        get() = FMLPaths.CONFIGDIR.get()

    override val modVersion: String
        get() = formatDisplayVersion(ModList.get().getModFileById(NTranslator.MOD_ID).versionString())

    override fun isModLoaded(id: String): Boolean {
        return ModList.get().isLoaded(id)
    }

    override fun isClient(): Boolean {
        return FMLLoader.getDist() == Dist.CLIENT
    }

    private fun formatDisplayVersion(raw: String): String {
        val match = BETA_VERSION_REGEX.matchEntire(raw) ?: return raw
        val major = match.groupValues[1]
        val minor = match.groupValues[2]
        val patch = match.groupValues.getOrElse(3) { "" }

        return buildString {
            append("beta ")
            append(major)
            append('.')
            append(minor)

            if (patch.isNotBlank() && patch != "0") {
                append('.')
                append(patch)
            }
        }
    }
}

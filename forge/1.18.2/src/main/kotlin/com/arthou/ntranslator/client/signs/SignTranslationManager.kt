package com.arthou.ntranslator.client.signs

import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.translation.ClientTranslationHelper
import com.arthou.ntranslator.mixin.SignBlockEntityAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.world.level.block.entity.SignBlockEntity

object SignTranslationManager {
    private var pendingScreenPos: BlockPos? = null
    private var suppressEditScreenPos: BlockPos? = null
    private var suppressEditScreenUntil = 0L
    private var allowVanillaUntil = 0L
    /** Tela traduzida ja montada, para reexibir sem piscar nem retraduzir. */
    private var readyScreen: Screen? = null
    private const val EDIT_SCREEN_SUPPRESS_MS = 2500L
    private const val VANILLA_EDIT_WINDOW_MS = 2000L

    fun openTranslatedSign(pos: BlockPos) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val entity = level.getBlockEntity(pos) as? SignBlockEntity ?: return

        val lines = readLines(entity)
        if (lines.all { it.isBlank() }) {
            return
        }

        readyScreen = null
        pendingScreenPos = pos.immutable()
        suppressEditScreenPos = pos.immutable()
        suppressEditScreenUntil = System.currentTimeMillis() + EDIT_SCREEN_SUPPRESS_MS
        showLoadingScreen()

        val targetLanguage = NTranslator.config.client.subtitleLanguage

        ClientTranslationHelper.translateLinesAsync(lines, targetLanguage).whenComplete { result, error ->
            mc.execute {
                // A janela de supressao precisa continuar aberta: o servidor manda o
                // pacote de abrir a edicao da placa depois, e a traducao agora termina
                // antes disso. Zerar aqui fazia a tela piscar e cair na edicao do jogo.
                pendingScreenPos = null

                // Fechar a tela ao falhar deixava o jogador sem GUI nenhuma. Melhor
                // mostrar o texto original com um aviso de que a traducao nao veio.
                if (error != null || result == null || !result.complete) {
                    NTranslator.logger.error("NTranslator: falha ao traduzir a placa.", error)
                    showTranslated(
                        TranslatedSignScreen(
                            TranslatableComponent("ntranslator.sign.title"),
                            result?.lines ?: lines,
                            TranslatableComponent("ntranslator.sign.failed"),
                            targetLanguage,
                            lines
                        )
                    )
                    return@execute
                }
                showTranslated(
                    TranslatedSignScreen(
                        TranslatableComponent("ntranslator.sign.title"),
                        result.lines,
                        TextComponent("${result.sourceLanguage.displayCode} -> ${targetLanguage.displayCode}"),
                        targetLanguage,
                        lines
                    )
                )
            }
        }
    }

    private fun readLines(entity: SignBlockEntity): List<String> {
        val accessor = entity as SignBlockEntityAccessor
        return accessor.`ntranslator$getMessages`().map { it?.string?.trim().orEmpty() }
    }

    /**
     * O jogador pediu a edicao normal (agachado ou com uma placa na mao). Sem esta
     * janela o `suppressEditScreenUntil` de uma traducao anterior sequestrava a tela.
     */
    fun allowVanillaEdit() {
        readyScreen = null
        pendingScreenPos = null
        suppressEditScreenPos = null
        suppressEditScreenUntil = 0L
        allowVanillaUntil = System.currentTimeMillis() + VANILLA_EDIT_WINDOW_MS
    }

    fun shouldReplaceSignScreen(screen: Screen): Boolean {
        if (screen !is SignEditScreen) {
            return false
        }

        // Agachado + clique abre a edicao normal da placa, sem traduzir.
        if (System.currentTimeMillis() < allowVanillaUntil || Minecraft.getInstance().player?.isShiftKeyDown == true) {
            readyScreen = null
            pendingScreenPos = null
            suppressEditScreenPos = null
            suppressEditScreenUntil = 0L
            return false
        }

        return pendingScreenPos != null || System.currentTimeMillis() < suppressEditScreenUntil
    }

    fun reopenPendingTranslation() {
        val ready = readyScreen
        if (ready != null) {
            Minecraft.getInstance().execute { Minecraft.getInstance().setScreen(ready) }
            return
        }

        val pos = pendingScreenPos ?: suppressEditScreenPos ?: return
        Minecraft.getInstance().execute { openTranslatedSign(pos) }
    }

    /** Tela pronta para substituir a edicao do jogo antes dela abrir, ou null. */
    fun readyTranslatedScreen(): Screen? = readyScreen

    private fun showTranslated(screen: Screen) {
        readyScreen = screen
        Minecraft.getInstance().setScreen(screen)
    }

    private fun showLoadingScreen() {
        Minecraft.getInstance().setScreen(
            TranslatedSignScreen(
                TranslatableComponent("ntranslator.sign.loading"),
                listOf("..."),
                TranslatableComponent("ntranslator.sign.loading.subtitle")
            )
        )
    }
}

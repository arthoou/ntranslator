package com.arthou.ntranslator.client.books

import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.translation.ClientTranslationHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.BookViewScreen
import net.minecraft.network.chat.Component

object WritableBookTranslationPreview {
    fun open(pages: List<String>, returnScreen: Screen) {
        val mc = Minecraft.getInstance()
        val loadingPages = List(pages.size.coerceAtLeast(1)) { Component.literal("...") }
        mc.setScreen(previewScreen(BookViewScreen.BookAccess(loadingPages), returnScreen))

        ClientTranslationHelper.translateLinesAsync(pages, NTranslator.config.client.subtitleLanguage).whenComplete { result, error ->
            val translated = if (error != null || result == null) {
                pages.map { Component.literal(it) }
            } else {
                result.lines.map { Component.literal(it) }
            }

            mc.execute {
                if (mc.screen is BookViewScreen) {
                    mc.setScreen(previewScreen(BookViewScreen.BookAccess(translated), returnScreen))
                }
            }
        }
    }

    private fun previewScreen(access: BookViewScreen.BookAccess, returnScreen: Screen): BookViewScreen {
        return object : BookViewScreen(access) {
            override fun closeScreen() {
                Minecraft.getInstance().setScreen(returnScreen)
            }

            override fun onClose() {
                Minecraft.getInstance().setScreen(returnScreen)
            }
        }
    }
}

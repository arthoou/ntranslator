package com.arthou.ntranslator.client.gui

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import java.util.Locale

/**
 * Tela de selecao de idioma usada pelo livro e pela placa.
 * O idioma escolhido e compartilhado entre as duas GUIs.
 */
class LanguageSelectScreen(private val parent: Screen?) :
    Screen(Component.translatable("gui.ntranslator.language_select.subtitles.title")) {

    private lateinit var list: LanguageSelectionList
    private lateinit var searchBox: EditBox
    private var appliedSelection = false

    override fun onClose() {
        if (!appliedSelection) {
            selectionCallback = null
        }

        Minecraft.getInstance().setScreen(parent)
    }

    override fun init() {
        super.init()

        searchBox = EditBox(
            font,
            panelLeft() + 32,
            panelTop() + 48,
            panelWidth() - 64,
            20,
            Component.translatable("gui.ntranslator.language_select.search")
        ).apply {
            setResponder { refreshEntries(it) }
            setHint(Component.translatable("gui.ntranslator.language_select.search"))
            setTextColor(0xFFFFFF)
            setTextColorUneditable(0xFFFFFF)
            setBordered(true)
        }

        list = LanguageSelectionList()
        list.setLeftPos(listLeft())

        addRenderableWidget(searchBox)
        addRenderableWidget(list)
        addRenderableWidget(
            nexelConfigButtonBuilder(CommonComponents.GUI_CANCEL) {
                onClose()
            }
                .bounds(panelLeft() + 26, panelTop() + panelHeight() - 28, 120, 20)
                .build()
        )
        addRenderableWidget(
            nexelConfigButtonBuilder(CommonComponents.GUI_DONE) {
                onDone()
            }
                .bounds(panelLeft() + panelWidth() - 146, panelTop() + panelHeight() - 28, 120, 20)
                .build()
        )

        refreshEntries("")
        setFocused(searchBox)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x78101822)
        guiGraphics.fill(panelLeft() - 4, panelTop() - 4, panelLeft() + panelWidth() + 4, panelTop() + panelHeight() + 4, 0x66242F3F)
        guiGraphics.fill(panelLeft(), panelTop(), panelLeft() + panelWidth(), panelTop() + panelHeight(), 0xE0202B38.toInt())
        guiGraphics.fill(panelLeft(), panelTop(), panelLeft() + panelWidth(), panelTop() + 28, 0xFF31445C.toInt())
        guiGraphics.fill(listLeft(), listTop() - 6, listRight(), listBottom() + 4, 0x7A323E4E)

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(font, title, width / 2, panelTop() + 10, 0xFFFFFF)
        if (list.children().isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.ntranslator.language_select.empty"),
                width / 2,
                panelTop() + (panelHeight() / 2),
                0xC5D4EA
            )
        }

        NTranslatorCredits.render(guiGraphics)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (NTranslatorCredits.handleClick(mouseX, mouseY)) {
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun onDone() {
        val selectedLanguage = list.selected?.language ?: run {
            onClose()
            return
        }

        NTranslator.config.client.subtitleLanguage = selectedLanguage
        NTranslator.saveConfig()
        appliedSelection = true
        val callback = selectionCallback
        selectionCallback = null

        onClose()
        callback?.invoke(selectedLanguage)
    }

    companion object {
        private var selectionCallback: ((Language) -> Unit)? = null

        @JvmStatic
        fun openSubtitles(parent: Screen?, callback: ((Language) -> Unit)? = null): LanguageSelectScreen {
            selectionCallback = callback
            return LanguageSelectScreen(parent)
        }
    }

    private fun refreshEntries(query: String) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val previousSelection = list.selected?.language ?: NTranslator.config.client.subtitleLanguage
        val filteredLanguages = Language.entries
            .filter { language ->
                normalized.isBlank() ||
                    language.text.string.lowercase(Locale.ROOT).contains(normalized) ||
                    language.code.lowercase(Locale.ROOT).contains(normalized)
            }
            .sortedBy { it.text.string.lowercase(Locale.ROOT) }

        list.setLanguages(filteredLanguages, previousSelection)
    }

    private fun panelWidth(): Int = minOf(620, width - 40)

    private fun panelHeight(): Int = minOf(390, height - 30)

    private fun panelLeft(): Int = (width - panelWidth()) / 2

    private fun panelTop(): Int = (height - panelHeight()) / 2

    private fun listLeft(): Int = panelLeft() + 26

    private fun listRight(): Int = panelLeft() + panelWidth() - 26

    private fun listTop(): Int = panelTop() + 84

    private fun listBottom(): Int = panelTop() + panelHeight() - 46

    private inner class LanguageSelectionList : ObjectSelectionList<LanguageSelectionList.Entry>(
        Minecraft.getInstance(),
        this@LanguageSelectScreen.listRight() - this@LanguageSelectScreen.listLeft(),
        this@LanguageSelectScreen.listBottom() - this@LanguageSelectScreen.listTop(),
        this@LanguageSelectScreen.listTop(),
        this@LanguageSelectScreen.listBottom(),
        22
    ) {
        init {
            setRenderBackground(false)
            setRenderTopAndBottom(false)
        }

        fun setLanguages(languages: List<Language>, selectedLanguage: Language?) {
            clearEntries()
            selected = null

            for (language in languages) {
                val entry = Entry(language)
                addEntry(entry)

                if (language == selectedLanguage) {
                    selected = entry
                }
            }

            if (selected == null) {
                selected = children().firstOrNull()
            }
        }

        override fun getRowWidth(): Int {
            return this@LanguageSelectScreen.listRight() - this@LanguageSelectScreen.listLeft() - 12
        }

        override fun getRowLeft(): Int {
            return this@LanguageSelectScreen.listLeft() + 6
        }

        override fun getScrollbarPosition(): Int {
            return this@LanguageSelectScreen.listRight() - 8
        }

        inner class Entry(val language: Language) : ObjectSelectionList.Entry<Entry>() {
            private var lastClickTime: Long = 0L

            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                top: Int,
                left: Int,
                width: Int,
                height: Int,
                mouseX: Int,
                mouseY: Int,
                hovering: Boolean,
                partialTick: Float
            ) {
                val rowLeft = left - 6
                val rowRight = left + width + 6

                if (this@LanguageSelectionList.selected == this) {
                    guiGraphics.fill(rowLeft, top, rowRight, top + height, 0x443F5B7A)
                    guiGraphics.fill(rowLeft, top, rowLeft + 3, top + height, 0xFF7BD4FF.toInt())
                } else if (hovering) {
                    guiGraphics.fill(rowLeft, top, rowRight, top + height, 0x2238455A)
                }

                guiGraphics.drawString(font, language.text, left + 8, top + 7, 0xF5F9FF, false)
                val codeText = language.displayCode
                guiGraphics.drawString(font, codeText, rowRight - font.width(codeText) - 8, top + 7, 0x88C8FF, false)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (button == 0) {
                    this@LanguageSelectionList.selected = this

                    if (Util.getMillis() - lastClickTime < 250L) {
                        this@LanguageSelectScreen.onDone()
                    }

                    lastClickTime = Util.getMillis()
                    return true
                }

                lastClickTime = Util.getMillis()
                return false
            }

            override fun getNarration(): Component {
                return Component.translatable("narrator.select", language.text)
            }
        }
    }
}

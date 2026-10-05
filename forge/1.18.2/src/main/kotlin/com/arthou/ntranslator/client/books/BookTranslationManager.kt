package com.arthou.ntranslator.client.books

import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.Language
import com.arthou.ntranslator.client.gui.LanguageSelectScreen
import com.arthou.ntranslator.client.translation.ClientTranslationHelper
import com.arthou.ntranslator.client.tts.LocalTtsSpeaker
import com.arthou.ntranslator.mixin.BookEditScreenAccessor
import com.arthou.ntranslator.mixin.BookViewScreenAccessor
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.BookEditScreen
import net.minecraft.client.gui.screens.inventory.BookViewScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.ResourceLocation
import java.util.Collections
import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object BookTranslationManager {
    private val SPEAKER_ICON: ResourceLocation = NTranslator.id("textures/gui/speaker.png")
    private const val BUTTON_SIZE = 20
    private const val BUTTON_GAP = 2
    private const val BUTTON_X_OFFSET = 78
    private const val DEFAULT_BUTTON_Y = 8
    private const val DONE_TO_BOOK_TOP = 200
    private var ttsActive = false
    private val states = Collections.synchronizedMap(WeakHashMap<BookViewScreen, State>())
    private val writableStates = Collections.synchronizedMap(WeakHashMap<BookEditScreen, WritableState>())

    fun installButton(screen: BookViewScreen, addButton: Consumer<AbstractWidget>) {
        val state = states.computeIfAbsent(screen) {
            val accessor = screen as BookViewScreenAccessor
            State(accessor.nexelGetBookAccess())
        }

        val layout = buttonLayout(screen)
        val button = TranslateBookButton(layout.x, layout.y, state.translatedMode) {
            toggle(screen)
        }

        val ttsButton = IconBookButton(
            layout.x,
            layout.y + BUTTON_SIZE + BUTTON_GAP,
            SPEAKER_ICON
        ) {
            speakCurrentPage(screen)
        }

        val languageButton = TextBookButton(
            layout.x,
            layout.y + (BUTTON_SIZE + BUTTON_GAP) * 2,
            "艾"
        ) {
            Minecraft.getInstance().setScreen(LanguageSelectScreen.openSubtitles(screen) {
                refreshReadableTranslation(screen)
            })
        }

        state.button = button
        state.ttsButton = ttsButton
        state.languageButton = languageButton
        addButton.accept(button)
        addButton.accept(ttsButton)
        addButton.accept(languageButton)

        // A tela e re-inicializada ao voltar do seletor de idioma (ou ao redimensionar).
        // Sem isto, os botoes somem e a traducao aberta se perde.
        if (state.translatedMode && state.translatedPages != null) {
            val viewAccessor = screen as BookViewScreenAccessor
            viewAccessor.nexelSetBookAccess(bookAccessFromPages(state.translatedPages!!.toList()))
            viewAccessor.nexelSetPage(viewAccessor.nexelGetCurrentPage())
        }
    }

    fun installWritableButton(screen: BookEditScreen, addButton: Consumer<AbstractWidget>) {
        val state = writableStates.computeIfAbsent(screen) { WritableState() }
        val layout = buttonLayout(screen)
        val button = TranslateBookButton(layout.x, layout.y, state.translatedMode) {
            toggleWritable(screen)
        }

        val ttsButton = IconBookButton(
            layout.x,
            layout.y + BUTTON_SIZE + BUTTON_GAP,
            SPEAKER_ICON
        ) {
            speakCurrentWritablePage(screen)
        }

        val languageButton = TextBookButton(
            layout.x,
            layout.y + (BUTTON_SIZE + BUTTON_GAP) * 2,
            "艾"
        ) {
            Minecraft.getInstance().setScreen(LanguageSelectScreen.openSubtitles(screen) {
                refreshWritableTranslation(screen)
            })
        }

        state.button = button
        state.ttsButton = ttsButton
        state.languageButton = languageButton
        addButton.accept(button)
        addButton.accept(ttsButton)
        addButton.accept(languageButton)

        // Idem para o livro escrivel: reaplica o texto traduzido apos o re-init.
        if (state.translatedMode && state.translatedPages != null) {
            replaceWritablePages(screen, state.translatedPages!!)
            setWritableEditingButtonsVisible(screen, false)
        }
    }

    fun isWritableTranslated(screen: BookEditScreen): Boolean {
        return writableStates[screen]?.translatedMode == true
    }

    fun tick(mc: Minecraft) {
        if (ttsActive && mc.screen !is BookViewScreen && mc.screen !is BookEditScreen) {
            stopTts()
        }
    }

    private fun toggle(screen: BookViewScreen) {
        val accessor = screen as BookViewScreenAccessor
        val state = states.computeIfAbsent(screen) { State(accessor.nexelGetBookAccess()) }
        state.translatedMode = !state.translatedMode

        if (state.translatedMode) {
            if (state.translatedPages == null) {
                state.translatedPages = MutableList(state.originalPages.size) { TextComponent("...") }
            }
            // Sempre re-enfileira: paginas que falharam antes ficavam com o texto
            // original e nunca eram tentadas de novo sem fechar e reabrir o livro.
            queueTranslations(screen, state)
            accessor.nexelSetBookAccess(bookAccessFromPages(state.translatedPages!!.toList()))
        } else {
            accessor.nexelSetBookAccess(state.originalAccess)
        }

        accessor.nexelSetPage(accessor.nexelGetCurrentPage())
        state.button?.translated = state.translatedMode
    }

    private fun speakCurrentPage(screen: BookViewScreen) {
        val accessor = screen as BookViewScreenAccessor
        val state = states[screen] ?: return
        val page = accessor.nexelGetCurrentPage()
        val text = if (state.translatedMode) {
            state.translatedPages?.getOrNull(page)?.string
        } else {
            state.originalPages.getOrNull(page)?.string
        }?.trim().orEmpty()

        speakText(text)
    }

    fun stopTts() {
        LocalTtsSpeaker.stop()
        ttsActive = false
    }

    private fun queueTranslations(screen: BookViewScreen, state: State) {
        val targetLanguage = NTranslator.config.client.subtitleLanguage
        state.originalPages.forEachIndexed { pageIndex, component ->
            if (state.translatedOk.contains(pageIndex) || !state.loadingPages.add(pageIndex)) {
                return@forEachIndexed
            }

            state.translatedPages?.set(pageIndex, TextComponent("..."))
            ClientTranslationHelper.translateTextAsync(component.string, targetLanguage).whenComplete { result, error ->
                state.loadingPages.remove(pageIndex)
                if (error == null && result != null && result.complete) {
                    state.translatedOk.add(pageIndex)
                }

                val translated = if (result == null) component.string else result.text
                state.translatedPages?.set(pageIndex, TextComponent(translated))

                Minecraft.getInstance().execute {
                    val currentScreen = Minecraft.getInstance().screen as? BookViewScreen ?: return@execute
                    if (currentScreen !== screen) {
                        return@execute
                    }

                    val currentState = states[currentScreen] ?: return@execute
                    if (!currentState.translatedMode || currentState.translatedPages == null) {
                        return@execute
                    }

                    val accessor = currentScreen as BookViewScreenAccessor
                    accessor.nexelSetBookAccess(bookAccessFromPages(currentState.translatedPages!!.toList()))
                    accessor.nexelSetPage(accessor.nexelGetCurrentPage())
                }
            }
        }
    }

    private fun refreshReadableTranslation(screen: BookViewScreen) {
        val accessor = screen as BookViewScreenAccessor
        val state = states[screen] ?: return
        if (!state.translatedMode) {
            return
        }

        state.loadingPages.clear()
        state.translatedOk.clear()
        state.translatedPages = MutableList(state.originalPages.size) { TextComponent("...") }
        accessor.nexelSetBookAccess(bookAccessFromPages(state.translatedPages!!.toList()))
        accessor.nexelSetPage(accessor.nexelGetCurrentPage())
        queueTranslations(screen, state)
    }

    private fun toggleWritable(screen: BookEditScreen) {
        val state = writableStates.computeIfAbsent(screen) { WritableState() }
        state.translatedMode = !state.translatedMode

        if (state.translatedMode) {
            val originalPages = (screen as BookEditScreenAccessor).nexelGetPages().toMutableList().ifEmpty {
                mutableListOf("")
            }
            state.originalPages = originalPages
            state.translatedPages = MutableList(originalPages.size) { "..." }
            // As paginas foram relidas da tela, entao o que ja tinha sido traduzido
            // nao vale mais: tudo e traduzido de novo.
            state.translatedOk.clear()
            state.loadingPages.clear()
            setWritableEditingButtonsVisible(screen, false)
            replaceWritablePages(screen, state.translatedPages!!)
            queueWritableTranslations(screen, state)
        } else {
            restoreWritableOriginal(screen, state)
        }

        state.button?.translated = state.translatedMode
    }

    private fun speakCurrentWritablePage(screen: BookEditScreen) {
        val accessor = screen as BookEditScreenAccessor
        val state = writableStates[screen]
        val page = accessor.nexelGetCurrentPage()
        val text = if (state?.translatedMode == true) {
            state.translatedPages?.getOrNull(page)
        } else {
            accessor.nexelGetPages().getOrNull(page)
        }?.trim().orEmpty()

        speakText(text)
    }

    private fun queueWritableTranslations(screen: BookEditScreen, state: WritableState) {
        val targetLanguage = NTranslator.config.client.subtitleLanguage
        val originalPages = state.originalPages ?: return

        originalPages.forEachIndexed { pageIndex, pageText ->
            if (state.translatedOk.contains(pageIndex) || !state.loadingPages.add(pageIndex)) {
                return@forEachIndexed
            }

            if (pageText.isBlank()) {
                state.translatedPages?.set(pageIndex, pageText)
                state.translatedOk.add(pageIndex)
                state.loadingPages.remove(pageIndex)
                return@forEachIndexed
            }

            ClientTranslationHelper.translateTextAsync(pageText, targetLanguage).whenComplete { result, error ->
                state.loadingPages.remove(pageIndex)
                if (error == null && result != null && result.complete) {
                    state.translatedOk.add(pageIndex)
                }

                val translated = if (result == null) pageText else result.text
                state.translatedPages?.set(pageIndex, translated)

                Minecraft.getInstance().execute {
                    val currentScreen = Minecraft.getInstance().screen as? BookEditScreen ?: return@execute
                    if (currentScreen !== screen) {
                        return@execute
                    }

                    val currentState = writableStates[currentScreen] ?: return@execute
                    if (!currentState.translatedMode || currentState.translatedPages == null) {
                        return@execute
                    }

                    replaceWritablePages(currentScreen, currentState.translatedPages!!)
                    setWritableEditingButtonsVisible(currentScreen, false)
                }
            }
        }
    }

    private fun refreshWritableTranslation(screen: BookEditScreen) {
        val state = writableStates[screen] ?: return
        if (!state.translatedMode) {
            return
        }

        val originalPages = state.originalPages ?: return
        state.loadingPages.clear()
        state.translatedOk.clear()
        state.translatedPages = MutableList(originalPages.size) { "..." }
        replaceWritablePages(screen, state.translatedPages!!)
        setWritableEditingButtonsVisible(screen, false)
        queueWritableTranslations(screen, state)
    }

    private fun restoreWritableOriginal(screen: BookEditScreen, state: WritableState) {
        val originalPages = state.originalPages ?: return
        replaceWritablePages(screen, originalPages)
        setWritableEditingButtonsVisible(screen, true)
    }

    private fun replaceWritablePages(screen: BookEditScreen, pages: List<String>) {
        val accessor = screen as BookEditScreenAccessor
        val targetPages = accessor.nexelGetPages()
        targetPages.clear()
        targetPages.addAll(pages.ifEmpty { listOf("") })
        accessor.nexelGetPageEdit().setSelectionRange(0, 0)
        accessor.nexelClearDisplayCache()
    }

    private fun setWritableEditingButtonsVisible(screen: BookEditScreen, visible: Boolean) {
        val accessor = screen as BookEditScreenAccessor
        accessor.nexelGetDoneButton()?.let {
            it.visible = visible
            it.active = visible
        }
        accessor.nexelGetSignButton()?.let {
            it.visible = visible
            it.active = visible
        }
    }

    private fun speakText(text: String) {
        val normalizedText = text
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalizedText.isBlank() || normalizedText == "...") {
            return
        }

        // Antes isto fazia uma chamada de rede para detectar o idioma antes de falar,
        // o que segurava o audio por segundos. O idioma ja e conhecido: ou veio da
        // traducao (cache), ou o texto esta no idioma escolhido pelo jogador.
        val language = ClientTranslationHelper.cachedLanguage(normalizedText)
            ?: NTranslator.config.client.subtitleLanguage

        ttsActive = true
        LocalTtsSpeaker.speak(normalizedText, language)
    }

    private fun buttonLayout(screen: net.minecraft.client.gui.screens.Screen): ButtonLayout {
        val doneButtonY = screen.children()
            .filterIsInstance<AbstractWidget>()
            .filter { it.width >= 80 || it.message.string.equals("done", ignoreCase = true) }
            .minOfOrNull { it.y }

        val anchoredY = doneButtonY
            ?.minus(DONE_TO_BOOK_TOP)
            ?.coerceAtLeast(DEFAULT_BUTTON_Y)
            ?: DEFAULT_BUTTON_Y

        return ButtonLayout(screen.width / 2 + BUTTON_X_OFFSET, anchoredY)
    }

    private data class State(
        val originalAccess: BookViewScreen.BookAccess,
        val originalPages: List<Component> = pagesFromAccess(originalAccess),
        var translatedPages: MutableList<Component>? = null,
        var translatedMode: Boolean = false,
        var button: TranslateBookButton? = null,
        var ttsButton: AbstractWidget? = null,
        var languageButton: AbstractWidget? = null,
        val loadingPages: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        /** Paginas ja traduzidas com sucesso; o resto e tentado de novo a cada toggle. */
        val translatedOk: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    )

    private data class ButtonLayout(
        val x: Int,
        val y: Int
    )

    private data class WritableState(
        var originalPages: MutableList<String>? = null,
        var translatedPages: MutableList<String>? = null,
        var translatedMode: Boolean = false,
        var button: TranslateBookButton? = null,
        var ttsButton: AbstractWidget? = null,
        var languageButton: AbstractWidget? = null,
        val loadingPages: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        /** Paginas ja traduzidas com sucesso; o resto e tentado de novo a cada toggle. */
        val translatedOk: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    )

    private fun bookAccessFromPages(pages: List<Component>): BookViewScreen.BookAccess {
        val safePages = pages.ifEmpty { listOf(TextComponent("")) }
        return object : BookViewScreen.BookAccess {
            override fun getPageCount(): Int = safePages.size

            override fun getPageRaw(index: Int): FormattedText {
                return safePages.getOrNull(index) ?: FormattedText.EMPTY
            }
        }
    }

    private fun pagesFromAccess(access: BookViewScreen.BookAccess): List<Component> {
        return (0 until access.pageCount).map { index ->
            TextComponent(formattedTextToString(access.getPageRaw(index)))
        }
    }

    private fun formattedTextToString(text: FormattedText): String {
        val builder = StringBuilder()
        text.visit<String> { part ->
            builder.append(part)
            Optional.empty()
        }
        return builder.toString()
    }

    private abstract class NexelBookButton(
        x: Int,
        y: Int,
        private val onPress: () -> Unit
    ) : AbstractWidget(x, y, BUTTON_SIZE, BUTTON_SIZE, TextComponent("")) {

        override fun onClick(mouseX: Double, mouseY: Double) {
            onPress()
        }

        override fun renderButton(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
            val background = when {
                !active -> 0xFF2D3238.toInt()
                isHoveredOrFocused -> 0xFF5A6574.toInt()
                else -> 0xFF434C58.toInt()
            }

            fill(poseStack, x, y, x + width, y + height, background)
            drawOutline(poseStack, if (isHoveredOrFocused) 0xFF9EC7FF.toInt() else 0x66242F3F)
            renderIcon(poseStack)
        }

        private fun drawOutline(poseStack: PoseStack, color: Int) {
            fill(poseStack, x, y, x + width, y + 1, color)
            fill(poseStack, x, y + height - 1, x + width, y + height, color)
            fill(poseStack, x, y + 1, x + 1, y + height - 1, color)
            fill(poseStack, x + width - 1, y + 1, x + width, y + height - 1, color)
        }

        protected abstract fun renderIcon(poseStack: PoseStack)

        override fun updateNarration(narrationElementOutput: NarrationElementOutput) {
        }
    }

    private class TranslateBookButton(
        x: Int,
        y: Int,
        translated: Boolean,
        onPress: () -> Unit
    ) : NexelBookButton(x, y, onPress) {
        var translated = translated
            set(value) {
                field = value
                message = TextComponent(if (value) "<" else "\u21C4")
            }

        init {
            this.translated = translated
        }

        override fun renderIcon(poseStack: PoseStack) {
            val label = if (translated) "<" else "\u21C4"
            drawCenteredString(poseStack, Minecraft.getInstance().font, label, x + width / 2, y + 6, 0xE7F0FF)
        }
    }

    private class IconBookButton(
        x: Int,
        y: Int,
        private val icon: ResourceLocation,
        onPress: () -> Unit
    ) : NexelBookButton(x, y, onPress) {
        override fun renderIcon(poseStack: PoseStack) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            RenderSystem.setShaderTexture(0, icon)
            RenderSystem.enableBlend()
            blit(poseStack, x + 2, y + 2, 0.0f, 0.0f, 16, 16, 16, 16)
            RenderSystem.disableBlend()
        }
    }

    private class TextBookButton(
        x: Int,
        y: Int,
        private val label: String,
        onPress: () -> Unit
    ) : NexelBookButton(x, y, onPress) {
        override fun renderIcon(poseStack: PoseStack) {
            drawCenteredString(poseStack, Minecraft.getInstance().font, label, x + width / 2, y + 6, 0xE7F0FF)
        }
    }
}

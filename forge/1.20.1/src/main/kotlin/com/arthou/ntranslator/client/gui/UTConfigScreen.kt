package com.arthou.ntranslator.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import com.arthou.ntranslator.NTranslator
import com.arthou.ntranslator.client.NTranslatorClient
import com.arthou.ntranslator.client.transcribers.browser.BrowserSpeechTranscriber
import com.arthou.ntranslator.config.*
import com.arthou.ntranslator.config.IntRange
import com.arthou.ntranslator.duck.ScrollableWidget
import com.arthou.ntranslator.mixin.AbstractWidgetAccessor
import com.arthou.ntranslator.translator.TranslatorManager
import java.lang.reflect.Method
import kotlin.math.absoluteValue

class UTConfigScreen(private val parent: Screen?) : Screen(Component.literal("NEXEL translations")) {
    companion object {
        private const val BACKGROUND = 0x7A101622
        private const val PANEL_BORDER = 0x66242F3F
        private const val PANEL = 0xE0202B38.toInt()
        private const val HEADER = 0xFF31445C.toInt()
        private const val CONTENT = 0x88323E4E.toInt()
        private const val LABEL_COLOR = 0xE7F0FF

        val ARROW_UP = NTranslator.id("arrow_up")
        val ARROW_DOWN = NTranslator.id("arrow_down")
        val MESSAGE_ICON = NTranslator.id("textures/gui/message_icon.png")
        val GEAR_ICON = NTranslator.id("textures/gui/gear_icon.png")
    }
    
    override fun init() {
        val panelLeft = panelLeft()
        val panelTop = panelTop()
        val panelWidth = panelWidth()
        val valueWidth = 174
        val valueX = panelLeft + panelWidth - valueWidth - 22
        val spokenY = panelTop + 54
        val subtitlesY = spokenY + 30
        val chatBoxY = subtitlesY + 30
        val bottomY = panelTop + panelHeight() - 28

        addRenderableWidget(
            Button.builder(languageButtonMessage(NTranslator.config.client.spokenLanguage)) {
                Minecraft.getInstance().setScreen(LanguageSelectScreen(this, LanguageSelectScreen.Mode.SPOKEN))
            }
                .pos(valueX, spokenY)
                .size(valueWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(languageButtonMessage(NTranslator.config.client.subtitleLanguage)) {
                Minecraft.getInstance().setScreen(LanguageSelectScreen(this, LanguageSelectScreen.Mode.SUBTITLES))
            }
                .pos(valueX, subtitlesY)
                .size(valueWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(chatBoxButtonMessage()) {
                Minecraft.getInstance().setScreen(EditTranscriptBoxesScreen(NTranslatorClient.languageBoxes, this))
            }
                .pos(valueX, chatBoxY)
                .size(valueWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.ntranslator.config.close")) {
                this.onClose()
            }
                .pos(panelLeft + 22, bottomY)
                .size(panelWidth - 82, 20)
                .build()
        )

        addRenderableWidget(
            TextureIconButton(
                panelLeft + panelWidth - 52,
                bottomY,
                Component.translatable("gui.ntranslator.config.speech_bubble"),
                MESSAGE_ICON
            ) {
                Minecraft.getInstance().setScreen(SpeechBubbleCustomizationScreen(this))
            }
        )

        addRenderableWidget(
            TextureIconButton(
                panelLeft + panelWidth - 26,
                bottomY,
                Component.translatable("gui.ntranslator.config.advanced"),
                GEAR_ICON
            ) {
                Minecraft.getInstance().setScreen(UTConfigSubScreen(NTranslator.config.server::class.java, NTranslator.config.server, "common"))
            }
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelLeft = panelLeft()
        val panelTop = panelTop()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val labelX = panelLeft + 24

        guiGraphics.fill(0, 0, width, height, BACKGROUND)
        guiGraphics.fill(panelLeft - 4, panelTop - 4, panelLeft + panelWidth + 4, panelTop + panelHeight + 4, PANEL_BORDER)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 28, HEADER)
        guiGraphics.fill(panelLeft + 16, panelTop + 42, panelLeft + panelWidth - 16, panelTop + panelHeight - 42, CONTENT)

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(this.font, this.title, panelLeft + panelWidth / 2, panelTop + (28 - this.font.lineHeight) / 2, 0xFFFFFF)
        guiGraphics.drawString(this.font, Component.translatable("gui.ntranslator.config.spoken_language"), labelX, panelTop + 60, LABEL_COLOR, false)
        guiGraphics.drawString(this.font, Component.translatable("gui.ntranslator.config.subtitles_language"), labelX, panelTop + 90, LABEL_COLOR, false)
        guiGraphics.drawString(this.font, Component.translatable("gui.ntranslator.config.chat_box_language"), labelX, panelTop + 120, LABEL_COLOR, false)
        NTranslatorClient.renderCreditText(guiGraphics)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (NTranslatorClient.handleCreditClick(mouseX, mouseY)) {
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun panelWidth(): Int {
        return minOf(380, this.width - 28)
    }

    private fun panelHeight(): Int {
        return minOf(182, this.height - 24)
    }

    private fun panelLeft(): Int {
        return (this.width - this.panelWidth()) / 2
    }

    private fun panelTop(): Int {
        return (this.height - this.panelHeight()) / 2
    }

    private fun languageButtonMessage(language: com.arthou.ntranslator.Language): Component {
        return Component.empty().append(language.text)
    }

    private fun chatBoxButtonMessage(): Component {
        val boxes = NTranslator.config.client.transcriptBoxes.map { it.language.text.string }

        return when {
            boxes.isEmpty() -> Component.translatable("gui.ntranslator.config.chat_box_language.add")
            boxes.size == 1 -> Component.literal(boxes.first())
            boxes.size == 2 -> Component.literal("${boxes.first()} / ${boxes.last()}")
            else -> Component.literal("${boxes.first()} +${boxes.size - 1}")
        }
    }

    private abstract class SmallSquareIconButton(
        x: Int,
        y: Int,
        tooltipText: Component,
        private val onPress: () -> Unit
    ) : AbstractWidget(x, y, 20, 20, Component.empty()) {
        init {
            tooltip = Tooltip.create(tooltipText)
        }

        override fun onClick(mouseX: Double, mouseY: Double) {
            onPress()
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val background = when {
                !active -> 0xFF2D3238.toInt()
                isHovered -> 0xFF5A6574.toInt()
                else -> 0xFF434C58.toInt()
            }
            guiGraphics.fill(x, y, x + width, y + height, background)
            guiGraphics.renderOutline(x, y, width, height, if (isHovered) 0xFF9EC7FF.toInt() else 0x66242F3F)
            renderIcon(guiGraphics)
        }

        protected abstract fun renderIcon(guiGraphics: GuiGraphics)

        override fun updateWidgetNarration(narrationElementOutput: net.minecraft.client.gui.narration.NarrationElementOutput) {
        }
    }

    private class TextureIconButton(
        x: Int,
        y: Int,
        tooltipText: Component,
        private val icon: ResourceLocation,
        private val onPress: () -> Unit
    ) : SmallSquareIconButton(x, y, tooltipText, onPress) {
        override fun renderIcon(guiGraphics: GuiGraphics) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((x + 4).toDouble(), (y + 4).toDouble(), 0.0)
            guiGraphics.pose().scale(12f / 64f, 12f / 64f, 1f)
            guiGraphics.blit(icon, 0, 0, 0f, 0f, 64, 64, 64, 64)
            guiGraphics.pose().popPose()
        }
    }

    inner class UTConfigSubScreen<T : Any>(val configClass: Class<out T>, val instance: T, val type: String) : Screen(Component.translatable("gui.ntranslator.config.$type")) {
        lateinit var doneButton: Button
        private var scrollAmount = 0.0
        private var scrolling = false

        val scrollbarPosition: Int
            get() {
                return contentRight() + 6
            }

        var maxPosition = 0
        val maxScroll: Int
            get() {
                return (this.maxPosition - viewportBottom()).coerceAtLeast(0)
            }

        private fun panelWidth(): Int {
            return minOf(840, this.width - 24)
        }

        private fun panelHeight(): Int {
            return minOf(520, this.height - 24)
        }

        private fun panelLeft(): Int {
            return (this.width - panelWidth()) / 2
        }

        private fun panelTop(): Int {
            return (this.height - panelHeight()) / 2
        }

        private fun contentLeft(): Int {
            return panelLeft() + 18
        }

        private fun contentRight(): Int {
            return panelLeft() + panelWidth() - 18
        }

        private fun contentTop(): Int {
            return panelTop() + 46
        }

        private fun contentBottom(): Int {
            return panelTop() + panelHeight() - 46
        }

        private fun viewportTop(): Int {
            return contentTop() + 14
        }

        private fun viewportBottom(): Int {
            return contentBottom() - 10
        }

        private fun viewportHeight(): Int {
            return viewportBottom() - viewportTop()
        }

        private fun labelX(): Int {
            return contentLeft() + 12
        }

        private fun fieldWidth(): Int {
            return minOf(156, (panelWidth() * 0.28).toInt().coerceAtLeast(112))
        }

        private fun fieldX(): Int {
            return contentRight() - fieldWidth() - 16
        }

        private fun centeredButtonX(buttonWidth: Int = Button.DEFAULT_WIDTH): Int {
            return panelLeft() + (panelWidth() - buttonWidth) / 2
        }

        private fun configMembers(): List<ConfigMember> {
            val methods = configClass.methods.associateBy { it.name }
            return configClass.methods
                .filter { method ->
                    method.parameterCount == 0 &&
                        method.name !in setOf("getClass", "toString", "hashCode", "copy", "equals") &&
                        !method.name.startsWith("component")
                }
                .mapNotNull { getter ->
                    val name = propertyName(getter) ?: return@mapNotNull null
                    val setter = methods[setterName(name)] ?: return@mapNotNull null
                    if (setter.parameterCount != 1) {
                        return@mapNotNull null
                    }
                    ConfigMember(name, getter, setter)
                }
                .distinctBy { it.name }
                .sortedWith(compareBy<ConfigMember> {
                    when (it.name) {
                        "preferEdgeBrowser" -> 0
                        "preferChromeBrowser" -> 1
                        "useLibreTranslate" -> 2
                        else -> 4
                    }
                }.thenBy { it.name })
        }

        private fun propertyName(method: Method): String? {
            return when {
                method.name.startsWith("get") && method.name.length > 3 ->
                    method.name.substring(3).replaceFirstChar { it.lowercaseChar() }
                method.name.startsWith("is") && method.name.length > 2 && method.returnType == java.lang.Boolean.TYPE ->
                    method.name.substring(2).replaceFirstChar { it.lowercaseChar() }
                else -> null
            }
        }

        private fun setterName(name: String): String {
            return "set" + name.replaceFirstChar { it.uppercaseChar() }
        }

        private fun addArrowWidget(x: Int, y: Int, sprite: ResourceLocation, enabled: Boolean, onPress: () -> Unit) {
            addRenderableWidget(
                object : SmallSquareIconButton(x, y, Component.empty(), {
                    if (enabled) {
                        onPress()
                    }
                }) {
                    init {
                        width = 12
                        (this as AbstractWidgetAccessor).setHeight(12)
                        (this as ScrollableWidget).updateInitialPosition()
                        active = enabled
                    }

                    override fun renderIcon(guiGraphics: GuiGraphics) {
                        guiGraphics.blit(sprite, this.x + 2, this.y + 2, 0f, 0f, 8, 8, 8, 8)
                    }
                }
            )
        }

        override fun init() {
            var y = viewportTop() + 8
            val hideLibreSettings = type == "common" && !NTranslator.config.server.useLibreTranslate
            val orderedMembers = configMembers()

            for (member in orderedMembers) {
                val dependent = member.annotation(DependsOn::class.java)
                if (dependent != null && orderedMembers.firstOrNull { it.name == dependent.configName }?.get(instance) != true) {
                    continue
                }

                if (member.annotation(Hidden::class.java) != null)
                    continue

                if (type == "common" && member.name == "preferChromeBrowser") {
                    continue
                }

                if (hideLibreSettings && member.name in setOf(
                        "translatePriority",
                        "shouldUseCuda",
                        "shouldRunTranslationServer",
                        "libreTranslateThreads",
                        "offloadServers",
                        "batchTranslateInterval"
                    )
                ) {
                    continue
                }

                val name = StringWidget(Component.translatable("config.ntranslator.$type.${member.name}"), font)
                name.x = labelX()
                name.y = y
                name.alignLeft()
                name.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.desc"))
                (name as ScrollableWidget).updateInitialPosition()

                addRenderableWidget(name)

                val value = member.get(instance)

                if (type == "common" && member.name == "preferEdgeBrowser") {
                    addBrowserPreferenceButtons(y)
                } else if (value is Boolean) {
                    var current: Boolean = value

                    val button = Button.builder(Component.translatable("ntranslator.value.$current")
                        .withStyle(if (current) ChatFormatting.GREEN else ChatFormatting.RED)
                    ) { btn ->
                        current = !current
                        member.set(instance, current)
                        rebuildWidgets()

                        btn.message = Component.translatable("ntranslator.value.$current")
                            .withStyle(if (current) ChatFormatting.GREEN else ChatFormatting.RED)
                    }
                        .pos(fieldX(), y - (Button.DEFAULT_HEIGHT / 2) + 3)
                        .width(fieldWidth())
                        .tooltip(Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.desc")))
                        .build()

                    addRenderableWidget(button)
                } else if (value is Enum<*>) {
                    val enumClass = value::class.java as Class<Enum<*>>
                    val values = enumClass.enumConstants
                    var current = value.ordinal

                    val button = Button.builder(Component.literal(values[current].name.propercase())) { btn ->
                        if (hasShiftDown()) {
                            current -= 1
                            if (current < 0)
                                current = values.size - 1
                        } else {
                            current += 1
                            if (current >= values.size)
                                current = 0
                        }

                        member.set(instance, values[current])
                        btn.message = Component.literal(values[current].name.propercase())
                    }
                        .pos(fieldX(), y - (Button.DEFAULT_HEIGHT / 2) + 3)
                        .width(fieldWidth())
                        .tooltip(Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.desc")))
                        .build()

                    addRenderableWidget(button)
                } else if (value is MutableList<*> && member.name == "offloadServers") { // special case
                    val actualValue = value as MutableList<NTranslatorConfig.OffloadedLibreTranslateServer>

                    addRenderableWidget(Button.builder(Component.literal("+")) {
                        actualValue.add(NTranslatorConfig.OffloadedLibreTranslateServer(""))
                        this.rebuildWidgets()
                    }
                        .pos(contentRight() - Button.DEFAULT_HEIGHT - 16, y - (Button.DEFAULT_HEIGHT / 2) + 3)
                        .width(Button.DEFAULT_HEIGHT)
                        .build())

                    for ((index, server) in actualValue.withIndex()) {
                        y += 30

                        val boxWidth = ((contentRight() - contentLeft() - 80) / 2).coerceAtLeast(120)
                        val leftBoxX = labelX()
                        val rightBoxX = leftBoxX + boxWidth + 8

                        addRenderableWidget(EditBox(font, leftBoxX, y - (Button.DEFAULT_HEIGHT / 2) + 3, boxWidth, Button.DEFAULT_HEIGHT, Component.translatable("ntranslator.value.none")).apply {
                            this.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.website_url.desc"))
                            this.value = server.url
                            this.setResponder {
                                server.url = it
                            }
                        })

                        addRenderableWidget(EditBox(font, rightBoxX, y - (Button.DEFAULT_HEIGHT / 2) + 3, boxWidth, Button.DEFAULT_HEIGHT, Component.translatable("ntranslator.value.none")).apply {
                            this.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.api_key.desc"))
                            this.value = server.authKey ?: ""
                            this.setResponder {
                                server.authKey = it
                            }
                        })

                        addRenderableWidget(Button.builder(Component.literal("-")) {
                            actualValue.removeAt(index)
                            this.rebuildWidgets()
                        }
                            .pos(contentRight() - Button.DEFAULT_HEIGHT - 16, y - (Button.DEFAULT_HEIGHT / 2) + 3)
                            .width(Button.DEFAULT_HEIGHT)
                            .build())

                        addArrows(contentRight() - 12, y, index, actualValue)
                    }
                } else if (value is MutableList<*> && member.name == "translatePriority") { // special case
                    val actualValue = value as MutableList<NTranslatorConfig.TranslationPriority>

                    for ((index, priority) in actualValue.withIndex()) {
                        y += 30

                        val text = Component.translatable("config.ntranslator.$type.${member.name}.${priority.name.lowercase()}")
                        val priorityName = StringWidget(text, font)
                        priorityName.x = labelX()
                        priorityName.y = y
                        priorityName.alignLeft()
                        priorityName.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.${priority.name.lowercase()}.desc"))
                        (priorityName as ScrollableWidget).updateInitialPosition()

                        addRenderableWidget(priorityName)
                        addArrows(contentRight() - 12, y, index, actualValue)
                    }
                } else if (value is Float) {
                    val range = member.annotation(FloatRange::class.java) ?: throw IllegalStateException("Missing range!")

                    val min = range.from
                    val max = range.to

                    addRenderableWidget(EditBox(font, fieldX(), y - (Button.DEFAULT_HEIGHT / 2) + 4, fieldWidth(), Button.DEFAULT_HEIGHT, Component.empty())
                        .apply {
                            this.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.desc"))
                            this.value = value.toString()
                            this.setFilter { it.toFloatOrNull() != null || it.isBlank() || it.contains('.') } // TODO: make adjustable via annotation
                            this.setResponder {
                                member.set(instance, Mth.clamp((
                                        if (it.startsWith('.'))
                                            "0$it"
                                        else if (it.endsWith('.'))
                                            "${it}0"
                                        else
                                            it
                                        ).toFloatOrNull() ?: 0.0f, min, max
                                ))
                            }
                        })
                    addArrowWidget(contentRight() - 12, y - 8, ARROW_UP, value < max) {
                        member.set(instance, Mth.clamp(value + range.increment, min, max))
                        rebuildWidgets()
                    }
                    addArrowWidget(contentRight() - 12, y + 4, ARROW_DOWN, value > min) {
                        member.set(instance, Mth.clamp(value - range.increment, min, max))
                        rebuildWidgets()
                    }
                } else if (value is Int) {
                    val range = member.annotation(IntRange::class.java) ?: throw IllegalStateException("Missing range!")

                    val min = range.from
                    val max = range.to.run {
                        if (member.name == "libreTranslateThreads") {
                            Mth.clamp(this, 1, Runtime.getRuntime().availableProcessors() - 2)
                        } else this
                    }

                    addRenderableWidget(EditBox(font, fieldX(), y - (Button.DEFAULT_HEIGHT / 2) + 4, fieldWidth(), Button.DEFAULT_HEIGHT, Component.empty())
                        .apply {
                            this.tooltip = Tooltip.create(Component.translatable("config.ntranslator.$type.${member.name}.desc"))
                            this.value = value.toString()
                            this.setFilter { it.toIntOrNull() != null || it.isBlank() } // TODO: make adjustable via annotation
                            this.setResponder {
                                member.set(instance, Mth.clamp(it.toIntOrNull() ?: min, min, max))
                            }
                        })
                    addArrowWidget(contentRight() - 12, y - 8, ARROW_UP, value < max) {
                        member.set(instance, Mth.clamp(value + range.increment, min, max))
                        rebuildWidgets()
                    }
                    addArrowWidget(contentRight() - 12, y + 4, ARROW_DOWN, value > min) {
                        member.set(instance, Mth.clamp(value - range.increment, min, max))
                        rebuildWidgets()
                    }
                }

                y += 30
            }

            if (type == "client") { // Special case
                addRenderableWidget(Button.builder(Component.translatable("ntranslator.configure_boxes")) {
                    Minecraft.getInstance().setScreen(EditTranscriptBoxesScreen(NTranslatorClient.languageBoxes, this@UTConfigSubScreen))
                }
                    .pos(centeredButtonX(), y)
                    .build()
                    .apply {
                        (this as ScrollableWidget).updateInitialPosition()
                    }
                )

                y += 30

                addRenderableWidget(Button.builder(Component.translatable("ntranslator.set_spoken_language")) {
                    Minecraft.getInstance().setScreen(LanguageSelectScreen(this@UTConfigSubScreen, LanguageSelectScreen.Mode.SPOKEN))
                }
                    .pos(centeredButtonX(), y)
                    .build()
                    .apply {
                        (this as ScrollableWidget).updateInitialPosition()
                    }
                )

                y += 30

                addRenderableWidget(Button.builder(Component.translatable("gui.ntranslator.config.subtitles_language")) {
                    Minecraft.getInstance().setScreen(LanguageSelectScreen(this@UTConfigSubScreen, LanguageSelectScreen.Mode.SUBTITLES))
                }
                    .pos(centeredButtonX(), y)
                    .build()
                    .apply {
                        (this as ScrollableWidget).updateInitialPosition()
                    }
                )

                y += 30
            }

            maxPosition = y + 18

            doneButton = addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE) {
                    this.onClose()
                }
                    .pos(centeredButtonX(), panelTop() + panelHeight() - 30)
                        .build()
            )
        }

        private fun addBrowserPreferenceButtons(y: Int) {
            val buttonWidth = (fieldWidth() - 6) / 2
            val edge = NTranslator.config.server.preferEdgeBrowser
            val chrome = NTranslator.config.server.preferChromeBrowser

            addRenderableWidget(browserPreferenceButton(
                fieldX(),
                y,
                buttonWidth,
                "Edge",
                edge,
                Component.translatable("config.ntranslator.common.preferEdgeBrowser.desc")
            ) {
                val next = !NTranslator.config.server.preferEdgeBrowser
                NTranslator.config.server.lastHiddenBrowser = "edge"
                NTranslator.config.server.preferEdgeBrowser = next
                if (next) {
                    NTranslator.config.server.preferChromeBrowser = false
                }
                NTranslator.saveConfig()
                reopenBrowserTranscriber()
                rebuildWidgets()
            })

            addRenderableWidget(browserPreferenceButton(
                fieldX() + buttonWidth + 6,
                y,
                buttonWidth,
                "Chrome",
                chrome,
                Component.translatable("config.ntranslator.common.preferChromeBrowser.desc")
            ) {
                val next = !NTranslator.config.server.preferChromeBrowser
                NTranslator.config.server.lastHiddenBrowser = "chrome"
                NTranslator.config.server.preferChromeBrowser = next
                if (next) {
                    NTranslator.config.server.preferEdgeBrowser = false
                }
                NTranslator.saveConfig()
                reopenBrowserTranscriber()
                rebuildWidgets()
            })
        }

        private fun browserPreferenceButton(
            x: Int,
            y: Int,
            width: Int,
            label: String,
            selected: Boolean,
            tooltipText: Component,
            onPress: () -> Unit
        ): Button {
            return Button.builder(Component.literal("${if (selected) "☑" else "☐"} $label")) {
                onPress()
            }
                .pos(x, y - (Button.DEFAULT_HEIGHT / 2) + 3)
                .width(width)
                .tooltip(Tooltip.create(tooltipText))
                .build()
        }

        private fun reopenBrowserTranscriber() {
            val transcriber = NTranslatorClient.transcriber
            if (transcriber is BrowserSpeechTranscriber) {
                transcriber.reopenWebsiteForBrowserChange()
            }
        }

        private fun <T> addArrows(x: Int, y: Int, index: Int, actualValue: MutableList<T>) {
            addArrowWidget(x, y - 8, ARROW_UP, index > 0) {
                val oldValue = actualValue[index]
                val oldPrevValue = actualValue[index - 1]
                actualValue[index - 1] = oldValue
                actualValue[index] = oldPrevValue
                rebuildWidgets()
            }
            addArrowWidget(x, y + 4, ARROW_DOWN, index < actualValue.size - 1) {
                val oldValue = actualValue[index]
                val oldPrevValue = actualValue[index + 1]
                actualValue[index + 1] = oldValue
                actualValue[index] = oldPrevValue
                rebuildWidgets()
            }
        }

        private fun String.propercase(): String {
            return "${this[0].uppercaseChar()}${this.lowercase().substring(1)}"
        }

        override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            guiGraphics.fill(0, 0, width, height, BACKGROUND)

            guiGraphics.fill(panelLeft() - 4, panelTop() - 4, panelLeft() + panelWidth() + 4, panelTop() + panelHeight() + 4, PANEL_BORDER)
            guiGraphics.fill(panelLeft(), panelTop(), panelLeft() + panelWidth(), panelTop() + panelHeight(), PANEL)
            guiGraphics.fill(panelLeft(), panelTop(), panelLeft() + panelWidth(), panelTop() + 28, HEADER)
            guiGraphics.fill(contentLeft(), contentTop(), contentRight(), contentBottom(), CONTENT)
            guiGraphics.drawString(this.font, this.title, panelLeft() + 18, panelTop() + 10, 0xFFFFFF, false)

            guiGraphics.enableScissor(contentLeft(), viewportTop(), contentRight(), viewportBottom())
            super.render(guiGraphics, mouseX, mouseY, partialTick)
            guiGraphics.disableScissor()

            val viewportHeight = viewportHeight()
            if (maxScroll.absoluteValue > 0 && viewportHeight > 8) {
                var scrollbarHeight = (viewportHeight * viewportHeight) / this.maxPosition.coerceAtLeast(viewportHeight)
                scrollbarHeight = Mth.clamp(scrollbarHeight, 32, viewportHeight - 8)

                var scrollbarPosY = (this.scrollAmount * (viewportHeight - scrollbarHeight) / maxScroll + viewportTop()).toInt()
                if (scrollbarPosY < viewportTop()) {
                    scrollbarPosY = viewportTop()
                }

                guiGraphics.fill(this.scrollbarPosition, viewportTop(), scrollbarPosition + 2, viewportBottom(), -16777216)
                guiGraphics.fill(this.scrollbarPosition, scrollbarPosY, scrollbarPosition + 2, scrollbarPosY + scrollbarHeight, -8355712)
                guiGraphics.fill(this.scrollbarPosition, scrollbarPosY, scrollbarPosition + 2 - 1, scrollbarPosY + scrollbarHeight - 1, -4144960)
            }

            doneButton.render(guiGraphics, mouseX, mouseY, partialTick)

            NTranslatorClient.renderCreditText(guiGraphics)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (NTranslatorClient.handleCreditClick(mouseX, mouseY)) {
                return true
            }

            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true
            }

            this.scrolling = button == 0 && mouseX >= this.scrollbarPosition && mouseX < this.scrollbarPosition + 6

            if (mouseY > viewportTop() && mouseY <= viewportBottom()) {
                return this.scrolling
            }

            return false
        }

        fun updateScroll() {
            for (child in this.children()) {
                if (child == doneButton)
                    continue

                if (child is AbstractWidget) {
                    child.y = (child as ScrollableWidget).initialY - scrollAmount.toInt()
                }
            }
        }

        override fun resize(minecraft: Minecraft, width: Int, height: Int) {
            super.resize(minecraft, width, height)

            if (this.scrollAmount > this.maxScroll)
                this.scrollAmount = this.maxScroll.toDouble()

            updateScroll()
        }

        override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
            if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true
            } else if (button == 0 && scrolling) {
                if (mouseY < viewportTop()) {
                    this.scrollAmount = 0.0
                } else if (mouseY > viewportBottom()) {
                    this.scrollAmount = this.maxScroll.toDouble()
                } else {
                    val max = this.maxScroll
                    val height = viewportHeight()
                    val scrollHeight = Mth.clamp(((height * height).toFloat() / this.maxPosition.toFloat()).toInt(), 32, height - 8)
                    val scrollDelta = (max / (height - scrollHeight).toDouble()).coerceAtMost(1.0)
                    this.scrollAmount = Mth.clamp(this.scrollAmount + dragY * scrollDelta, 0.0, this.maxScroll.toDouble())
                }

                updateScroll()
                return true
            }

            return false
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
            this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * (this.maxPosition / 4.0), 0.0, this.maxScroll.toDouble())
            updateScroll()
            return true
        }

        override fun onClose() {
            Minecraft.getInstance().setScreen(this@UTConfigScreen)
            NTranslator.saveConfig()
            if (type == "common") {
                TranslatorManager.loadFromConfig()
            }
        }
    }
}

private data class ConfigMember(
    val name: String,
    val getter: Method,
    val setter: Method
) {
    fun get(instance: Any): Any? = getter.invoke(instance)

    fun set(instance: Any, value: Any?) {
        setter.invoke(instance, value)
    }

    fun <A : Annotation> annotation(type: Class<A>): A? {
        return getter.getAnnotation(type)
    }
}

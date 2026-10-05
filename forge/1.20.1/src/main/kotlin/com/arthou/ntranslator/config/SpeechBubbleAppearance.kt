package com.arthou.ntranslator.config

import net.minecraft.network.FriendlyByteBuf

data class SpeechBubbleAppearance(
    val style: SpeechBubbleStyle,
    val lineMode: SpeechBubbleLineMode,
    val font: SpeechBubbleFont,
    val textColor: Int,
    val fillColor: Int,
    val borderColor: Int,
    val visible: Boolean = true
) {
    fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(style.resolved())
        buf.writeEnum(lineMode.resolved())
        buf.writeEnum(font)
        buf.writeInt(textColor)
        buf.writeInt(fillColor)
        buf.writeInt(borderColor)
        buf.writeBoolean(visible)
    }

    companion object {
        fun fromConfig(config: SpeechBubbleConfig): SpeechBubbleAppearance {
            return SpeechBubbleAppearance(
                style = config.style.resolved(),
                lineMode = config.lineMode.resolved(),
                font = config.font.resolved(),
                textColor = config.textColor,
                fillColor = config.fillColor,
                borderColor = config.borderColor,
                visible = config.showOwnBubble
            )
        }

        fun read(buf: FriendlyByteBuf): SpeechBubbleAppearance {
            val readerIndex = buf.readerIndex()
            return runCatching {
                readModern(buf)
            }.getOrElse {
                buf.readerIndex(readerIndex)
                readLegacy(buf)
            }
        }

        private fun readModern(buf: FriendlyByteBuf): SpeechBubbleAppearance {
            return SpeechBubbleAppearance(
                style = buf.readEnum(SpeechBubbleStyle::class.java).resolved(),
                lineMode = buf.readEnum(SpeechBubbleLineMode::class.java).resolved(),
                font = buf.readEnum(SpeechBubbleFont::class.java).resolved(),
                textColor = buf.readInt(),
                fillColor = buf.readInt(),
                borderColor = buf.readInt(),
                visible = if (buf.readableBytes() >= 1) buf.readBoolean() else true
            )
        }

        private fun readLegacy(buf: FriendlyByteBuf): SpeechBubbleAppearance {
            val defaults = SpeechBubbleConfig()
            val style = readEnumOrDefault(buf, SpeechBubbleStyle::class.java, SpeechBubbleStyle.ROUNDED).resolved()
            var lineMode = SpeechBubbleLineMode.STACKED
            var font = SpeechBubbleFont.NEXEL

            // Older beta clients sent only style + the three colors. Some dev builds
            // briefly sent style + font + colors; keep both readable without failing.
            if (buf.readableBytes() == 13) {
                font = readEnumOrDefault(buf, SpeechBubbleFont::class.java, SpeechBubbleFont.NEXEL).resolved()
            } else if (buf.readableBytes() >= 14) {
                lineMode = readEnumOrDefault(buf, SpeechBubbleLineMode::class.java, SpeechBubbleLineMode.STACKED).resolved()
                font = readEnumOrDefault(buf, SpeechBubbleFont::class.java, SpeechBubbleFont.NEXEL).resolved()
            }

            val textColor = if (buf.readableBytes() >= 4) buf.readInt() else defaults.textColor
            val fillColor = if (buf.readableBytes() >= 4) buf.readInt() else defaults.fillColor
            val borderColor = if (buf.readableBytes() >= 4) buf.readInt() else defaults.borderColor
            val visible = if (buf.readableBytes() >= 1) buf.readBoolean() else true

            return SpeechBubbleAppearance(
                style = style,
                lineMode = lineMode,
                font = font,
                textColor = textColor,
                fillColor = fillColor,
                borderColor = borderColor,
                visible = visible
            )
        }

        private fun <T : Enum<T>> readEnumOrDefault(buf: FriendlyByteBuf, enumClass: Class<T>, defaultValue: T): T {
            if (buf.readableBytes() <= 0) {
                return defaultValue
            }

            return runCatching {
                buf.readEnum(enumClass)
            }.getOrDefault(defaultValue)
        }
    }
}

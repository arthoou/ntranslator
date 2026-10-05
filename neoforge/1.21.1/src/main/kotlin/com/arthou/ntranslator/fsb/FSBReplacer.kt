package com.arthou.ntranslator.fsb

import com.arthou.ntranslator.Language
import com.arthou.ntranslator.NTranslator
import java.util.concurrent.ConcurrentHashMap

object FSBReplacer {
    private val rulesByLanguage = ConcurrentHashMap<Language, List<Rule>>()
    private val starRun = Regex("\\*+")
    private val fullPatternCharacters = Regex("^[\\s*.,!?\"'¿¡\\-]+$")
    private val wordBoundary = "[\\p{L}\\p{N}_]"

    fun apply(text: String, language: Language): String {
        if (!NTranslator.config.server.fsbEnabled || text.indexOf('*') < 0) {
            return text
        }

        return applyRules(text, rulesByLanguage.computeIfAbsent(language, ::loadRules))
    }

    internal fun applyForTest(text: String, rawRules: List<String>): String {
        return applyRules(text, parseRules(rawRules))
    }

    private fun applyRules(text: String, rules: List<Rule>): String {
        if (text.indexOf('*') < 0 || rules.isEmpty()) {
            return text
        }

        val selected = selectMatches(text, rules)
        if (selected.isEmpty()) {
            return text
        }

        val builder = StringBuilder(text.length)
        var cursor = 0
        for (match in selected.sortedBy { it.start }) {
            if (cursor < match.start) {
                builder.append(text, cursor, match.start)
            }
            builder.append(match.replacementText(text))
            cursor = match.end
        }
        if (cursor < text.length) {
            builder.append(text, cursor, text.length)
        }

        return builder.toString()
    }

    private fun selectMatches(text: String, rules: List<Rule>): List<RuleMatch> {
        val candidates = rules.flatMap { rule -> rule.findMatches(text) }
            .sortedWith(
                compareBy<RuleMatch> { it.rule.type.priority }
                    .thenByDescending { it.rule.pattern.length }
                    .thenBy { it.start }
                    .thenBy { it.rule.sourceOrder }
            )

        val selected = mutableListOf<RuleMatch>()
        val occupied = BooleanArray(text.length)
        for (candidate in candidates) {
            if ((candidate.start until candidate.end).any { occupied[it] }) {
                continue
            }

            selected.add(candidate)
            for (index in candidate.start until candidate.end) {
                occupied[index] = true
            }
        }

        return selected
    }

    private fun loadRules(language: Language): List<Rule> {
        val names = ruleFileNames(language)
        val lines = names.firstNotNullOfOrNull { name ->
            FSBReplacer::class.java.classLoader.getResourceAsStream("FSB/$name.txt")?.bufferedReader(Charsets.UTF_8)?.use {
                it.readLines()
            }
        } ?: return emptyList()

        return parseRules(lines)
    }

    private fun parseRules(rawRules: List<String>): List<Rule> {
        return rawRules.mapIndexedNotNull { index, rawLine ->
            parseRule(rawLine, index)
        }.sortedWith(
            compareBy<Rule> { it.type.priority }
                .thenByDescending { it.pattern.length }
                .thenBy { it.sourceOrder }
        )
    }

    private fun ruleFileNames(language: Language): List<String> {
        val code = language.code.replace('-', '_').uppercase()
        return listOf(code, language.code.uppercase(), language.name)
            .distinct()
    }

    private fun parseRule(rawLine: String, index: Int): Rule? {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) {
            return null
        }

        val separator = line.indexOf('=')
        if (separator < 0) {
            return null
        }

        val pattern = cleanToken(line.substring(0, separator))
            .replace("\"", "")
            .replace("'", "")
        val replacement = cleanToken(line.substring(separator + 1))
        if (pattern.isBlank() || replacement.isBlank() || !pattern.contains('*')) {
            return null
        }

        val star = starRun.find(pattern) ?: return null
        val type = getRuleType(pattern)
        val regex = buildRegex(pattern)
        return Rule(
            pattern = pattern,
            type = type,
            replacement = replacement,
            sourceOrder = index,
            regex = regex,
            starGroupIndex = if (type == RuleType.CONTEXT) starGroupIndex(pattern, star.range.first) else null
        )
    }

    private fun getRuleType(pattern: String): RuleType {
        val trimmed = pattern.trim()
        return when {
            Regex("^\\*+$").matches(trimmed) -> RuleType.SOLO
            fullPatternCharacters.matches(trimmed) -> RuleType.FULL
            else -> RuleType.CONTEXT
        }
    }

    private fun buildRegex(pattern: String): Regex {
        val builder = StringBuilder()
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            when {
                char.isWhitespace() -> {
                    while (index < pattern.length && pattern[index].isWhitespace()) {
                        index++
                    }
                    builder.append("\\s+")
                    continue
                }
                char == '*' -> {
                    val start = index
                    while (index < pattern.length && pattern[index] == '*') {
                        index++
                    }
                    builder.append("(\\*{").append(index - start).append("})")
                    continue
                }
                else -> builder.append(Regex.escape(char.toString()))
            }
            index++
        }

        return Regex("(?<!$wordBoundary)$builder(?!$wordBoundary)", setOf(RegexOption.IGNORE_CASE))
    }

    private fun starGroupIndex(pattern: String, starStart: Int): Int {
        var groupIndex = 1
        var index = 0
        while (index < starStart) {
            if (pattern[index] == '*') {
                groupIndex++
                while (index < pattern.length && pattern[index] == '*') {
                    index++
                }
                continue
            }
            index++
        }
        return groupIndex
    }

    private fun cleanToken(value: String): String {
        var cleaned = value.trim()
        while (cleaned.length >= 2 && ((cleaned.first() == '"' && cleaned.last() == '"') || (cleaned.first() == '\'' && cleaned.last() == '\''))) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        return cleaned
    }

    private data class Rule(
        val pattern: String,
        val type: RuleType,
        val replacement: String,
        val sourceOrder: Int,
        val regex: Regex,
        val starGroupIndex: Int?
    ) {
        fun findMatches(text: String): List<RuleMatch> {
            return regex.findAll(text).mapNotNull { match ->
                val starGroup = starGroupIndex?.let { match.groups[it] }?.range
                RuleMatch(
                    rule = this,
                    start = match.range.first,
                    end = match.range.last + 1,
                    starStart = starGroup?.first,
                    starEnd = starGroup?.last?.plus(1)
                )
            }.toList()
        }
    }

    private data class RuleMatch(
        val rule: Rule,
        val start: Int,
        val end: Int,
        val starStart: Int?,
        val starEnd: Int?
    ) {
        fun replacementText(original: String): String {
            if (rule.type != RuleType.CONTEXT) {
                return rule.replacement
            }

            val censoredStart = starStart ?: return original.substring(start, end)
            val censoredEnd = starEnd ?: return original.substring(start, end)
            return buildString {
                append(original, start, censoredStart)
                append(rule.replacement)
                append(original, censoredEnd, end)
            }
        }
    }

    private enum class RuleType(val priority: Int) {
        FULL(0),
        CONTEXT(1),
        SOLO(2)
    }
}

package com.ventus.apisearch.parsing

/**
 * 将 JavaDoc / KDoc 原始文本清洗为适合展示和搜索的单段文本。
 */
object DocCommentCleaner {

    private const val MAX_LENGTH = 2000
    private val INLINE_TAG = Regex("\\{@\\w+\\s+([^}]*)}")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val body = raw
            .removePrefix("/**")
            .removePrefix("/*")
            .removeSuffix("*/")
        return body.lineSequence()
            .map { line -> line.trim().removePrefix("*").trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith("@") }
            .joinToString(" ")
            .replace(INLINE_TAG, "$1")
            .trim()
            .take(MAX_LENGTH)
    }
}

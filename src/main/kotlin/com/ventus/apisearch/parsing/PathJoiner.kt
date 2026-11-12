package com.ventus.apisearch.parsing

/**
 * URL 路径拼接与规范化。
 *
 * 规则：按 `/` 拆分所有片段，丢弃空片段，统一以 `/` 开头且不保留尾部 `/`。
 */
object PathJoiner {

    fun join(vararg parts: String?): String {
        val segments = parts.asSequence()
            .filterNotNull()
            .flatMap { it.replace('\\', '/').split('/').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    }

    /**
     * 类级路径 × 方法级路径 的所有组合，去重后返回。
     */
    fun combine(classPaths: List<String?>, methodPaths: List<String?>): List<String> {
        val classParts = classPaths.ifEmpty { listOf(null) }
        val methodParts = methodPaths.ifEmpty { listOf(null) }
        return classParts.asSequence()
            .flatMap { cp -> methodParts.asSequence().map { mp -> join(cp, mp) } }
            .distinct()
            .toList()
    }
}

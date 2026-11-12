package com.ventus.apisearch.index

import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.SearchResult

/**
 * 在内存中的接口列表上执行过滤与排序，避免每次按键都读取索引。
 */
object EndpointSearchService {

    /**
     * @param moduleFilter 侧边栏模块下拉的精确过滤（null 表示全部）
     * @param methodFilter 侧边栏 HTTP 方法下拉的过滤（null 表示全部）
     */
    fun filter(
        items: List<SearchResult>,
        rawQuery: String,
        moduleFilter: String? = null,
        methodFilter: String? = null,
    ): List<SearchResult> {
        val tokens = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val moduleTokens = tokens
            .filter { it.startsWith("@") && it.length > 1 }
            .map { it.substring(1).lowercase() }
        val textTokens = tokens.filterNot { it.startsWith("@") }

        return items.asSequence()
            .filter { item ->
                if (!moduleFilter.isNullOrBlank() && !item.moduleName.equals(moduleFilter, true)) {
                    return@filter false
                }
                if (moduleTokens.isNotEmpty() &&
                    moduleTokens.none { item.moduleName.lowercase().contains(it) }
                ) {
                    return@filter false
                }
                if (!methodFilter.isNullOrBlank() && !matchesMethod(item.endpoint, methodFilter)) {
                    return@filter false
                }
                true
            }
            .mapNotNull { item ->
                val score = score(item.endpoint, textTokens) ?: return@mapNotNull null
                item.copy(score = score)
            }
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.endpoint.path })
            .distinctBy { "${it.endpoint.filePath}#${it.endpoint.offset}#${it.endpoint.path}" }
            .toList()
    }

    /**
     * 统计各 HTTP 方法的接口数量（用于侧边栏下拉框的计数）。
     */
    fun methodCounts(items: List<SearchResult>, moduleFilter: String? = null): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        var all = 0
        for (item in items) {
            if (!moduleFilter.isNullOrBlank() && !item.moduleName.equals(moduleFilter, true)) continue
            all++
            for (method in item.endpoint.httpMethod.split(',').map { it.trim() }) {
                if (method.isEmpty() || method == "ANY") continue
                counts[method] = (counts[method] ?: 0) + 1
            }
        }
        counts["ALL"] = all
        return counts
    }

    fun moduleNames(items: List<SearchResult>): List<String> =
        items.map { it.moduleName }.filter { it.isNotBlank() }.distinct().sorted()

    fun moduleCounts(items: List<SearchResult>): Map<String, Int> =
        items.filter { it.moduleName.isNotBlank() }.groupingBy { it.moduleName }.eachCount()

    private fun matchesMethod(endpoint: ApiEndpoint, method: String): Boolean =
        endpoint.httpMethod.split(',').any { it.trim().equals(method, ignoreCase = true) }

    private fun score(endpoint: ApiEndpoint, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        var total = 0
        for (token in tokens) {
            val best = listOfNotNull(
                pathScore(endpoint.path, token),
                fieldScore(endpoint.methodName, token, 40, 30),
                fieldScore(endpoint.className, token, 25, 15),
                fieldScore(endpoint.comment, token, 20, 10),
            ).maxOrNull() ?: return null
            total += best
        }
        return total
    }

    private fun pathScore(path: String, token: String): Int? {
        if (!pathMatches(path, token)) return null
        val normalized = "/" + token.trim('/')
        return if (path.lowercase().startsWith(normalized.lowercase())) 100 else 60
    }

    /**
     * 支持路径变量通配：输入 `contract/construct/delete/13241`
     * 可以匹配 `/contract/construct/delete/{contractId}`。
     */
    fun pathMatches(path: String, token: String): Boolean {
        val needle = token.trim()
        if (needle.isEmpty()) return true
        if (path.contains(needle, ignoreCase = true)) return true

        val pathSegments = path.trim('/').split('/').filter { it.isNotEmpty() }
        val tokenSegments = needle.trim('/').split('/').filter { it.isNotEmpty() }
        if (tokenSegments.isEmpty()) return true

        // 单段输入：只有数字或变量占位符才允许匹配路径变量（如 13241 -> {contractId}）
        if (tokenSegments.size == 1) {
            return isValueLike(tokenSegments[0]) && pathSegments.any { isPathVariable(it) }
        }

        if (tokenSegments.size > pathSegments.size) return false

        // 多段输入：逐段比较；普通字面量段必须完全相等，
        // 路径变量只能匹配「最后一段」或数字/变量占位符，避免误匹配。
        val tail = pathSegments.takeLast(tokenSegments.size)
        return tokenSegments.indices.all { index ->
            val pathSegment = tail[index]
            val tokenSegment = tokenSegments[index]
            val isLast = index == tokenSegments.lastIndex
            pathSegment.equals(tokenSegment, ignoreCase = true) ||
                (isPathVariable(pathSegment) && (isLast || isValueLike(tokenSegment)))
        }
    }

    private fun isValueLike(segment: String): Boolean =
        segment.startsWith("{") || segment.all { it.isDigit() }

    private fun isPathVariable(segment: String): Boolean =
        (segment.startsWith("{") && segment.endsWith("}")) || segment.contains("\${")

    private fun fieldScore(value: String, token: String, prefixWeight: Int, containsWeight: Int): Int? {
        if (value.isEmpty()) return null
        val lowerValue = value.lowercase()
        val lowerToken = token.lowercase()
        return when {
            lowerValue.startsWith(lowerToken) -> prefixWeight
            lowerValue.contains(lowerToken) -> containsWeight
            else -> null
        }
    }
}

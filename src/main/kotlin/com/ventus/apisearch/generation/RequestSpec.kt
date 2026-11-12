package com.ventus.apisearch.generation

/**
 * 从方法参数推导出的请求描述，用于生成 cURL。
 */
data class RequestSpec(
    val bodyJson: String? = null,
    val query: List<Pair<String, String>> = emptyList(),
    val pathVariables: Map<String, String> = emptyMap(),
    val headers: List<Pair<String, String>> = emptyList(),
)

package com.ventus.apisearch.generation

import com.ventus.apisearch.model.ApiEndpoint

/**
 * 生成可导入 Postman 的单行 cURL。
 *
 * 风格：`curl -X METHOD 'url' -H 'Content-Type: application/json' -d '{...}'`
 */
object CurlGenerator {

    private val PATH_VARIABLE = Regex("\\{([^}:]+)(?::[^}]*)?}")

    fun build(endpoint: ApiEndpoint, url: String, spec: RequestSpec): String {
        val method = endpoint.httpMethod
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != "ANY" }
            ?: "GET"

        val withVariables = applyPathVariables(url, spec.pathVariables)
        val finalUrl = appendQuery(withVariables, spec.query)

        val builder = StringBuilder("curl -X ")
            .append(method)
            .append(' ')
            .append(singleQuote(finalUrl))

        if (spec.bodyJson != null) {
            builder.append(" -H ")
                .append(singleQuote("Content-Type: application/json"))
                .append(" -d ")
                .append(singleQuote(spec.bodyJson))
        }

        for ((name, value) in spec.headers) {
            builder.append(" -H ").append(singleQuote("$name: $value"))
        }

        return builder.toString()
    }

    fun applyPathVariables(url: String, variables: Map<String, String>): String =
        PATH_VARIABLE.replace(url) { match -> variables[match.groupValues[1]] ?: "1" }

    fun appendQuery(url: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return url
        val query = params.joinToString("&") { (name, value) -> "$name=$value" }
        return if (url.contains('?')) "$url&$query" else "$url?$query"
    }

    fun singleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}

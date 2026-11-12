package com.ventus.apisearch.parsing

/**
 * 解析 Spring 配置中的 `${NAME:default}` 占位符。
 *
 * 查找顺序：环境变量 → 系统属性 → 当前配置文件内的 key → 默认值。
 */
object PlaceholderResolver {

    private val PLACEHOLDER = Regex("\\$\\{([^}:]+)(?::([^}]*))?}")
    private const val MAX_DEPTH = 8

    fun resolve(value: String, props: Map<String, String>): String = resolve(
        value,
        props,
        { key -> System.getenv(key) },
        { key -> System.getProperty(key) },
    )

    fun resolve(
        value: String,
        props: Map<String, String>,
        environment: (String) -> String?,
        systemProperties: (String) -> String?,
        depth: Int = 0,
    ): String {
        if (depth >= MAX_DEPTH || !value.contains("\${")) return value

        val replaced = PLACEHOLDER.replace(value) { match ->
            val key = match.groupValues[1]
            val defaultValue = match.groupValues.getOrElse(2) { "" }
            environment(key) ?: systemProperties(key) ?: props[key] ?: defaultValue
        }

        return if (replaced != value && replaced.contains("\${")) {
            resolve(replaced, props, environment, systemProperties, depth + 1)
        } else {
            replaced
        }
    }
}

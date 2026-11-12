package com.ventus.apisearch.index

import com.intellij.openapi.vfs.VirtualFile
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import com.ventus.apisearch.parsing.PathJoiner
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * 从 Spring Cloud Gateway 的 application.yml 中解析路由。
 */
object GatewayEndpointCollector {

    private val PATH_PREDICATE = Regex("(?i)^\\s*Path\\s*=\\s*(.+)$")

    fun collect(file: VirtualFile, text: String): List<ApiEndpoint> {
        if (!text.contains("gateway", ignoreCase = true)) return emptyList()

        val root = runCatching { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text) }
            .getOrNull() as? Map<*, *> ?: return emptyList()
        val routes = findRoutes(root) ?: return emptyList()

        return routes.mapNotNull { it as? Map<*, *> }.flatMap { route ->
            val id = route["id"]?.toString().orEmpty()
            val uri = route["uri"]?.toString()
            val predicates = route["predicates"] as? List<*> ?: emptyList<Any>()
            val filters = route["filters"] as? List<*> ?: emptyList<Any>()
            val stripPrefix = filters.firstOrNull {
                it?.toString()?.startsWith("StripPrefix=", ignoreCase = true) == true
            }?.toString()

            val paths = extractPaths(predicates)
            paths.map { path ->
                ApiEndpoint(
                    framework = Framework.GATEWAY,
                    httpMethod = "ANY",
                    path = PathJoiner.join(path),
                    className = "gateway",
                    methodName = id.ifEmpty { "route" },
                    comment = buildString {
                        if (!uri.isNullOrBlank()) append("uri=").append(uri)
                        if (stripPrefix != null) {
                            if (isNotEmpty()) append(" ")
                            append(stripPrefix)
                        }
                    },
                    filePath = file.path,
                    offset = 0,
                    gatewayTarget = uri,
                )
            }
        }
    }

    private fun findRoutes(root: Map<*, *>): List<*>? {
        val spring = root["spring"] as? Map<*, *> ?: return null
        val cloud = spring["cloud"] as? Map<*, *> ?: return null
        val gateway = cloud["gateway"] as? Map<*, *> ?: return null
        return gateway["routes"] as? List<*>
    }

    private fun extractPaths(predicates: List<*>): List<String> {
        for (predicate in predicates) {
            val text = predicate?.toString() ?: continue
            val match = PATH_PREDICATE.find(text) ?: continue
            return match.groupValues[1]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return emptyList()
    }
}

package com.ventus.apisearch.generation

import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import com.ventus.apisearch.parsing.PathJoiner
import com.ventus.apisearch.parsing.ResolvedConfig
import java.net.URI

/**
 * 构建完整请求 URL。
 *
 * Spring / JAX-RS / Gateway：`{host}:{port}{context-path}{path}`
 * Feign：优先使用 `@FeignClient(url)`，否则使用服务名作为 host。
 */
object UrlGenerator {

    fun fullUrl(endpoint: ApiEndpoint, config: ResolvedConfig, hostSetting: String): String =
        when (endpoint.framework) {
            Framework.FEIGN -> feignUrl(endpoint)
            else -> joinBase(
                hostBase(hostSetting, config.port),
                PathJoiner.join(config.contextPath, endpoint.path),
            )
        }

    fun hostBase(hostSetting: String, port: Int?): String {
        val host = hostSetting.trim().ifEmpty { "localhost" }
        if (host.contains("://")) {
            val uri = runCatching { URI(host) }.getOrNull()
            if (uri != null && !uri.host.isNullOrBlank()) {
                val scheme = uri.scheme ?: "http"
                val effectivePort = if (uri.port > 0) uri.port else port
                val portPart = if (effectivePort != null) ":$effectivePort" else ""
                val pathPart = uri.path.orEmpty().trimEnd('/')
                return "$scheme://${uri.host}$portPart$pathPart"
            }
        }
        val portPart = if (port != null) ":$port" else ""
        return "http://$host$portPart"
    }

    private fun feignUrl(endpoint: ApiEndpoint): String {
        val base = endpoint.feignBaseUrl?.takeIf { it.isNotBlank() }
            ?: "http://${endpoint.feignServiceName?.takeIf { it.isNotBlank() } ?: "localhost"}"
        return joinBase(base, endpoint.path)
    }

    private fun joinBase(base: String, path: String): String =
        base.trimEnd('/') + "/" + path.trimStart('/')
}

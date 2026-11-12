package com.ventus.apisearch.model

/**
 * 支持的接口声明框架。
 */
enum class Framework(val displayName: String) {
    SPRING("Spring"),
    FEIGN("Feign"),
    JAX_RS("JAX-RS"),
    GATEWAY("Gateway"),
}

package com.ventus.apisearch.model

/**
 * 一个可搜索的 HTTP 接口。
 *
 * 每个路径独立成一条记录；同一方法映射多个路径时会产生多条 [ApiEndpoint]。
 */
data class ApiEndpoint(
    val framework: Framework,
    /** 单个方法或逗号分隔的多个方法；无法确定时为 ANY。 */
    val httpMethod: String,
    /** 已拼接完整（类级 + 方法级）的路径，不含 context-path 与 host。 */
    val path: String,
    val className: String,
    val methodName: String,
    /** 清洗后的 JavaDoc / KDoc 文本。 */
    val comment: String,
    val filePath: String,
    /** 方法（或 YAML 中路由）在文件内的偏移，用于跳转。 */
    val offset: Int,
    /** Feign 客户端声明的直连地址（@FeignClient(url = ...)）。 */
    val feignBaseUrl: String? = null,
    /** Feign 客户端服务名（@FeignClient(name = ...)）。 */
    val feignServiceName: String? = null,
    /** Gateway 路由目标（uri）。 */
    val gatewayTarget: String? = null,
)

/**
 * 搜索结果：接口 + 所属模块名 + 匹配得分。
 */
data class SearchResult(
    val endpoint: ApiEndpoint,
    val moduleName: String,
    val score: Int,
)

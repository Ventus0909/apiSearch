package com.ventus.apisearch.index

import com.intellij.psi.util.PsiTreeUtil
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import com.ventus.apisearch.parsing.AnnotationValues
import com.ventus.apisearch.parsing.DocCommentCleaner
import com.ventus.apisearch.parsing.PathJoiner
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 解析 Kotlin Controller / Feign 接口 / JAX-RS 资源中的接口定义。
 */
object KotlinEndpointCollector {

    private val SPRING_MAPPINGS = mapOf(
        "GetMapping" to "GET",
        "PostMapping" to "POST",
        "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE",
        "PatchMapping" to "PATCH",
    )
    private val JAXRS_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    fun collect(file: KtFile): List<ApiEndpoint> =
        PsiTreeUtil.collectElementsOfType(file, KtClass::class.java)
            .flatMap { collectClass(it) }

    fun collectClass(ktClass: KtClass): List<ApiEndpoint> {
        val annotations = ktClass.annotationEntries
        val feign = annotations.firstOrNull { AnnotationValues.simpleName(it) == "FeignClient" }
        val feignBaseUrl = feign?.let { AnnotationValues.kotlinString(it, "url") }
        val feignServiceName = feign?.let {
            AnnotationValues.kotlinString(it, "name") ?: AnnotationValues.kotlinString(it, "value")
        }

        val springClassPaths = annotations
            .firstOrNull { AnnotationValues.simpleName(it) == "RequestMapping" }
            ?.let { AnnotationValues.kotlinStrings(it) }
            ?: emptyList()

        val jaxrsClassPaths = annotations
            .filter { AnnotationValues.simpleName(it) == "Path" }
            .flatMap { AnnotationValues.kotlinStrings(it, setOf("value")) }

        return ktClass.declarations
            .filterIsInstance<KtNamedFunction>()
            .flatMap {
                collectFunction(it, springClassPaths, jaxrsClassPaths, feignBaseUrl, feignServiceName)
            }
    }

    fun collectFunction(
        function: KtNamedFunction,
        springClassPaths: List<String> = emptyList(),
        jaxrsClassPaths: List<String> = emptyList(),
        feignBaseUrl: String? = null,
        feignServiceName: String? = null,
    ): List<ApiEndpoint> {
        val annotations = function.annotationEntries
        val springMapping = annotations.firstOrNull {
            val name = AnnotationValues.simpleName(it)
            name in SPRING_MAPPINGS || name == "RequestMapping"
        }
        val jaxrsMethod = annotations.firstOrNull {
            AnnotationValues.simpleName(it) in JAXRS_METHODS
        }

        return when {
            springMapping != null -> springEndpoints(
                function, springMapping, springClassPaths, feignBaseUrl, feignServiceName
            )
            jaxrsMethod != null -> jaxrsEndpoints(function, jaxrsMethod, jaxrsClassPaths)
            else -> emptyList()
        }
    }

    private fun springEndpoints(
        function: KtNamedFunction,
        annotation: KtAnnotationEntry,
        classPaths: List<String>,
        feignBaseUrl: String?,
        feignServiceName: String?,
    ): List<ApiEndpoint> {
        val simpleName = AnnotationValues.simpleName(annotation)
        val httpMethods = if (simpleName in SPRING_MAPPINGS) {
            listOf(SPRING_MAPPINGS.getValue(simpleName))
        } else {
            AnnotationValues.kotlinHttpMethods(annotation).ifEmpty { listOf("ANY") }
        }
        val methodPaths = AnnotationValues.kotlinStrings(annotation)
        val paths = PathJoiner.combine(classPaths, methodPaths)
        val framework = if (feignBaseUrl != null || feignServiceName != null) Framework.FEIGN else Framework.SPRING
        val httpMethod = httpMethods.joinToString(",")
        return paths.map {
            endpoint(function, framework, httpMethod, it, feignBaseUrl, feignServiceName)
        }
    }

    private fun jaxrsEndpoints(
        function: KtNamedFunction,
        annotation: KtAnnotationEntry,
        classPaths: List<String>,
    ): List<ApiEndpoint> {
        val methodPaths = function.annotationEntries
            .filter { AnnotationValues.simpleName(it) == "Path" }
            .flatMap { AnnotationValues.kotlinStrings(it, setOf("value")) }
        val paths = PathJoiner.combine(classPaths, methodPaths)
        val httpMethod = AnnotationValues.simpleName(annotation)
        return paths.map {
            endpoint(function, Framework.JAX_RS, httpMethod, it, null, null)
        }
    }

    /**
     * 优先 KDoc；没有时回退到 Swagger 2 / OpenAPI 3 的接口描述。
     */
    private fun commentOf(function: KtNamedFunction): String {
        val doc = DocCommentCleaner.clean(function.docComment?.text)
        if (doc.isNotBlank()) return doc

        for (annotation in function.annotationEntries) {
            when (AnnotationValues.simpleName(annotation)) {
                "ApiOperation" -> {
                    val value = AnnotationValues.kotlinString(annotation, "value")
                        ?: AnnotationValues.kotlinString(annotation, "notes")
                    if (!value.isNullOrBlank()) return value.trim()
                }
                "Operation" -> {
                    val value = AnnotationValues.kotlinString(annotation, "summary")
                        ?: AnnotationValues.kotlinString(annotation, "description")
                    if (!value.isNullOrBlank()) return value.trim()
                }
            }
        }
        return ""
    }

    private fun endpoint(
        function: KtNamedFunction,
        framework: Framework,
        httpMethod: String,
        path: String,
        feignBaseUrl: String?,
        feignServiceName: String?,
    ): ApiEndpoint {
        val containingClass = PsiTreeUtil.getParentOfType(function, KtClass::class.java)
        return ApiEndpoint(
            framework = framework,
            httpMethod = httpMethod,
            path = path,
            className = containingClass?.fqName?.asString() ?: containingClass?.name ?: "",
            methodName = function.name ?: "",
            comment = commentOf(function),
            filePath = function.containingKtFile.virtualFile?.path ?: "",
            offset = function.nameIdentifier?.textRange?.startOffset ?: function.textRange.startOffset,
            feignBaseUrl = feignBaseUrl,
            feignServiceName = feignServiceName,
        )
    }
}

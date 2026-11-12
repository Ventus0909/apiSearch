package com.ventus.apisearch.index

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import com.ventus.apisearch.parsing.AnnotationValues
import com.ventus.apisearch.parsing.DocCommentCleaner
import com.ventus.apisearch.parsing.PathJoiner

/**
 * 解析 Java Controller / Feign 接口 / JAX-RS 资源中的接口定义。
 */
object JavaEndpointCollector {

    private val SPRING_MAPPINGS = mapOf(
        "GetMapping" to "GET",
        "PostMapping" to "POST",
        "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE",
        "PatchMapping" to "PATCH",
    )
    private val JAXRS_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    fun collect(file: PsiJavaFile): List<ApiEndpoint> =
        PsiTreeUtil.collectElementsOfType(file, PsiClass::class.java)
            .flatMap { collectClass(it) }

    fun collectClass(psiClass: PsiClass): List<ApiEndpoint> {
        val annotations = psiClass.annotations
        val feign = annotations.firstOrNull { AnnotationValues.simpleName(it) == "FeignClient" }
        val feignBaseUrl = feign?.let { AnnotationValues.javaString(it, "url") }
        val feignServiceName = feign?.let {
            AnnotationValues.javaString(it, "name") ?: AnnotationValues.javaString(it, "value")
        }

        val springClassPaths = annotations
            .firstOrNull { AnnotationValues.simpleName(it) == "RequestMapping" }
            ?.let { AnnotationValues.javaStrings(it) }
            ?: emptyList()

        val jaxrsClassPaths = annotations
            .filter { AnnotationValues.simpleName(it) == "Path" }
            .flatMap { AnnotationValues.javaStrings(it, setOf("value")) }

        return psiClass.methods.flatMap {
            collectMethod(it, springClassPaths, jaxrsClassPaths, feignBaseUrl, feignServiceName)
        }
    }

    fun collectMethod(
        method: PsiMethod,
        springClassPaths: List<String> = emptyList(),
        jaxrsClassPaths: List<String> = emptyList(),
        feignBaseUrl: String? = null,
        feignServiceName: String? = null,
    ): List<ApiEndpoint> {
        val annotations = method.annotations
        val springMapping = annotations.firstOrNull {
            val name = AnnotationValues.simpleName(it)
            name in SPRING_MAPPINGS || name == "RequestMapping"
        }
        val jaxrsMethod = annotations.firstOrNull {
            AnnotationValues.simpleName(it) in JAXRS_METHODS
        }

        return when {
            springMapping != null -> springEndpoints(
                method, springMapping, springClassPaths, feignBaseUrl, feignServiceName
            )
            jaxrsMethod != null -> jaxrsEndpoints(method, jaxrsMethod, jaxrsClassPaths)
            else -> emptyList()
        }
    }

    private fun springEndpoints(
        method: PsiMethod,
        annotation: PsiAnnotation,
        classPaths: List<String>,
        feignBaseUrl: String?,
        feignServiceName: String?,
    ): List<ApiEndpoint> {
        val simpleName = AnnotationValues.simpleName(annotation)
        val httpMethods = if (simpleName in SPRING_MAPPINGS) {
            listOf(SPRING_MAPPINGS.getValue(simpleName))
        } else {
            AnnotationValues.javaHttpMethods(annotation).ifEmpty { listOf("ANY") }
        }
        val methodPaths = AnnotationValues.javaStrings(annotation)
        val paths = PathJoiner.combine(classPaths, methodPaths)
        val framework = if (feignBaseUrl != null || feignServiceName != null) Framework.FEIGN else Framework.SPRING
        val httpMethod = httpMethods.joinToString(",")
        return paths.map {
            endpoint(method, framework, httpMethod, it, feignBaseUrl, feignServiceName)
        }
    }

    private fun jaxrsEndpoints(
        method: PsiMethod,
        annotation: PsiAnnotation,
        classPaths: List<String>,
    ): List<ApiEndpoint> {
        val methodPaths = method.annotations
            .filter { AnnotationValues.simpleName(it) == "Path" }
            .flatMap { AnnotationValues.javaStrings(it, setOf("value")) }
        val paths = PathJoiner.combine(classPaths, methodPaths)
        val httpMethod = AnnotationValues.simpleName(annotation)
        return paths.map {
            endpoint(method, Framework.JAX_RS, httpMethod, it, null, null)
        }
    }

    /**
     * 优先 JavaDoc；没有时回退到 Swagger 2 / OpenAPI 3 的接口描述。
     */
    private fun commentOf(method: PsiMethod): String {
        val doc = DocCommentCleaner.clean(method.docComment?.text)
        if (doc.isNotBlank()) return doc

        for (annotation in method.annotations) {
            when (AnnotationValues.simpleName(annotation)) {
                "ApiOperation" -> {
                    val value = AnnotationValues.javaString(annotation, "value")
                        ?: AnnotationValues.javaString(annotation, "notes")
                    if (!value.isNullOrBlank()) return value.trim()
                }
                "Operation" -> {
                    val value = AnnotationValues.javaString(annotation, "summary")
                        ?: AnnotationValues.javaString(annotation, "description")
                    if (!value.isNullOrBlank()) return value.trim()
                }
            }
        }
        return ""
    }

    private fun endpoint(
        method: PsiMethod,
        framework: Framework,
        httpMethod: String,
        path: String,
        feignBaseUrl: String?,
        feignServiceName: String?,
    ): ApiEndpoint {
        val containingClass = method.containingClass
        return ApiEndpoint(
            framework = framework,
            httpMethod = httpMethod,
            path = path,
            className = containingClass?.qualifiedName ?: containingClass?.name ?: "",
            methodName = method.name,
            comment = commentOf(method),
            filePath = method.containingFile?.virtualFile?.path ?: "",
            offset = method.nameIdentifier?.textRange?.startOffset ?: method.textRange.startOffset,
            feignBaseUrl = feignBaseUrl,
            feignServiceName = feignServiceName,
        )
    }
}

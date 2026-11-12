package com.ventus.apisearch.generation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.ventus.apisearch.parsing.AnnotationValues
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * 从方法参数提取 cURL 需要的 body / query / path 变量。
 */
object RequestSpecBuilder {

    fun forJavaMethod(project: Project, method: PsiMethod): RequestSpec {
        val jsonGenerator = JsonExampleGenerator(project)
        var body: String? = null
        val query = mutableListOf<Pair<String, String>>()
        val pathVariables = mutableMapOf<String, String>()

        for (parameter in method.parameterList.parameters) {
            val annotations = parameter.annotations.map { AnnotationValues.simpleName(it) to it }

            if (annotations.any { it.first == "RequestBody" }) {
                body = jsonGenerator.forJavaType(parameter.type)
            }

            val requestParam = annotations.firstOrNull { it.first == "RequestParam" }?.second
            if (requestParam != null) {
                val name = AnnotationValues.javaString(requestParam, "value")
                    ?: AnnotationValues.javaString(requestParam, "name")
                    ?: parameter.name ?: "param"
                val defaultValue = AnnotationValues.javaString(requestParam, "defaultValue")
                query += name to (defaultValue ?: typeDefaultValue(parameter.type))
            }

            val pathVariable = annotations.firstOrNull { it.first == "PathVariable" }?.second
            if (pathVariable != null) {
                val name = AnnotationValues.javaString(pathVariable, "value")
                    ?: AnnotationValues.javaString(pathVariable, "name")
                    ?: parameter.name ?: "id"
                pathVariables[name] = typeDefaultValue(parameter.type)
            }
        }

        return RequestSpec(body, query, pathVariables)
    }

    fun forKotlinFunction(project: Project, function: KtNamedFunction): RequestSpec {
        val jsonGenerator = JsonExampleGenerator(project)
        var body: String? = null
        val query = mutableListOf<Pair<String, String>>()
        val pathVariables = mutableMapOf<String, String>()

        for (parameter in function.valueParameters) {
            val annotations = parameter.annotationEntries.map { AnnotationValues.simpleName(it) to it }

            if (annotations.any { it.first == "RequestBody" }) {
                body = jsonGenerator.forKotlinTypeReference(parameter.typeReference)
            }

            val requestParam = annotations.firstOrNull { it.first == "RequestParam" }?.second
            if (requestParam != null) {
                val name = AnnotationValues.kotlinString(requestParam, "value", "name")
                    ?: parameter.name ?: "param"
                val defaultValue = AnnotationValues.kotlinString(requestParam, "defaultValue")
                query += name to (defaultValue ?: typeDefaultValue(parameter.typeReference))
            }

            val pathVariable = annotations.firstOrNull { it.first == "PathVariable" }?.second
            if (pathVariable != null) {
                val name = AnnotationValues.kotlinString(pathVariable, "value", "name")
                    ?: parameter.name ?: "id"
                pathVariables[name] = typeDefaultValue(parameter.typeReference)
            }
        }

        return RequestSpec(body, query, pathVariables)
    }

    private fun typeDefaultValue(type: PsiType?): String = when (type?.canonicalText) {
        "boolean", "java.lang.Boolean" -> "false"
        "char", "java.lang.Character" -> "a"
        else -> "1"
    }

    private fun typeDefaultValue(typeReference: KtTypeReference?): String =
        when (typeReference?.text?.trim()?.removeSuffix("?")) {
            "Boolean" -> "false"
            "Char" -> "a"
            else -> "1"
        }
}

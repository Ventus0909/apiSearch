package com.ventus.apisearch.parsing

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * 统一读取 Java / Kotlin 注解中的简单值。
 *
 * 只读取字面量与字面量数组，不解析常量引用，避免在索引线程里触发额外索引。
 */
object AnnotationValues {

    val HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE")

    private const val VALUE = "value"
    private const val PATH = "path"
    private const val METHOD = "method"
    private const val NAME = "name"

    // ---------------------------------------------------------------- Java

    fun simpleName(annotation: PsiAnnotation): String =
        annotation.nameReferenceElement?.referenceName
            ?: annotation.qualifiedName?.substringAfterLast('.')
            ?: ""

    fun javaStrings(annotation: PsiAnnotation, names: Set<String> = setOf(VALUE, PATH)): List<String> {
        val result = mutableListOf<String>()
        for (attribute in annotation.parameterList.attributes) {
            val name = attribute.name ?: VALUE
            if (name !in names) continue
            collectJava(attribute.value, result)
        }
        return result
    }

    fun javaString(annotation: PsiAnnotation, vararg names: String): String? =
        javaStrings(annotation, names.toSet()).firstOrNull()

    fun javaHttpMethods(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue(METHOD) ?: return emptyList()
        val text = value.text
        return HTTP_METHODS.filter { Regex("\\b$it\\b").containsMatchIn(text) }
    }

    private fun collectJava(value: PsiAnnotationMemberValue?, out: MutableList<String>) {
        when (value) {
            is PsiLiteralExpression -> (value.value as? String)?.let(out::add)
            is PsiArrayInitializerMemberValue -> value.initializers.forEach { collectJava(it, out) }
            else -> {
                val text = value?.text
                if (text != null && text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    out.add(text.substring(1, text.length - 1))
                }
            }
        }
    }

    // ---------------------------------------------------------------- Kotlin

    fun simpleName(entry: KtAnnotationEntry): String =
        entry.shortName?.asString()
            ?: entry.typeReference?.text?.substringAfterLast('.')?.trim()
            ?: ""

    fun kotlinStrings(entry: KtAnnotationEntry, names: Set<String> = setOf(VALUE, PATH)): List<String> {
        val result = mutableListOf<String>()
        for (argument in entry.valueArguments) {
            val name = argument.getArgumentName()?.asName?.asString() ?: VALUE
            if (name !in names) continue
            collectKotlin(argument.getArgumentExpression(), result)
        }
        return result
    }

    fun kotlinString(entry: KtAnnotationEntry, vararg names: String): String? =
        kotlinStrings(entry, names.toSet()).firstOrNull()

    fun kotlinHttpMethods(entry: KtAnnotationEntry): List<String> {
        val argument = entry.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() == METHOD
        } ?: return emptyList()
        val text = argument.getArgumentExpression()?.text ?: return emptyList()
        return HTTP_METHODS.filter { Regex("\\b$it\\b").containsMatchIn(text) }
    }

    private fun collectKotlin(expression: KtExpression?, out: MutableList<String>) {
        when (expression) {
            is KtStringTemplateExpression -> {
                if (expression.entries.all { it is KtLiteralStringTemplateEntry }) {
                    out.add(expression.entries.joinToString("") { it.text })
                }
            }
            is KtCollectionLiteralExpression -> expression.getInnerExpressions().forEach { collectKotlin(it, out) }
            is KtParenthesizedExpression -> collectKotlin(expression.expression, out)
        }
    }
}

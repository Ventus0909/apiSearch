package com.ventus.apisearch.generation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 根据 @RequestBody 的类型递归生成一行合法 JSON。
 *
 * 默认值规则：String→""、Boolean→false、数字→0、日期→当前时间、
 * 集合→一个元素、Map→{}、循环引用→{}。
 */
class JsonExampleGenerator(private val project: Project) {

    fun forJavaType(type: PsiType?): String = render(toJson(type, mutableSetOf()))

    fun forKotlinTypeReference(typeReference: KtTypeReference?): String =
        render(toJsonFromKotlinText(typeReference?.text ?: "", mutableSetOf()))

    // ------------------------------------------------------------ Java

    private fun toJson(type: PsiType?, visited: MutableSet<String>): JsonValue {
        if (type == null) return JsonValue.Str("")

        when (type.canonicalText) {
            "boolean" -> return JsonValue.Bool(false)
            "byte", "short", "int", "long", "float", "double" -> return JsonValue.Num("0")
            "char" -> return JsonValue.Str("")
        }

        if (type is PsiArrayType) {
            return JsonValue.Arr(listOf(toJson(type.componentType, visited)))
        }

        if (type !is PsiClassType) {
            return if (type is PsiPrimitiveType) JsonValue.Num("0") else JsonValue.Str("")
        }

        val resolved = type.resolve()
        val qualifiedName = resolved?.qualifiedName ?: ""

        if (isMap(type, resolved)) return JsonValue.Obj(emptyList())

        if (qualifiedName == "java.util.Optional") {
            val inner = type.parameters.firstOrNull()
            return if (inner != null) toJson(inner, visited) else JsonValue.Str("")
        }

        val iterableElement = PsiUtil.extractIterableTypeParameter(type, false)
        if (iterableElement != null) {
            return JsonValue.Arr(listOf(toJson(iterableElement, visited)))
        }

        return when {
            qualifiedName == "java.lang.String" || qualifiedName == "java.lang.CharSequence" ->
                JsonValue.Str("")

            qualifiedName == "java.lang.Boolean" -> JsonValue.Bool(false)

            qualifiedName in NUMBER_TYPES -> JsonValue.Num("0")

            qualifiedName == "java.lang.Character" -> JsonValue.Str("")

            qualifiedName == "java.time.LocalDate" -> JsonValue.Str(LocalDate.now().toString())

            qualifiedName in DATE_TIME_TYPES -> JsonValue.Str(LocalDateTime.now().toString())

            qualifiedName == "java.lang.Object" -> JsonValue.Str("")

            resolved != null && resolved.isEnum ->
                JsonValue.Str(resolved.fields.filterIsInstance<PsiEnumConstant>().firstOrNull()?.name ?: "")

            resolved != null -> forClass(resolved, visited)

            else -> JsonValue.Str("")
        }
    }

    private fun isMap(type: PsiClassType, resolved: PsiClass?): Boolean {
        val qualifiedName = resolved?.qualifiedName ?: ""
        return (qualifiedName.startsWith("java.util.") && qualifiedName.contains("Map")) ||
            type.canonicalText.startsWith("Map<") ||
            type.canonicalText.startsWith("java.util.Map")
    }

    private fun forClass(psiClass: PsiClass, visited: MutableSet<String>): JsonValue {
        val key = psiClass.qualifiedName ?: psiClass.name ?: return JsonValue.Obj(emptyList())
        if (!visited.add(key)) return JsonValue.Obj(emptyList())

        return try {
            val kotlinOrigin = (psiClass as? KtLightClass)?.kotlinOrigin as? KtClass
            val fields = if (kotlinOrigin != null) {
                kotlinFields(kotlinOrigin, visited)
            } else {
                psiClass.allFields
                    .filterNot {
                        it.hasModifierProperty(PsiModifier.STATIC) ||
                            it.hasModifierProperty(PsiModifier.TRANSIENT)
                    }
                    .filterNot { it.name.startsWith("$") }
                    .map { it.name to toJson(it.type, visited) }
            }
            JsonValue.Obj(fields)
        } finally {
            visited.remove(key)
        }
    }

    private fun kotlinFields(ktClass: KtClass, visited: MutableSet<String>): List<Pair<String, JsonValue>> {
        val constructorFields = ktClass.primaryConstructorParameters
            .filter { it.hasValOrVar() }
            .mapNotNull { parameter ->
                val name = parameter.name ?: return@mapNotNull null
                name to toJsonFromKotlinText(parameter.typeReference?.text ?: "", visited)
            }
        val propertyFields = ktClass.declarations
            .filterIsInstance<KtProperty>()
            .filterNot { it.hasDelegate() }
            .mapNotNull { property ->
                val name = property.name ?: return@mapNotNull null
                name to toJsonFromKotlinText(property.typeReference?.text ?: "", visited)
            }
        return (constructorFields + propertyFields).distinctBy { it.first }
    }

    // ------------------------------------------------------------ Kotlin

    private fun toJsonFromKotlinText(raw: String, visited: MutableSet<String>): JsonValue {
        val text = raw.trim().removeSuffix("?").trim()
        if (text.isEmpty()) return JsonValue.Str("")

        if (text.startsWith("Array<") || text.startsWith("List<") || text.startsWith("MutableList<") ||
            text.startsWith("Set<") || text.startsWith("MutableSet<") ||
            text.startsWith("Collection<") || text.startsWith("Iterable<") || text.startsWith("Sequence<")
        ) {
            val inner = text.substringAfter('<').substringBeforeLast('>')
            return JsonValue.Arr(listOf(toJsonFromKotlinText(inner, visited)))
        }

        if (text.startsWith("Map<") || text.startsWith("MutableMap<") || text.startsWith("HashMap<")) {
            return JsonValue.Obj(emptyList())
        }

        val simpleName = text.substringAfterLast('.').substringBefore('<').trim()
        return when (simpleName) {
            "String", "CharSequence", "Char", "UUID", "URI", "URL" -> JsonValue.Str("")
            "Boolean" -> JsonValue.Bool(false)
            "Byte", "Short", "Int", "Long", "Float", "Double",
            "BigDecimal", "BigInteger", "Number" -> JsonValue.Num("0")
            "LocalDate" -> JsonValue.Str(LocalDate.now().toString())
            "LocalDateTime", "Instant", "Date", "Timestamp",
            "OffsetDateTime", "ZonedDateTime", "LocalTime" -> JsonValue.Str(LocalDateTime.now().toString())
            "Any", "Unit", "Nothing" -> JsonValue.Str("")
            else -> {
                val resolved = PsiShortNamesCache.getInstance(project)
                    .getClassesByName(simpleName, GlobalSearchScope.allScope(project))
                    .firstOrNull()
                when {
                    resolved == null -> JsonValue.Str("")
                    resolved.isEnum -> JsonValue.Str(
                        resolved.fields.filterIsInstance<PsiEnumConstant>().firstOrNull()?.name ?: ""
                    )
                    else -> forClass(resolved, visited)
                }
            }
        }
    }

    // ------------------------------------------------------------ 渲染

    private fun render(value: JsonValue): String = buildString { write(value, this) }

    private fun write(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Str -> out.append('"').append(escape(value.value)).append('"')
            is JsonValue.Num -> out.append(value.value)
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            is JsonValue.Obj -> {
                out.append('{')
                value.fields.forEachIndexed { index, (name, fieldValue) ->
                    if (index > 0) out.append(',')
                    out.append('"').append(escape(name)).append("\":")
                    write(fieldValue, out)
                }
                out.append('}')
            }
        }
    }

    private fun escape(value: String): String = buildString {
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
    }

    private sealed interface JsonValue {
        data class Str(val value: String) : JsonValue
        data class Num(val value: String) : JsonValue
        data class Bool(val value: Boolean) : JsonValue
        data class Arr(val items: List<JsonValue>) : JsonValue
        data class Obj(val fields: List<Pair<String, JsonValue>>) : JsonValue
    }

    private companion object {
        val NUMBER_TYPES = setOf(
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger",
            "java.lang.Number",
        )

        val DATE_TIME_TYPES = setOf(
            "java.time.LocalDateTime", "java.time.LocalTime", "java.time.Instant",
            "java.time.OffsetDateTime", "java.time.ZonedDateTime",
            "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
        )
    }
}

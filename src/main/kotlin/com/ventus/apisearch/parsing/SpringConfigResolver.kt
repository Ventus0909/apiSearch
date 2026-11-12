package com.ventus.apisearch.parsing

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.ventus.apisearch.settings.ApiSearchSettings
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.StringReader
import java.util.Properties

/**
 * 解析结果：端口、context-path 以及来源说明。
 */
data class ResolvedConfig(
    val port: Int?,
    val contextPath: String,
    val source: String,
)

/**
 * 按模块解析 Spring Boot 的 server.port 与 context-path。
 *
 * 支持 application/bootstrap 的 yml/properties、active profile 覆盖、
 * `${VAR:default}` 占位符；设置页中的模块覆写优先级最高。
 */
object SpringConfigResolver {

    private val CONFIG_NAME =
        Regex("^(application|bootstrap)(-[\\w.-]+)?\\.(ya?ml|properties)$", RegexOption.IGNORE_CASE)

    fun resolve(project: Project, module: Module?): ResolvedConfig {
        val settings = ApiSearchSettings.getInstance()
        val moduleName = module?.name.orEmpty()
        val overridePort = moduleName.takeIf { it.isNotEmpty() }?.let(settings::portOverride)
        val overrideContext = moduleName.takeIf { it.isNotEmpty() }?.let(settings::contextPathOverride)

        if (module == null) {
            return ResolvedConfig(overridePort, overrideContext.orEmpty(), "设置项")
        }

        val files = findConfigFiles(project, module)
        if (files.isEmpty()) {
            return ResolvedConfig(overridePort, overrideContext.orEmpty(), "设置项")
        }

        val props = mutableMapOf<String, String>()
        // bootstrap 的优先级低于 application
        files.asSequence()
            .filter { it.name.startsWith("bootstrap", ignoreCase = true) && !isProfileFile(it) }
            .sortedWith(compareBy({ !it.extension.equals("properties", true) }, { it.name }))
            .forEach { props.putAll(readConfig(it)) }
        files.asSequence()
            .filter { it.name.startsWith("application", ignoreCase = true) && !isProfileFile(it) }
            .sortedWith(compareBy({ !it.extension.equals("properties", true) }, { it.name }))
            .forEach { props.putAll(readConfig(it)) }

        val profiles = activeProfiles(props)
        for (profile in profiles) {
            // 先应用 bootstrap-{profile}，再应用 application-{profile}
            files.asSequence()
                .filter { isProfileFile(it, profile) }
                .sortedWith(compareBy(
                    { if (it.name.startsWith("bootstrap", ignoreCase = true)) 0 else 1 },
                    { !it.extension.equals("properties", true) },
                    { it.name },
                ))
                .forEach { props.putAll(readConfig(it)) }
        }

        val port = overridePort
            ?: RunConfigPortResolver.resolve(project, module)
            ?: resolvePort(props["server.port"], props)
        val rawContext = props["server.servlet.context-path"] ?: props["spring.mvc.servlet.path"]
        val contextPath = overrideContext
            ?: PlaceholderResolver.resolve(rawContext.orEmpty(), props).trim()

        return ResolvedConfig(port, contextPath, if (port == null) "未找到" else "配置文件")
    }

    // ------------------------------------------------------------ 配置文件

    private fun findConfigFiles(project: Project, module: Module): List<VirtualFile> {
        val scope = GlobalSearchScope.moduleScope(module)
        val fileIndex = ProjectFileIndex.getInstance(project)
        return listOf("yml", "yaml", "properties")
            .flatMap { extension -> FilenameIndex.getAllFilesByExt(project, extension, scope) }
            .filter { CONFIG_NAME.matches(it.name) }
            .filterNot { fileIndex.isInTestSourceContent(it) }
            .distinct()
    }

    private fun isProfileFile(file: VirtualFile, profile: String? = null): Boolean {
        val name = file.nameWithoutExtension
        val dash = name.indexOf('-')
        if (dash < 0) return false
        val prefix = name.substring(0, dash)
        if (!prefix.equals("application", true) && !prefix.equals("bootstrap", true)) return false
        return profile == null || name.substring(dash + 1).equals(profile, true)
    }

    private fun activeProfiles(props: Map<String, String>): List<String> {
        val raw = props["spring.profiles.active"]
            ?: System.getenv("SPRING_PROFILES_ACTIVE")
            ?: System.getProperty("spring.profiles.active")
        if (raw.isNullOrBlank()) return emptyList()
        return PlaceholderResolver.resolve(raw, props)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun readConfig(file: VirtualFile): Map<String, String> {
        val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return emptyMap()
        return if (file.extension.equals("properties", true)) {
            val properties = Properties()
            runCatching { properties.load(StringReader(text)) }
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        } else {
            readYaml(text)
        }
    }

    private fun readYaml(text: String): Map<String, String> {
        val root = runCatching {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text)
        }.getOrNull()
            ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        flatten(root, "", result)
        return result
    }

    private fun flatten(value: Any?, prefix: String, out: MutableMap<String, String>) {
        when (value) {
            is Map<*, *> -> value.forEach { (key, child) ->
                val childKey = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                flatten(child, childKey, out)
            }
            is List<*> -> value.forEachIndexed { index, child ->
                flatten(child, "$prefix[$index]", out)
            }
            else -> if (prefix.isNotEmpty() && value != null) {
                out[prefix] = value.toString()
            }
        }
    }

    private fun resolvePort(raw: String?, props: Map<String, String>): Int? {
        if (raw.isNullOrBlank()) return null
        return PlaceholderResolver.resolve(raw, props)
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 1..65535 }
    }
}

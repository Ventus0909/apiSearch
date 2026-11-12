package com.ventus.apisearch.ui

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.ventus.apisearch.model.SearchResult
import com.ventus.apisearch.services.EndpointOperations
import javax.swing.Icon

/**
 * ChooseByNamePopup 中的接口条目，参考 RestfulToolkit 的 RestServiceItem。
 */
class ApiSearchItem(
    private val project: Project,
    val result: SearchResult,
) : NavigationItem {

    val endpoint get() = result.endpoint
    val moduleName get() = result.moduleName

    /**
     * 名称参与匹配：路径 + 注释 + Class#method + HTTP 方法。
     * 显示时由 renderer 使用 presentation，不受这里影响。
     */
    override fun getName(): String {
        val ep = endpoint
        val simpleClass = ep.className.substringAfterLast('.')
        return buildString {
            append(ep.path)
            if (ep.comment.isNotBlank()) append(' ').append(ep.comment)
            if (simpleClass.isNotBlank()) append(' ').append(simpleClass).append('#').append(ep.methodName)
            append(' ').append(ep.httpMethod)
        }
    }

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "[${endpoint.httpMethod}] ${endpoint.path}"

        override fun getLocationString(): String? = buildLocation()

        override fun getIcon(unused: Boolean): Icon? = null
    }

    fun buildLocationWithoutModule(): String {
        val ep = endpoint
        val simpleClass = ep.className.substringAfterLast('.')
        return if (simpleClass.isBlank()) ep.methodName else "$simpleClass#${ep.methodName}"
    }

    fun buildLocation(): String {
        val base = buildLocationWithoutModule()
        return if (moduleName.isBlank()) base else "$base in '$moduleName'"
    }

    override fun navigate(requestFocus: Boolean) {
        EndpointOperations.openSource(project, endpoint)
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true
}

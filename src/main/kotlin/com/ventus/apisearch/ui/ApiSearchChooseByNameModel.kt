package com.ventus.apisearch.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.gotoByName.CustomMatcherModel
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.ventus.apisearch.index.EndpointSearchService
import javax.swing.ListCellRenderer

/**
 * ChooseByNamePopup 的 Model，参考 RestfulToolkit 的 GotoRequestMappingModel。
 */
class ApiSearchChooseByNameModel(
    private val project: Project,
    val currentModuleName: String?,
    contributors: Array<ChooseByNameContributor>,
) : FilteringGotoByModel<String>(project, contributors), DumbAware, CustomMatcherModel {

    private val properties = PropertiesComponent.getInstance(project)

    @Volatile
    var onlyThisModuleChecked: Boolean = false

    override fun filterValueFor(item: NavigationItem): String? {
        val endpoint = (item as? ApiSearchItem)?.endpoint ?: return null
        return endpoint.httpMethod
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != "ANY" }
    }

    override fun getPromptText(): String = "Enter API Path:"

    override fun getNotInMessage(): String = "No matching API found in project"

    override fun getNotFoundMessage(): String = "No matching API found"

    override fun getCheckBoxName(): String = "Only this module"

    override fun getCheckBoxMnemonic(): Char = 'n'

    override fun loadInitialCheckBoxState(): Boolean {
        onlyThisModuleChecked = properties.getBoolean(ONLY_MODULE_KEY, false)
        return onlyThisModuleChecked
    }

    override fun saveInitialCheckBoxState(state: Boolean) {
        onlyThisModuleChecked = state
        properties.setValue(ONLY_MODULE_KEY, state)
    }

    override fun getFullName(element: Any): String? = getElementName(element)

    override fun getSeparators(): Array<String> = arrayOf("/", "?")

    override fun willOpenEditor(): Boolean = true

    override fun useMiddleMatching(): Boolean = true

    override fun getListCellRenderer(): ListCellRenderer<*> = ApiSearchItemRenderer()

    override fun matches(popupItem: String, userPattern: String): Boolean {
        if (userPattern.isBlank() || userPattern == "/") return true
        if (popupItem.contains(userPattern, ignoreCase = true)) return true
        val path = popupItem.substringBefore(' ')
        return EndpointSearchService.pathMatches(path, userPattern)
    }

    private companion object {
        const val ONLY_MODULE_KEY = "api-search.onlyThisModule"
    }
}

package com.ventus.apisearch.ui

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project

/**
 * 为 ChooseByNamePopup 提供接口名称与条目。
 */
class ApiSearchContributor(
    private val items: List<ApiSearchItem>,
    private val currentModuleName: String?,
) : ChooseByNameContributor {

    private var filteredItems: List<ApiSearchItem> = items

    override fun getNames(project: Project, onlyThisModuleChecked: Boolean): Array<String> {
        filteredItems = if (onlyThisModuleChecked && currentModuleName != null) {
            items.filter { it.moduleName == currentModuleName }
        } else {
            items
        }
        return filteredItems.map { it.name }.toTypedArray()
    }

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        onlyThisModuleChecked: Boolean,
    ): Array<NavigationItem> {
        val source = if (filteredItems.isEmpty()) items else filteredItems
        return source
            .filter { it.name == name }
            .toTypedArray()
    }
}

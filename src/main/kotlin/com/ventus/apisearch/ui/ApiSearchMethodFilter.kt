package com.ventus.apisearch.ui

import com.intellij.ide.util.gotoByName.ChooseByNameFilter
import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * ChooseByNamePopup 底部的 HTTP 方法筛选。
 */
class ApiSearchMethodFilter(
    popup: ChooseByNamePopup,
    model: ApiSearchChooseByNameModel,
    configuration: ChooseByNameFilterConfiguration<String>,
    project: Project,
) : ChooseByNameFilter<String>(popup, model, configuration, project) {

    override fun textForFilterValue(value: String): String = value

    override fun iconForFilterValue(value: String): Icon? = null

    override fun getAllFilterValues(): Collection<String> = HTTP_METHODS

    companion object {
        val HTTP_METHODS = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    }
}

class ApiSearchFilterConfiguration : ChooseByNameFilterConfiguration<String>() {
    override fun nameForElement(element: String): String = element
}

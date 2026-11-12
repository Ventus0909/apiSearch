package com.ventus.apisearch.actions

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.util.gotoByName.ChooseByNameFilter
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectFileIndex
import com.ventus.apisearch.services.EndpointRepository
import com.ventus.apisearch.ui.ApiSearchChooseByNameModel
import com.ventus.apisearch.ui.ApiSearchChooseByNamePopup
import com.ventus.apisearch.ui.ApiSearchContributor
import com.ventus.apisearch.ui.ApiSearchFilterConfiguration
import com.ventus.apisearch.ui.ApiSearchItem
import com.ventus.apisearch.ui.ApiSearchItemProvider
import com.ventus.apisearch.ui.ApiSearchMethodFilter

/**
 * 打开 IDEA 原生 ChooseByNamePopup（与 Ctrl+N 同款）搜索接口。
 * Action ID: ApiSearch.SearchApi
 */
class SearchApiAction : GotoActionBase(), DumbAware {

    override fun gotoActionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val repository = EndpointRepository.getInstance(project)
        // 每次打开弹窗都读取最新索引，保证修改后的路径立即可搜索
        val results = repository.loadNow()
        val items = results.map { ApiSearchItem(project, it) }

        val currentModuleName = event.getData(CommonDataKeys.PSI_FILE)?.let { file ->
            ProjectFileIndex.getInstance(project).getModuleForFile(file.virtualFile)?.name
        }

        val contributor = ApiSearchContributor(items, currentModuleName)
        val model = ApiSearchChooseByNameModel(project, currentModuleName, arrayOf(contributor))
        val provider = ApiSearchItemProvider(items)
        val popup = ApiSearchChooseByNamePopup.create(project, model, provider, null, false, 0)

        val callback = object : GotoActionBase.GotoActionCallback<String>() {
            override fun elementChosen(popup: ChooseByNamePopup, element: Any?) {
                (element as? ApiSearchItem)?.navigate(true)
            }

            override fun createFilter(popup: ChooseByNamePopup): ChooseByNameFilter<String> =
                ApiSearchMethodFilter(popup, model, ApiSearchFilterConfiguration(), project)
        }

        showNavigationPopup(callback, "Search API", popup, false)
    }
}

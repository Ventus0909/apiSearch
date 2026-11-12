package com.ventus.apisearch.ui

import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import com.ventus.apisearch.index.EndpointSearchService
import com.ventus.apisearch.model.SearchResult

/**
 * 自定义过滤：直接使用项目搜索逻辑，支持路径、注释、Class#method 与路径变量通配，
 * 不受平台默认名称匹配限制。
 */
class ApiSearchItemProvider(
    private val items: List<ApiSearchItem>,
) : ChooseByNameItemProvider {

    override fun filterNames(
        viewModel: ChooseByNameViewModel,
        names: Array<String>,
        pattern: String,
    ): List<String> = names.toList()

    override fun filterElements(
        viewModel: ChooseByNameViewModel,
        pattern: String,
        everywhere: Boolean,
        indicator: ProgressIndicator,
        processor: Processor<Any>,
    ): Boolean {
        val model = viewModel.model as? ApiSearchChooseByNameModel
        val popup = viewModel as? ApiSearchChooseByNamePopup
        val onlyModule = popup?.isOnlyThisModuleSelected ?: (model?.onlyThisModuleChecked == true)
        val moduleFilter = if (onlyModule) model?.currentModuleName else null
        val results = EndpointSearchService.filter(items.map { it.result }, pattern, moduleFilter, null)
        val byKey = items.associateBy { key(it.result) }
        for (result in results) {
            if (indicator.isCanceled) return false
            val item = byKey[key(result)] ?: continue
            if (!processor.process(item)) return false
        }
        return true
    }

    private fun key(result: SearchResult): String =
        "${result.endpoint.filePath}#${result.endpoint.offset}#${result.endpoint.path}"
}

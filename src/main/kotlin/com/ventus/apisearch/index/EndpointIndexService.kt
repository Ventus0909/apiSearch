package com.ventus.apisearch.index

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ventus.apisearch.model.ApiEndpoint

/**
 * 读取接口索引。
 */
object EndpointIndexService {

    fun isReady(project: Project): Boolean = !DumbService.isDumb(project)

    fun getAll(project: Project): List<ApiEndpoint> {
        if (!isReady(project)) return emptyList()
        return FileBasedIndex.getInstance()
            .getValues(
                ApiEndpointIndex.INDEX_ID,
                ApiEndpointIndex.FILE_KEY,
                GlobalSearchScope.projectScope(project),
            )
            .flatten()
    }
}

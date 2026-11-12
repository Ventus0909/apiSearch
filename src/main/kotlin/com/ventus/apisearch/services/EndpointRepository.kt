package com.ventus.apisearch.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import com.ventus.apisearch.index.ApiEndpointIndex
import com.ventus.apisearch.index.EndpointIndexService
import com.ventus.apisearch.model.SearchResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 项目级接口数据仓库：后台一次性读取索引并缓存，UI 侧只做内存过滤。
 *
 * 刷新时可选强制重建索引（索引版本或解析规则变化后使用）。
 */
@Service(Service.Level.PROJECT)
class EndpointRepository(private val project: Project) {

    private val loading = AtomicBoolean(false)
    private val pending = java.util.concurrent.CopyOnWriteArrayList<(List<SearchResult>) -> Unit>()

    @Volatile
    private var cached: List<SearchResult>? = null

    @Volatile
    private var cachedModificationCount: Long = -1

    /**
     * 缓存随 PSI 修改计数失效，保证代码改动后搜索能拿到新接口。
     */
    fun snapshot(): List<SearchResult>? {
        val value = cached ?: return null
        return if (cachedModificationCount == currentModificationCount()) value else null
    }

    /**
     * 同步读取（用于 ChooseByNamePopup 打开前获取数据），总是读取最新索引。
     */
    fun loadNow(): List<SearchResult> {
        val data = ReadAction.compute<List<SearchResult>, RuntimeException> { loadBlocking() }
        cached = data
        cachedModificationCount = currentModificationCount()
        return data
    }

    fun ensureLoaded(onReady: (List<SearchResult>) -> Unit) {
        snapshot()?.let {
            onReady(it)
            return
        }
        // 索引构建中：先让 UI 立即可用，索引完成后自动重新加载
        if (DumbService.isDumb(project)) {
            onReady(emptyList())
            DumbService.getInstance(project).runWhenSmart {
                ensureLoaded(onReady)
            }
            return
        }
        pending.add(onReady)
        if (!loading.compareAndSet(false, true)) return
        loadAsync()
    }

    /**
     * 轻量刷新：只重新读取现有索引（IDEA 已增量维护），不触发全量重建。
     */
    fun reload(onReady: (List<SearchResult>) -> Unit) {
        cached = null
        pending.add(onReady)
        DumbService.getInstance(project).runWhenSmart {
            if (!loading.compareAndSet(false, true)) return@runWhenSmart
            loadAsync()
        }
    }

    fun invalidate() {
        cached = null
    }

    private fun loadAsync() {
        AppExecutorUtil.getAppExecutorService().execute {
            val data = try {
                ReadAction.compute<List<SearchResult>, RuntimeException> { loadBlocking() }
            } catch (_: Throwable) {
                emptyList()
            }
            cached = data
            cachedModificationCount = currentModificationCount()
            loading.set(false)
            val callbacks = pending.toList()
            pending.clear()
            ApplicationManager.getApplication().invokeLater {
                callbacks.forEach { callback -> callback(data) }
            }
        }
    }

    private fun loadBlocking(): List<SearchResult> {
        if (!EndpointIndexService.isReady(project)) return emptyList()

        val values = FileBasedIndex.getInstance()
            .getValues(
                ApiEndpointIndex.INDEX_ID,
                ApiEndpointIndex.FILE_KEY,
                GlobalSearchScope.projectScope(project),
            )

        val moduleCache = HashMap<String, String?>()
        return values.asSequence()
            .flatten()
            .map { endpoint ->
                val moduleName = moduleCache.getOrPut(endpoint.filePath) {
                    moduleNameFor(endpoint.filePath)
                }.orEmpty()
                SearchResult(endpoint, moduleName, 0)
            }
            .distinctBy { "${it.endpoint.filePath}#${it.endpoint.offset}#${it.endpoint.path}" }
            .toList()
    }

    private fun moduleNameFor(filePath: String): String? {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        return ProjectFileIndex.getInstance(project).getModuleForFile(file)?.name
    }

    private fun currentModificationCount(): Long =
        PsiModificationTracker.getInstance(project).modificationCount

    companion object {
        fun getInstance(project: Project): EndpointRepository = project.service()
    }
}

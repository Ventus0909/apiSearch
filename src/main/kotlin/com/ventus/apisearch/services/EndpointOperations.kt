package com.ventus.apisearch.services

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.application.ReadAction
import com.ventus.apisearch.generation.CurlGenerator
import com.ventus.apisearch.generation.RequestSpec
import com.ventus.apisearch.generation.RequestSpecBuilder
import com.ventus.apisearch.generation.UrlGenerator
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import com.ventus.apisearch.parsing.PsiEndpointLocator
import com.ventus.apisearch.parsing.ResolvedConfig
import com.ventus.apisearch.parsing.SpringConfigResolver
import com.ventus.apisearch.settings.ApiSearchSettings
import java.awt.datatransfer.StringSelection

/**
 * 右键菜单与弹窗共用的接口操作：复制、打开、跳转。
 */
object EndpointOperations {

    fun moduleFor(project: Project, endpoint: ApiEndpoint): Module? {
        val file = LocalFileSystem.getInstance().findFileByPath(endpoint.filePath) ?: return null
        return ProjectFileIndex.getInstance(project).getModuleForFile(file)
    }

    /**
     * 解析端口与 context-path；端口缺失且允许交互时弹框填写并记住。
     */
    fun resolveConfig(
        project: Project,
        module: Module?,
        endpoint: ApiEndpoint,
        allowPrompt: Boolean,
    ): ResolvedConfig? {
        var config = ReadAction.compute<ResolvedConfig, RuntimeException> {
            SpringConfigResolver.resolve(project, module)
        }
        if (config.port == null && needsPort(endpoint)) {
            if (!allowPrompt) return null
            val port = promptPort(project, module) ?: return null
            config = config.copy(port = port, source = "手动覆写")
        }
        return config
    }

    private fun needsPort(endpoint: ApiEndpoint): Boolean =
        endpoint.framework != Framework.FEIGN ||
            (endpoint.feignBaseUrl.isNullOrBlank() && endpoint.feignServiceName.isNullOrBlank())

    private fun promptPort(project: Project, module: Module?): Int? {
        val moduleName = module?.name ?: "当前模块"
        val input = Messages.showInputDialog(
            project,
            "无法从配置解析模块「$moduleName」的端口，请输入端口（将保存为该模块的覆写值）：",
            "API Search",
            Messages.getQuestionIcon(),
            "8080",
            null,
        ) ?: return null
        val port = input.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (module != null) {
            ApiSearchSettings.getInstance().setPortOverride(module.name, port)
        }
        return port
    }

    fun buildUrl(endpoint: ApiEndpoint, config: ResolvedConfig): String =
        UrlGenerator.fullUrl(endpoint, config, ApiSearchSettings.getInstance().host)

    fun buildCurl(project: Project, endpoint: ApiEndpoint, config: ResolvedConfig): String {
        val url = buildUrl(endpoint, config)
        return CurlGenerator.build(endpoint, url, buildRequestSpec(project, endpoint))
    }

    fun buildRequestBodyJson(project: Project, endpoint: ApiEndpoint): String? =
        buildRequestSpec(project, endpoint).bodyJson

    private fun buildRequestSpec(project: Project, endpoint: ApiEndpoint): RequestSpec =
        ReadAction.compute<RequestSpec, RuntimeException> {
            PsiEndpointLocator.findJavaMethod(project, endpoint.filePath, endpoint.offset)?.let {
                return@compute RequestSpecBuilder.forJavaMethod(project, it)
            }
            PsiEndpointLocator.findKotlinFunction(project, endpoint.filePath, endpoint.offset)?.let {
                return@compute RequestSpecBuilder.forKotlinFunction(project, it)
            }
            RequestSpec()
        }

    fun copyPath(endpoint: ApiEndpoint): String {
        copyToClipboard(endpoint.path)
        return endpoint.path
    }

    fun copyUrl(project: Project, endpoint: ApiEndpoint, module: Module?): String? {
        val config = resolveConfig(project, module, endpoint, true) ?: return null
        val url = buildUrl(endpoint, config)
        copyToClipboard(url)
        return url
    }

    fun copyCurl(project: Project, endpoint: ApiEndpoint, module: Module?): String? {
        val config = resolveConfig(project, module, endpoint, true) ?: return null
        val curl = buildCurl(project, endpoint, config)
        copyToClipboard(curl)
        return curl
    }

    fun copyJson(project: Project, endpoint: ApiEndpoint): String? {
        val body = buildRequestBodyJson(project, endpoint) ?: return null
        copyToClipboard(body)
        return body
    }

    fun openInBrowser(project: Project, endpoint: ApiEndpoint, module: Module?): Boolean {
        val config = resolveConfig(project, module, endpoint, true) ?: return false
        BrowserUtil.browse(buildUrl(endpoint, config))
        return true
    }

    fun openSource(project: Project, endpoint: ApiEndpoint) {
        val file = LocalFileSystem.getInstance().findFileByPath(endpoint.filePath) ?: return
        OpenFileDescriptor(project, file, endpoint.offset.coerceAtLeast(0)).navigate(true)
    }

    private fun copyToClipboard(value: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(value))
    }

    fun notify(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ApiSearch")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }
}

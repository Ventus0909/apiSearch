package com.ventus.apisearch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.services.EndpointOperations

/**
 * 所有基于光标位置接口的右键动作的公共基类。
 */
abstract class EndpointActionBase(text: String) : AnAction(text) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null || DumbService.isDumb(project)) {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val context = ReadAction.compute<EndpointActionContext?, RuntimeException> {
            EndpointActionContext.create(event)
        }
        event.presentation.isEnabledAndVisible = context != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val context = ReadAction.compute<EndpointActionContext?, RuntimeException> {
            EndpointActionContext.create(event)
        } ?: return
        val endpoint = context.chooseEndpoint() ?: return
        perform(context, endpoint)
    }

    protected abstract fun perform(context: EndpointActionContext, endpoint: ApiEndpoint)
}

class CopyPathAction : EndpointActionBase("复制路径") {
    override fun perform(context: EndpointActionContext, endpoint: ApiEndpoint) {
        val path = EndpointOperations.copyPath(endpoint)
        EndpointOperations.notify(context.project, "已复制路径", path)
    }
}

class CopyUrlAction : EndpointActionBase("复制完整 URL") {
    override fun perform(context: EndpointActionContext, endpoint: ApiEndpoint) {
        val url = EndpointOperations.copyUrl(context.project, endpoint, context.module) ?: return
        EndpointOperations.notify(context.project, "已复制 URL", url)
    }
}

class CopyCurlAction : EndpointActionBase("复制 cURL") {
    override fun perform(context: EndpointActionContext, endpoint: ApiEndpoint) {
        val curl = EndpointOperations.copyCurl(context.project, endpoint, context.module) ?: return
        EndpointOperations.notify(context.project, "已复制 cURL", curl)
    }
}

class CopyJsonAction : EndpointActionBase("复制生成 JSON") {
    override fun perform(context: EndpointActionContext, endpoint: ApiEndpoint) {
        val json = EndpointOperations.copyJson(context.project, endpoint)
        if (json == null) {
            EndpointOperations.notify(context.project, "没有请求体", "该方法没有 @RequestBody 参数")
        } else {
            EndpointOperations.notify(context.project, "已复制请求体 JSON", json)
        }
    }
}

class OpenInBrowserAction : EndpointActionBase("在浏览器打开") {
    override fun perform(context: EndpointActionContext, endpoint: ApiEndpoint) {
        if (!EndpointOperations.openInBrowser(context.project, endpoint, context.module)) {
            EndpointOperations.notify(context.project, "无法打开", "缺少可解析的端口配置")
        }
    }
}

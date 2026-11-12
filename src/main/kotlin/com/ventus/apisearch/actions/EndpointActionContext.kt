package com.ventus.apisearch.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.parsing.MethodEndpointResolver
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 右键菜单上下文：光标所在方法的接口定义集合。
 */
class EndpointActionContext(
    val project: Project,
    val module: Module?,
    val endpoints: List<ApiEndpoint>,
) {

    /**
     * 一个方法映射多个路径时让用户选择；只有一个时直接返回。
     */
    fun chooseEndpoint(): ApiEndpoint? {
        if (endpoints.size <= 1) return endpoints.firstOrNull()
        val options = endpoints.map { it.path }.toTypedArray()
        val selectedIndex = Messages.showChooseDialog(
            project,
            "该方法映射了多个路径，请选择：",
            "API Search",
            Messages.getQuestionIcon(),
            options,
            options.first(),
        )
        return endpoints.getOrNull(selectedIndex)
    }

    companion object {
        fun create(event: AnActionEvent): EndpointActionContext? {
            val project = event.project ?: return null
            val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
            val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null
            val element = psiFile.findElementAt(editor.caretModel.offset) ?: return null

            val javaMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            val kotlinFunction = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            val endpoints = when {
                javaMethod != null -> MethodEndpointResolver.forJavaMethod(javaMethod)
                kotlinFunction != null -> MethodEndpointResolver.forKotlinFunction(kotlinFunction)
                else -> emptyList()
            }
            if (endpoints.isEmpty()) return null

            val module = ProjectFileIndex.getInstance(project).getModuleForFile(psiFile.virtualFile)
            return EndpointActionContext(project, module, endpoints)
        }
    }
}

package com.ventus.apisearch.parsing

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.ventus.apisearch.index.JavaEndpointCollector
import com.ventus.apisearch.index.KotlinEndpointCollector
import com.ventus.apisearch.model.ApiEndpoint
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 从光标所在的方法重新解析接口定义（右键菜单使用），保证包含类级路径。
 */
object MethodEndpointResolver {

    fun forJavaMethod(method: PsiMethod): List<ApiEndpoint> {
        val containingClass = method.containingClass ?: return emptyList()
        val offset = method.nameIdentifier?.textRange?.startOffset ?: method.textRange.startOffset
        return JavaEndpointCollector.collectClass(containingClass).filter { it.offset == offset }
    }

    fun forKotlinFunction(function: KtNamedFunction): List<ApiEndpoint> {
        val containingClass = PsiTreeUtil.getParentOfType(function, KtClass::class.java)
            ?: return emptyList()
        val offset = function.nameIdentifier?.textRange?.startOffset ?: function.textRange.startOffset
        return KotlinEndpointCollector.collectClass(containingClass).filter { it.offset == offset }
    }
}

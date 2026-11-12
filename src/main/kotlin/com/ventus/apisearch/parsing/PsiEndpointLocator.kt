package com.ventus.apisearch.parsing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 通过索引中保存的文件路径与偏移定位 PSI 方法。
 */
object PsiEndpointLocator {

    fun findJavaMethod(project: Project, filePath: String, offset: Int): PsiMethod? {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val safeOffset = offset.coerceIn(0, (psiFile.textLength - 1).coerceAtLeast(0))
        return PsiTreeUtil.findElementOfClassAtOffset(psiFile, safeOffset, PsiMethod::class.java, false)
    }

    fun findKotlinFunction(project: Project, filePath: String, offset: Int): KtNamedFunction? {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        val safeOffset = offset.coerceIn(0, (psiFile.textLength - 1).coerceAtLeast(0))
        return PsiTreeUtil.findElementOfClassAtOffset(
            psiFile, safeOffset, KtNamedFunction::class.java, false
        )
    }
}

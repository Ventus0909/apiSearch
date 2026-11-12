package com.ventus.apisearch.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.ventus.apisearch.model.ApiEndpoint
import org.jetbrains.kotlin.psi.KtFile

/**
 * 项目级接口索引：每个源文件对应一条记录（key 固定），value 为文件中所有接口。
 *
 * 只索引源码与配置文件，不索引测试目录和构建产物（由输入过滤器和调用方保证）。
 */
class ApiEndpointIndex : FileBasedIndexExtension<String, List<ApiEndpoint>>() {

    companion object {
        const val NAME_ID = "com.ventus.apisearch.endpoints"
        const val FILE_KEY = "endpoints"
        private const val VERSION = 3

        val INDEX_ID: ID<String, List<ApiEndpoint>> = ID.create(NAME_ID)
    }

    override fun getName(): ID<String, List<ApiEndpoint>> = INDEX_ID

    override fun getIndexer(): DataIndexer<String, List<ApiEndpoint>, FileContent> =
        DataIndexer { inputData ->
            val file = inputData.file
            val endpoints = when (file.extension?.lowercase()) {
                "java" -> (inputData.psiFile as? PsiJavaFile)
                    ?.let(JavaEndpointCollector::collect)
                    .orEmpty()

                "kt" -> (inputData.psiFile as? KtFile)
                    ?.let(KotlinEndpointCollector::collect)
                    .orEmpty()

                "yml", "yaml" -> GatewayEndpointCollector.collect(file, inputData.contentAsText.toString())

                else -> emptyList()
            }
            if (endpoints.isEmpty()) emptyMap() else mapOf(FILE_KEY to endpoints)
        }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<ApiEndpoint>> = ApiEndpointExternalizer()

    override fun getVersion(): Int = VERSION

    override fun dependsOnFileContent(): Boolean = true

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        object : FileBasedIndex.InputFilter {
            override fun acceptInput(file: VirtualFile): Boolean {
                val name = file.name
                return when (file.extension?.lowercase()) {
                    "java", "kt" -> true
                    "yml", "yaml" -> name.startsWith("application") || name.startsWith("bootstrap")
                    else -> false
                }
            }
        }
}

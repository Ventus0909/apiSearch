package com.ventus.apisearch.parsing

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * 从 IDEA 的 Run/Debug 配置中读取端口：
 * - 环境变量 SERVER_PORT / server.port
 * - VM 参数 -Dserver.port=xxxx
 * - 程序参数 --server.port=xxxx
 *
 * 注意：这是运行配置里声明的端口，不是进程实际监听的端口。
 */
object RunConfigPortResolver {

    private val SERVER_PORT_ARG = Regex("(?:-Dserver\\.port=|--server\\.port=)(\\d+)")

    fun resolve(project: Project, module: Module): Int? {
        val manager = RunManager.getInstance(project)
        for (settings in manager.allSettings) {
            val configuration = settings.configuration ?: continue
            val configModuleName = moduleNameOf(configuration)
            if (configModuleName != null && configModuleName != module.name) continue

            val env = readEnvironment(configuration)
            val port = parsePort(env["SERVER_PORT"])
                ?: parsePort(env["server.port"])
                ?: parseServerPortArgument(readString(configuration, "getVMParameters"))
                ?: parseServerPortArgument(readString(configuration, "getVmParameters"))
                ?: parseServerPortArgument(readString(configuration, "getProgramParameters"))
            if (port != null) return port
        }
        return null
    }

    private fun moduleNameOf(configuration: RunConfiguration): String? = try {
        val configurationModule = configuration.javaClass.methods
            .firstOrNull { it.name == "getConfigurationModule" && it.parameterCount == 0 }
            ?.invoke(configuration) ?: return null
        val module = configurationModule.javaClass.methods
            .firstOrNull { it.name == "getModule" && it.parameterCount == 0 }
            ?.invoke(configurationModule) as? Module
        module?.name
    } catch (_: Throwable) {
        null
    }

    @Suppress("UNCHECKED_CAST")
    private fun readEnvironment(configuration: RunConfiguration): Map<String, String> = try {
        val method = configuration.javaClass.methods.firstOrNull {
            it.name == "getEnvs" && it.parameterCount == 0
        }
        (method?.invoke(configuration) as? Map<String, String>) ?: emptyMap()
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun readString(configuration: RunConfiguration, methodName: String): String? = try {
        configuration.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        }?.invoke(configuration) as? String
    } catch (_: Throwable) {
        null
    }

    private fun parsePort(value: String?): Int? =
        value?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }

    private fun parseServerPortArgument(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = SERVER_PORT_ARG.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..65535 }
    }
}

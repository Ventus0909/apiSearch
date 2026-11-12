package com.ventus.apisearch.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 插件全局设置：默认 host、模块端口覆写、模块 context-path 覆写。
 */
@State(name = "ApiSearchSettings", storages = [Storage("api-search.xml")])
class ApiSearchSettings : PersistentStateComponent<ApiSearchSettings.State> {

    data class State(
        var host: String = "localhost",
        var portOverrides: MutableMap<String, String> = mutableMapOf(),
        var contextPathOverrides: MutableMap<String, String> = mutableMapOf(),
        var maxPopupRows: Int = 15,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var host: String
        get() = state.host.ifBlank { "localhost" }
        set(value) {
            state.host = value
        }

    fun portOverride(moduleName: String): Int? =
        state.portOverrides[moduleName]?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }

    fun setPortOverride(moduleName: String, port: Int?) {
        if (port == null) state.portOverrides.remove(moduleName)
        else state.portOverrides[moduleName] = port.toString()
    }

    fun contextPathOverride(moduleName: String): String? =
        state.contextPathOverrides[moduleName]?.takeIf { it.isNotBlank() }

    fun setContextPathOverride(moduleName: String, contextPath: String?) {
        if (contextPath.isNullOrBlank()) state.contextPathOverrides.remove(moduleName)
        else state.contextPathOverrides[moduleName] = contextPath
    }

    var maxPopupRows: Int
        get() = state.maxPopupRows.coerceIn(MIN_POPUP_ROWS, MAX_POPUP_ROWS)
        set(value) {
            state.maxPopupRows = value.coerceIn(MIN_POPUP_ROWS, MAX_POPUP_ROWS)
        }

    fun portOverrideEntries(): Map<String, String> = state.portOverrides.toMap()

    fun contextPathOverrideEntries(): Map<String, String> = state.contextPathOverrides.toMap()

    companion object {
        const val MIN_POPUP_ROWS = 5
        const val MAX_POPUP_ROWS = 50

        fun getInstance(): ApiSearchSettings =
            ApplicationManager.getApplication().getService(ApiSearchSettings::class.java)
    }
}

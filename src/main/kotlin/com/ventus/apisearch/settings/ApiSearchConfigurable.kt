package com.ventus.apisearch.settings

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

private data class ModuleOverrideRow(
    val moduleName: String,
    var port: String,
    var contextPath: String,
)

private class ModuleOverrideTableModel : AbstractTableModel() {

    private val columns = listOf("模块", "端口", "Context-Path")
    private val rows = mutableListOf<ModuleOverrideRow>()

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.moduleName
            1 -> row.port
            2 -> row.contextPath
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex > 0

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            1 -> row.port = value?.toString().orEmpty()
            2 -> row.contextPath = value?.toString().orEmpty()
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun setRows(newRows: List<ModuleOverrideRow>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }

    fun currentRows(): List<ModuleOverrideRow> = rows.map { it.copy() }
}

/**
 * 设置页：默认 host、模块端口覆写、模块 context-path 覆写。
 */
class ApiSearchConfigurable(private val project: Project) : Configurable {

    private val hostField = JBTextField()
    private val maxRowsSpinner = JBIntSpinner(15, ApiSearchSettings.MIN_POPUP_ROWS, ApiSearchSettings.MAX_POPUP_ROWS)
    private val tableModel = ModuleOverrideTableModel()
    private val table = JBTable(tableModel)

    private var panel: JComponent? = null
    private var originalHost = ""
    private var originalRows: List<ModuleOverrideRow> = emptyList()

    override fun getDisplayName(): String = "API Search"

    override fun createComponent(): JComponent {
        panel?.let { return it }

        table.preferredScrollableViewportSize = Dimension(600, 220)
        table.fillsViewportHeight = true

        val created = FormBuilder.createFormBuilder()
            .addLabeledComponent("默认 Host:", hostField)
            .addComponent(
                JBLabel("例如 localhost 或 https://api.example.com（端口仍按模块配置解析）")
                    .apply { foreground = com.intellij.ui.JBColor.GRAY }
            )
            .addLabeledComponent("弹窗最大展示行数:", maxRowsSpinner)
            .addSeparator()
            .addComponent(JBLabel("模块端口 / Context-Path 覆写（留空表示自动解析）"))
            .addComponent(JBScrollPane(table))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel = created
        return created
    }

    override fun reset() {
        val settings = ApiSearchSettings.getInstance()
        originalHost = settings.host
        hostField.text = originalHost
        maxRowsSpinner.number = settings.maxPopupRows

        val moduleNames = runCatching {
            ModuleManager.getInstance(project).modules.map { it.name }
        }.getOrDefault(emptyList())

        val portOverrides = settings.portOverrideEntries()
        val contextOverrides = settings.contextPathOverrideEntries()
        val allNames = (moduleNames + portOverrides.keys + contextOverrides.keys).distinct().sorted()

        originalRows = allNames.map { name ->
            ModuleOverrideRow(
                moduleName = name,
                port = portOverrides[name].orEmpty(),
                contextPath = contextOverrides[name].orEmpty(),
            )
        }
        tableModel.setRows(originalRows)
    }

    override fun isModified(): Boolean =
        hostField.text.trim() != originalHost.trim() ||
            maxRowsSpinner.number != ApiSearchSettings.getInstance().maxPopupRows ||
            tableModel.currentRows() != originalRows

    override fun apply() {
        val settings = ApiSearchSettings.getInstance()
        settings.host = hostField.text.trim().ifEmpty { "localhost" }
        settings.maxPopupRows = maxRowsSpinner.number
        for (row in tableModel.currentRows()) {
            settings.setPortOverride(row.moduleName, row.port.trim().toIntOrNull())
            settings.setContextPathOverride(row.moduleName, row.contextPath.trim())
        }
    }

    override fun disposeUIResources() {
        panel = null
    }
}

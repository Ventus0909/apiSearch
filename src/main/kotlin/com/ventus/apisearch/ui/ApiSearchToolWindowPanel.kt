package com.ventus.apisearch.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.ventus.apisearch.index.EndpointSearchService
import com.ventus.apisearch.model.SearchResult
import com.ventus.apisearch.services.EndpointOperations
import com.ventus.apisearch.services.EndpointRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComboBox
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableColumn

/**
 * 右侧 REST Services 面板：搜索 + 方法/模块筛选 + 接口表格。
 */
class ApiSearchToolWindowPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JPanel(BorderLayout(0, 6)), Disposable {

    private data class FilterOption(val key: String?, val label: String) {
        override fun toString(): String = label
    }

    private val repository = EndpointRepository.getInstance(project)
    private val properties = PropertiesComponent.getInstance(project)
    private val searchField = SearchTextField()
    private val methodBox = JComboBox<FilterOption>()
    private val moduleBox = JComboBox<FilterOption>()
    private val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
        toolTipText = "Refresh REST services"
        preferredSize = Dimension(46, 28)
        minimumSize = Dimension(46, 28)
        margin = java.awt.Insets(0, 0, 0, 0)
        isFocusable = false
    }
    private val statusLabel = JBLabel("Loading REST services...")
    private val tableModel = EndpointTableModel()
    private val table = JBTable(tableModel)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private var allItems: List<SearchResult> = emptyList()
    private var suppressEvents = false
    private val allColumns = mutableListOf<TableColumn>()
    private val columnVisibility = BooleanArray(COLUMN_NAMES.size) { true }
    private var popupRow = -1
    private var popupColumn = -1

    init {
        Disposer.register(parentDisposable, this)
        buildUi()
        installListeners()
        loadData()
    }

    private fun buildUi() {
        methodBox.preferredSize = Dimension(150, 28)
        moduleBox.preferredSize = Dimension(190, 28)

        val filterLeft = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
        filterLeft.add(JBLabel("Method:"))
        filterLeft.add(methodBox)
        filterLeft.add(JBLabel("Module:"))
        filterLeft.add(moduleBox)

        val filterRow = JPanel(BorderLayout(8, 0))
        filterRow.add(filterLeft, BorderLayout.WEST)
        filterRow.add(refreshButton, BorderLayout.EAST)

        val top = JPanel(BorderLayout(0, 8))
        top.add(searchField, BorderLayout.NORTH)
        top.add(filterRow, BorderLayout.SOUTH)
        top.border = JBUI.Borders.empty(8, 8, 4, 8)

        table.fillsViewportHeight = true
        table.rowHeight = 26
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.tableHeader.font = table.tableHeader.font.deriveFont(java.awt.Font.BOLD)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        val renderer = EndpointTableCellRenderer()
        allColumns.clear()
        for (column in 0 until table.columnCount) {
            val tableColumn = table.columnModel.getColumn(column)
            tableColumn.cellRenderer = renderer
            allColumns.add(tableColumn)
        }
        table.columnModel.getColumn(0).preferredWidth = 70
        table.columnModel.getColumn(1).preferredWidth = 300
        table.columnModel.getColumn(2).preferredWidth = 260
        table.columnModel.getColumn(3).preferredWidth = 220
        table.columnModel.getColumn(4).preferredWidth = 150
        loadColumnVisibility()
        applyColumnVisibility()

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                maybeShowColumnPopup(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                maybeShowColumnPopup(event)
            }
        })

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    selectedItem()?.let { EndpointOperations.openSource(project, it.endpoint) }
                }
            }

            override fun mousePressed(event: MouseEvent) {
                maybeShowPopup(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                maybeShowPopup(event)
            }
        })

        statusLabel.border = JBUI.Borders.empty(4, 8)
        statusLabel.foreground = com.intellij.ui.JBColor.GRAY

        add(top, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun installListeners() {
        searchField.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                alarm.cancelAllRequests()
                alarm.addRequest({ applyFilters() }, 150)
            }
        })

        methodBox.addActionListener {
            if (!suppressEvents) applyFilters()
        }

        moduleBox.addActionListener {
            if (!suppressEvents) applyFilters()
        }

        refreshButton.addActionListener {
            refreshData()
        }
    }

    private fun loadData() {
        refreshButton.isEnabled = false
        statusLabel.text = "Loading REST services..."
        val onReady: (List<SearchResult>) -> Unit = { items ->
            allItems = items
            rebuildModuleOptions()
            refreshMethodOptions()
            refreshButton.isEnabled = true
            applyFilters()
        }
        repository.ensureLoaded(onReady)
    }

    private fun refreshData() {
        refreshButton.isEnabled = false
        statusLabel.text = "Refreshing REST services..."
        repository.reload { items ->
            allItems = items
            rebuildModuleOptions()
            refreshMethodOptions()
            refreshButton.isEnabled = true
            applyFilters()
        }
    }

    private fun rebuildModuleOptions() {
        val selectedKey = currentModuleKey()
        val base = EndpointSearchService.filter(allItems, searchField.text, null, currentMethodKey())
        val counts = EndpointSearchService.moduleCounts(base)
        suppressEvents = true
        try {
            moduleBox.removeAllItems()
            moduleBox.addItem(FilterOption(null, "All Modules (${base.size})"))
            for ((name, count) in counts.entries.sortedBy { it.key }) {
                moduleBox.addItem(FilterOption(name, "$name ($count)"))
            }
            selectByKey(moduleBox, selectedKey)
        } finally {
            suppressEvents = false
        }
    }

    private fun refreshMethodOptions() {
        val base = EndpointSearchService.filter(allItems, searchField.text, currentModuleKey(), null)
        val counts = EndpointSearchService.methodCounts(base, null)
        val selectedKey = currentMethodKey()
        suppressEvents = true
        try {
            methodBox.removeAllItems()
            methodBox.addItem(FilterOption(null, "All Methods (${counts["ALL"] ?: 0})"))
            for (method in METHOD_ORDER) {
                methodBox.addItem(FilterOption(method, "$method (${counts[method] ?: 0})"))
            }
            selectByKey(methodBox, selectedKey)
        } finally {
            suppressEvents = false
        }
    }

    private fun selectByKey(box: JComboBox<FilterOption>, key: String?) {
        for (index in 0 until box.itemCount) {
            if (box.getItemAt(index).key == key) {
                box.selectedIndex = index
                return
            }
        }
        box.selectedIndex = 0
    }

    private fun currentMethodKey(): String? = (methodBox.selectedItem as? FilterOption)?.key

    private fun currentModuleKey(): String? = (moduleBox.selectedItem as? FilterOption)?.key

    private fun applyFilters() {
        // 代码修改后缓存会失效：重新加载并等待回调再过滤
        if (allItems.isNotEmpty() && repository.snapshot() == null) {
            loadData()
            return
        }
        rebuildModuleOptions()
        refreshMethodOptions()
        val filtered = EndpointSearchService.filter(
            allItems,
            searchField.text,
            currentModuleKey(),
            currentMethodKey(),
        )
        tableModel.setRows(filtered)
        statusLabel.text = "${filtered.size} REST services"
    }

    private fun selectedItem(): SearchResult? {
        val row = table.selectedRow
        if (row < 0) return null
        return tableModel.itemAt(row)
    }

    private fun maybeShowPopup(event: MouseEvent) {
        if (!event.isPopupTrigger) return
        val row = table.rowAtPoint(event.point)
        val column = table.columnAtPoint(event.point)
        if (row >= 0) table.setRowSelectionInterval(row, row)
        popupRow = row
        popupColumn = column
        buildPopupMenu().show(table, event.x, event.y)
    }

    private fun maybeShowColumnPopup(event: MouseEvent) {
        if (!event.isPopupTrigger) return
        val menu = JPopupMenu()
        COLUMN_NAMES.forEachIndexed { index, name ->
            val item = JCheckBoxMenuItem(name, columnVisibility[index])
            item.addActionListener {
                columnVisibility[index] = item.isSelected
                if (columnVisibility.none { it }) {
                    columnVisibility[index] = true
                    item.isSelected = true
                }
                applyColumnVisibility()
                saveColumnVisibility()
            }
            menu.add(item)
        }
        menu.show(table.tableHeader, event.x, event.y)
    }

    private fun applyColumnVisibility() {
        val columnModel = table.columnModel
        while (columnModel.columnCount > 0) {
            columnModel.removeColumn(columnModel.getColumn(0))
        }
        for ((index, column) in allColumns.withIndex()) {
            if (columnVisibility[index]) columnModel.addColumn(column)
        }
    }

    private fun loadColumnVisibility() {
        val saved = properties.getValue(COLUMNS_KEY, "")
        if (saved.isBlank()) return
        saved.split(',').forEachIndexed { index, value ->
            if (index < columnVisibility.size) {
                columnVisibility[index] = value == "true"
            }
        }
        if (columnVisibility.none { it }) columnVisibility[0] = true
    }

    private fun saveColumnVisibility() {
        properties.setValue(COLUMNS_KEY, columnVisibility.joinToString(",") { it.toString() })
    }

    private fun buildPopupMenu(): JPopupMenu {
        val popup = JPopupMenu()
        popup.add(menuItem("Copy Cell") { copyPopupCell() })
        popup.addSeparator()
        popup.add(menuItem("Jump to Source") {
            selectedItem()?.let { EndpointOperations.openSource(project, it.endpoint) }
        })
        popup.add(menuItem("Copy URL") { selectedItem()?.let(::copyPath) })
        popup.add(menuItem("Copy Full URL") { selectedItem()?.let(::copyFullUrl) })
        popup.add(menuItem("Copy cURL") { selectedItem()?.let(::copyCurl) })
        popup.addSeparator()
        popup.add(menuItem("Filter by Module") { selectedItem()?.let(::filterByModule) })
        popup.add(menuItem("Filter by Method") { selectedItem()?.let(::filterByMethod) })
        popup.add(menuItem("Clear Filters") { clearFilters() })
        popup.addSeparator()
        popup.add(menuItem("Refresh") { refreshData() })
        return popup
    }

    private fun menuItem(text: String, action: () -> Unit): JMenuItem =
        JMenuItem(text).apply { addActionListener { action() } }

    private fun copyPopupCell() {
        if (popupRow < 0 || popupColumn < 0 || popupRow >= tableModel.rowCount) return
        val modelColumn = table.convertColumnIndexToModel(popupColumn)
        val value = tableModel.getValueAt(popupRow, modelColumn).toString()
        com.intellij.openapi.ide.CopyPasteManager.getInstance()
            .setContents(java.awt.datatransfer.StringSelection(value))
        EndpointOperations.notify(project, "已复制单元格", value)
    }

    private fun copyPath(item: SearchResult) {
        val path = EndpointOperations.copyPath(item.endpoint)
        EndpointOperations.notify(project, "已复制路径", path)
    }

    private fun copyFullUrl(item: SearchResult) {
        val module = EndpointOperations.moduleFor(project, item.endpoint)
        val url = EndpointOperations.copyUrl(project, item.endpoint, module) ?: return
        EndpointOperations.notify(project, "已复制完整 URL", url)
    }

    private fun copyCurl(item: SearchResult) {
        val module = EndpointOperations.moduleFor(project, item.endpoint)
        val curl = EndpointOperations.copyCurl(project, item.endpoint, module) ?: return
        EndpointOperations.notify(project, "已复制 cURL", curl)
    }

    private fun filterByModule(item: SearchResult) {
        if (item.moduleName.isBlank()) return
        suppressEvents = true
        try {
            selectByKey(moduleBox, item.moduleName)
        } finally {
            suppressEvents = false
        }
        refreshMethodOptions()
        applyFilters()
    }

    private fun filterByMethod(item: SearchResult) {
        val method = item.endpoint.httpMethod
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != "ANY" } ?: return
        suppressEvents = true
        try {
            selectByKey(methodBox, method)
        } finally {
            suppressEvents = false
        }
        applyFilters()
    }

    private fun clearFilters() {
        suppressEvents = true
        try {
            searchField.text = ""
            selectByKey(moduleBox, null)
            refreshMethodOptions()
            selectByKey(methodBox, null)
        } finally {
            suppressEvents = false
        }
        applyFilters()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    private class EndpointTableModel : AbstractTableModel() {

        private val columns = arrayOf("Method", "Path", "Class#Method", "Comment", "Module")
        private var rows: List<SearchResult> = emptyList()

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val item = rows[rowIndex]
            val endpoint = item.endpoint
            return when (columnIndex) {
                0 -> endpoint.httpMethod
                1 -> endpoint.path
                2 -> if (endpoint.className.isBlank()) {
                    endpoint.methodName
                } else {
                    "${endpoint.className.substringAfterLast('.')}#${endpoint.methodName}"
                }
                3 -> endpoint.comment
                4 -> item.moduleName
                else -> ""
            }
        }

        fun setRows(newRows: List<SearchResult>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun itemAt(index: Int): SearchResult? = rows.getOrNull(index)
    }

    private class EndpointTableCellRenderer : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val raw = value?.toString().orEmpty()
            val display = if (column == 3 && raw.length > 100) raw.take(99) + "…" else raw
            super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, column)

            toolTipText = if (raw.length > display.length) raw else null
            foreground = if (isSelected) {
                table.selectionForeground
            } else if (column == 0) {
                MethodColors.of(raw)
            } else {
                table.foreground
            }
            horizontalAlignment = if (column == 4) SwingConstants.RIGHT else SwingConstants.LEFT
            return this
        }
    }

    private companion object {
        val METHOD_ORDER = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        val COLUMN_NAMES = listOf("Method", "Path", "Class#Method", "Comment", "Module")
        const val COLUMNS_KEY = "api-search.visibleColumns"
    }
}

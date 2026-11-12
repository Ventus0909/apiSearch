package com.ventus.apisearch.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.ventus.apisearch.model.ApiEndpoint
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.border.LineBorder

/**
 * 单行展示：`◈ [METHOD] url # 注释` + 右侧 `Class#method  模块名`。
 */
class ApiSearchItemRenderer : JPanel(BorderLayout(10, 0)), ListCellRenderer<Any> {

    private val leftLabel = JLabel()
    private val classLabel = JLabel()
    private val moduleBadge = JLabel()
    private val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

    init {
        isOpaque = true
        border = JBUI.Borders.empty(2, 8)

        add(leftLabel, BorderLayout.CENTER)

        rightPanel.isOpaque = false
        rightPanel.add(classLabel)
        rightPanel.add(moduleBadge)
        add(rightPanel, BorderLayout.EAST)

        moduleBadge.isOpaque = true
        moduleBadge.border = BorderFactory.createCompoundBorder(
            LineBorder(JBColor.border(), 1, true),
            JBUI.Borders.empty(0, 5),
        )
        moduleBadge.background = JBColor(0xEFEFEF, 0x3C3F41)
        moduleBadge.font = moduleBadge.font.deriveFont(moduleBadge.font.size2D - 0.5f)
        classLabel.font = classLabel.font.deriveFont(classLabel.font.size2D - 0.5f)
    }

    override fun getPreferredSize(): Dimension = Dimension(900, 26)

    override fun getListCellRendererComponent(
        list: JList<out Any>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        if (list.fixedCellHeight != 26) {
            list.fixedCellHeight = 26
        }

        val item = value as? ApiSearchItem
        if (item == null) {
            leftLabel.text = value?.toString().orEmpty()
            classLabel.text = ""
            moduleBadge.isVisible = false
            return this
        }

        val endpoint = item.endpoint
        leftLabel.text = buildLeft(item)

        classLabel.text = item.buildLocationWithoutModule()
        classLabel.toolTipText = item.buildLocationWithoutModule()

        val module = item.moduleName
        moduleBadge.isVisible = module.isNotBlank()
        moduleBadge.text = module
        moduleBadge.toolTipText = module

        leftLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        classLabel.foreground = if (isSelected) list.selectionForeground else JBColor.GRAY

        background = when {
            isSelected -> list.selectionBackground
            index % 2 == 1 -> list.background.darker()
            else -> list.background
        }
        return this
    }

    private fun buildLeft(item: ApiSearchItem): String {
        val endpoint = item.endpoint
        val color = MethodColors.of(endpoint.httpMethod)
        val hex = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
        return buildString {
            append("<html><body style='padding:0'>")
            append("<span style='color:#8c8c8c'>◈</span>&nbsp;")
            append("<span style='color:#ffffff;background-color:").append(hex).append("'>&nbsp;")
            append(escape(endpoint.httpMethod))
            append("&nbsp;</span>&nbsp;")
            append("<span style='font-family:monospace'>")
            append(escape(truncate(endpoint.path, 80)))
            append("</span>")
            if (endpoint.comment.isNotBlank()) {
                append("&nbsp;<span style='color:#000000'># ")
                append(escape(truncate(endpoint.comment, 50)))
                append("</span>")
            }
            append("</body></html>")
        }
    }

    private fun truncate(value: String, maxLength: Int): String =
        if (value.length <= maxLength) value else value.take(maxLength - 1) + "…"

    private fun escape(value: String): String = buildString {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }
}

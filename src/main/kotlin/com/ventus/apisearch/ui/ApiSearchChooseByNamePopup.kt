package com.ventus.apisearch.ui

import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.ventus.apisearch.settings.ApiSearchSettings

/**
 * 自定义 ChooseByNamePopup：在初始化时固定列表行高与宽度，保证两行条目不遮挡。
 */
class ApiSearchChooseByNamePopup protected constructor(
    project: Project?,
    model: ChooseByNameModel,
    provider: ChooseByNameItemProvider,
    oldPopup: ChooseByNamePopup?,
    predefinedText: String?,
    mayRequestOpenInCurrentWindow: Boolean,
    initialIndex: Int,
) : ChooseByNamePopup(
    project,
    model,
    provider,
    oldPopup,
    predefinedText,
    mayRequestOpenInCurrentWindow,
    initialIndex,
) {

    override fun initUI(
        callback: ChooseByNamePopupComponent.Callback,
        modalityState: ModalityState,
        showListForEmptyPattern: Boolean,
    ) {
        super.initUI(callback, modalityState, showListForEmptyPattern)
        myList.fixedCellHeight = 26
        myList.fixedCellWidth = 900
        // 限制结果列表最大可见行数，超出部分通过滚动查看，避免遮挡搜索框
        myList.visibleRowCount = ApiSearchSettings.getInstance().maxPopupRows
    }

    /**
     * 原生复选框的实时状态（Model 的 saveInitialCheckBoxState 不保证在勾选时调用）。
     */
    val isOnlyThisModuleSelected: Boolean
        get() = myCheckBox.isSelected

    companion object {
        fun create(
            project: Project?,
            model: ChooseByNameModel,
            provider: ChooseByNameItemProvider,
            predefinedText: String?,
            mayRequestOpenInCurrentWindow: Boolean,
            initialIndex: Int,
        ): ApiSearchChooseByNamePopup = ApiSearchChooseByNamePopup(
            project,
            model,
            provider,
            null,
            predefinedText,
            mayRequestOpenInCurrentWindow,
            initialIndex,
        )
    }
}

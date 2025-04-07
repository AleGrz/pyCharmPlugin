package org.alegrz.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.NotNull

class TypeStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = TypeStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Type Status Bar Widget"

    override fun createWidget(@NotNull project: Project): StatusBarWidget = TypeStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

}
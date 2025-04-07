package org.alegrz.plugin

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext


class TypeStatusBarWidget(private val project: Project) :
    StatusBarWidget, CaretListener, FileEditorManagerListener, DocumentListener {

    companion object {
        const val WIDGET_ID = "TypeStatusBarWidget"
        const val EMPTY = "-"
    }

    private val disp = Disposer.newDisposable("TSBWDisposable")

    private var content = EMPTY
    private var statusBar: StatusBar? = null


    override fun ID(): String = WIDGET_ID

    override fun dispose() {
        Disposer.dispose(disp)
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText() = content
            override fun getTooltipText() = "Type of the variable under caret"
            override fun getAlignment() = 0f
        }
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        EditorFactory.getInstance().eventMulticaster.addCaretListener(this, disp)

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, disp)

        project.messageBus.connect(disp)
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)

        FileEditorManager.getInstance(project).selectedTextEditor?.let {
            updateType(it)
        }
    }

    override fun caretPositionChanged(e: CaretEvent) {
        if (e.editor.project == project) {
            updateType(e.editor)
        }
    }

    override fun caretAdded(event: CaretEvent) {
        if (event.editor.project == project) {
            updateType(event.editor)
        }
    }

    override fun caretRemoved(event: CaretEvent) {
        if (event.editor.project == project) {
            updateType(event.editor)
        }
    }

    override fun selectionChanged(e: FileEditorManagerEvent) {
        val newEditor = e.newEditor
        if (newEditor is TextEditor) {
            updateType(newEditor.editor)
        } else {
            updateWidget(EMPTY)
        }
    }

    override fun documentChanged(e: DocumentEvent) {
        val document = e.document
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor != null && editor.document == document) {
            // wait for the caret to update
            PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(ModalityState.defaultModalityState()) {
                updateType(editor)
            }
        }
    }

    private fun updateType(editor: Editor) {
        val offset = editor.caretModel.offset

        ReadAction.nonBlocking<String> {
            calculateType(editor, offset)
                ?: calculateType(editor, (offset - 1).coerceAtLeast(0))
                ?: EMPTY
        }
        .finishOnUiThread(ModalityState.defaultModalityState()) { typeInfo ->
            if (FileEditorManager.getInstance(project).selectedTextEditor == editor) {
                updateWidget(typeInfo)
            }
        }
        .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun calculateType(editor: Editor, offset: Int): String? {
        if (editor.caretModel.caretCount != 1) {
            return null
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)

        if (psiFile !is PyFile) {
            return null
        }

        val element = psiFile.findElementAt(offset) ?: return null

        // Since the assignment requires displaying types only for variables,
        // I used a whitelist to filter the elements.
        // this whitelist can be modified depending on what we consider a variable (e.g. literals),
        // or removed entirely if we want to display types for everything

        val isVariableFilter = element.parent is PyReferenceExpression ||    // variable reference
                element.parent is PyTargetExpression ||                      // variable assignment
                element.parent is PyParameter                                // function parameter

        if (!isVariableFilter) {
            return null
        }

        val typed = element.let {
            PsiTreeUtil.getParentOfType(it, PyTypedElement::class.java) ?: it as? PyTypedElement
        } ?: return null

        val context = TypeEvalContext.userInitiated(project, psiFile)
        val type = context.getType(typed)

        return type?.name
    }

    private fun updateWidget(text: String) {
        if (text != content) {
            content = text
            statusBar?.updateWidget(ID())
        }
    }
}
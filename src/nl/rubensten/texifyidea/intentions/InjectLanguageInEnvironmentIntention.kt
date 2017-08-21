package nl.rubensten.texifyidea.intentions

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.psi.PsiFile
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.ui.EmptyIcon
import nl.rubensten.texifyidea.LatexLanguage
import nl.rubensten.texifyidea.file.LatexFile
import nl.rubensten.texifyidea.lang.LatexAnnotation
import nl.rubensten.texifyidea.psi.LatexBeginCommand
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.SwingConstants

/**
 *
 * @author Sten Wessel
 */
class InjectLanguageInEnvironmentIntention : TexifyIntention() {

    override fun getFamilyName() = "Inject language"

    override fun getText() = "Inject language in environment"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file !is LatexFile || editor == null) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false

        return PsiTreeUtil.getParentOfType(element, LatexBeginCommand::class.java) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file !is LatexFile || editor == null) {
            return
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val beginCommand = PsiTreeUtil.getParentOfType(element, LatexBeginCommand::class.java) ?: return

        chooseLanguage(editor) { language ->
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.insertString(beginCommand.textOffset, LatexAnnotation(LatexAnnotation.KEY_INJECT_LANGUAGE, language.id).toString() + "\n")
            }
        }

    }

    private fun chooseLanguage(editor: Editor, onChosen: (language: Injectable) -> Unit) {
        // Dummy to determine height of single cell
        val dimension = JLabel(LatexLanguage.INSTANCE.displayName, EmptyIcon.ICON_16, SwingConstants.LEFT).minimumSize
        dimension.height *= 4

        val list = JBList(injectableLanguages()).apply {
            cellRenderer = object : ColoredListCellRenderer<Injectable>() {
                override fun customizeCellRenderer(list: JList<out Injectable>, language: Injectable, index: Int, selected: Boolean, hasFixed: Boolean) {
                    icon = language.icon
                    append(language.displayName)

                }
            }

            minimumSize = dimension
        }

        val popup = PopupChooserBuilder(list).setItemChoosenCallback { onChosen(list.selectedValue) }
                .setFilteringEnabled { language -> (language as Injectable).displayName }
                .setMinSize(dimension)
                .createPopup()

        popup.showInBestPositionFor(editor)
    }

    private fun injectableLanguages(): List<Injectable> = Language.getRegisteredLanguages().map { Injectable.fromLanguage(it) }
}

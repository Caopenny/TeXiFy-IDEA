package nl.rubensten.texifyidea.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.rubensten.texifyidea.lang.Environment
import nl.rubensten.texifyidea.psi.*
import nl.rubensten.texifyidea.util.*

/**
 * @author Ruben Schellekens
 */
open class LatexAnnotator : Annotator {

    companion object {

        /**
         * The maximum amount of times the cache may be used before doing another lookup.
         */
        private val MAX_CACHE_COUNT = 40
    }

    // Cache to prevent many PsiFile#isUsed and PsiFile#definitions() lookups.
    private var definitionCache: Collection<LatexCommands>? = null
    private var definitionCacheFile: PsiFile? = null
    private var definitionCacheCount: Int = 0

    /**
     * Looks up all the definitions in the file set.
     *
     * It does cache all the definitions for [MAX_CACHE_COUNT] lookups.
     * See also members [definitionCache], [definitionCacheFile], and [definitionCacheCount]
     */
    private fun PsiFile.definitionCache(): Collection<LatexCommands> {
        // Initialise.
        if (definitionCache == null) {
            definitionCache = definitionsAndRedefinitions().filter { it.isEnvironmentDefinition() }
            definitionCacheFile = this
            definitionCacheCount = 0
            return definitionCache!!
        }

        // Check if the file is the same.
        if (definitionCacheFile != this) {
            definitionCache = definitionsAndRedefinitions().filter { it.isEnvironmentDefinition() }
            definitionCacheCount = 0
            definitionCacheFile = this
        }

        // Re-evaluate after the count has been reached times.
        if (++definitionCacheCount > MAX_CACHE_COUNT) {
            definitionCache = definitionsAndRedefinitions().filter { it.isEnvironmentDefinition() }
            definitionCacheCount = 0
        }

        return definitionCache!!
    }

    override fun annotate(psiElement: PsiElement, annotationHolder: AnnotationHolder) {
        // Comments
        if (psiElement is PsiComment) {
            annotateComment(psiElement, annotationHolder)
        }
        else if (psiElement.inDirectEnvironmentContext(Environment.Context.COMMENT)) {
            annotateComment(psiElement, annotationHolder)
        }
        // Math display
        else if (psiElement is LatexInlineMath) {
            annotateInlineMath(psiElement, annotationHolder)
        }
        else if (psiElement is LatexDisplayMath) {
            annotateDisplayMath(psiElement, annotationHolder)
        }
        else if (psiElement.inDirectEnvironmentContext(Environment.Context.MATH)) {
            annotateDisplayMath(psiElement, annotationHolder)
        }
        // Optional parameters
        else if (psiElement is LatexOptionalParam) {
            annotateOptionalParameters(psiElement, annotationHolder)
        }
    }

    /**
     * Annotates an inline math element and its children.
     *
     * All elements will  be coloured accoding to [LatexSyntaxHighlighter.INLINE_MATH] and
     * all commands that are contained in the math environment get styled with
     * [LatexSyntaxHighlighter.COMMAND_MATH_INLINE].
     */
    private fun annotateInlineMath(inlineMathElement: LatexInlineMath,
                                   annotationHolder: AnnotationHolder) {
        val annotation = annotationHolder.createInfoAnnotation(inlineMathElement, null)
        annotation.textAttributes = LatexSyntaxHighlighter.INLINE_MATH

        annotateMathCommands(LatexPsiUtil.getAllChildren(inlineMathElement), annotationHolder,
                LatexSyntaxHighlighter.COMMAND_MATH_INLINE)
    }

    /**
     * Annotates a display math element and its children.
     *
     * All elements will be coloured accoding to [LatexSyntaxHighlighter.DISPLAY_MATH] and
     * all commands that are contained in the math environment get styled with
     * [LatexSyntaxHighlighter.COMMAND_MATH_DISPLAY].
     */
    private fun annotateDisplayMath(displayMathElement: PsiElement,
                                    annotationHolder: AnnotationHolder) {
        val annotation = annotationHolder.createInfoAnnotation(displayMathElement, null)
        annotation.textAttributes = LatexSyntaxHighlighter.DISPLAY_MATH

        annotateMathCommands(displayMathElement.childrenOfType(LatexCommands::class), annotationHolder,
                LatexSyntaxHighlighter.COMMAND_MATH_DISPLAY)
    }

    /**
     * Annotates the given comment.
     */
    private fun annotateComment(comment: PsiElement, annotationHolder: AnnotationHolder) {
        val file = comment.containingFile
        val hasDefinition = file.definitionCache().any { it.requiredParameter(0) == "comment" }
        if (hasDefinition) {
            return
        }

        val annotation = annotationHolder.createInfoAnnotation(comment, null)
        annotation.textAttributes = LatexSyntaxHighlighter.COMMENT
    }

    /**
     * Annotates all command tokens of the comands that are included in the `elements`.
     *
     * @param elements
     *              All elements to handle. Only elements that are [LatexCommands] are considered.
     * @param highlighter
     *              The attributes to apply to all command tokens.
     */
    private fun annotateMathCommands(elements: Collection<PsiElement>,
                                     annotationHolder: AnnotationHolder,
                                     highlighter: TextAttributesKey) {
        for (element in elements) {
            if (element !is LatexCommands) {
                continue
            }

            val token = element.commandToken
            val annotation = annotationHolder.createInfoAnnotation(token, null)
            annotation.textAttributes = highlighter
        }
    }

    /**
     * Annotates the given optional parameters of commands.
     */
    private fun annotateOptionalParameters(optionalParamElement: LatexOptionalParam,
                                           annotationHolder: AnnotationHolder) {
        for (element in optionalParamElement.openGroup.contentList) {
            if (element !is LatexContent) {
                continue
            }

            val noMathContent = element.noMathContent ?: continue
            val toStyle = noMathContent.normalText ?: continue
            val annotation = annotationHolder.createInfoAnnotation(toStyle, null)
            annotation.textAttributes = LatexSyntaxHighlighter.OPTIONAL_PARAM
        }
    }
}

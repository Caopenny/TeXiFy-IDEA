package nl.rubensten.texifyidea.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import nl.rubensten.texifyidea.lang.DefaultEnvironment
import nl.rubensten.texifyidea.lang.LatexCommand
import nl.rubensten.texifyidea.lang.Package
import nl.rubensten.texifyidea.psi.LatexCommands
import nl.rubensten.texifyidea.psi.LatexEnvironment
import nl.rubensten.texifyidea.util.*
import kotlin.reflect.jvm.internal.impl.utils.SmartList

/**
 * Currently works for built-in commands and environments.
 *
 * @author Ruben Schellekens
 */
open class MissingImportInspection : TexifyInspectionBase() {


    override fun getDisplayName(): String {
        return "Missing imports"
    }

    override fun getShortName(): String {
        return "MissingImport"
    }

    override fun inspectFile(file: PsiFile, manager: InspectionManager, isOntheFly: Boolean): List<ProblemDescriptor> {
        val descriptors = SmartList<ProblemDescriptor>()

        val includedPackages = PackageUtils.getIncludedPackages(file)
        analyseCommands(file, includedPackages, descriptors, manager, isOntheFly)
        analyseEnvironments(file, includedPackages, descriptors, manager, isOntheFly)

        return descriptors
    }

    private fun analyseEnvironments(file: PsiFile, includedPackages: Collection<String>,
                                descriptors: MutableList<ProblemDescriptor>, manager: InspectionManager,
                                isOntheFly: Boolean) {
        val environments = file.childrenOfType(LatexEnvironment::class)
        val defined = file.definitionsAndRedefinitions()
                .filter { it.isEnvironmentDefinition() }
                .mapNotNull { it.requiredParameter(0) }.toSet()
        for (env in environments) {
            // Don't consider environments that have been defined.
            if (env.name()?.text in defined) {
                continue
            }

            val name = env.name()?.text ?: continue
            val environment = DefaultEnvironment[name] ?: continue
            val pack = environment.dependency

            if (pack == Package.DEFAULT || includedPackages.contains(pack.name)) {
                continue
            }

            descriptors.add(manager.createProblemDescriptor(
                    env,
                    TextRange(7, 7 + name.length),
                    "DefaultEnvironment requires ${pack.name} package",
                    ProblemHighlightType.ERROR,
                    isOntheFly,
                    ImportEnvironmentFix(pack.name)
            ))
        }
    }

    private fun analyseCommands(file: PsiFile, includedPackages: Collection<String>,
                                descriptors: MutableList<ProblemDescriptor>, manager: InspectionManager,
                                isOntheFly: Boolean) {
        val commands = file.commandsInFileSet()
        for (cmd in commands) {
            if (cmd.name == null) {
                continue
            }
            val latexCommand = LatexCommand.lookup(cmd.name!!) ?: continue
            val pack = latexCommand.dependency

            if (pack.isDefault) {
                continue
            }

            if (!includedPackages.contains(pack.name)) {
                descriptors.add(manager.createProblemDescriptor(
                        cmd,
                        TextRange(0, latexCommand.command.length + 1),
                        "Command requires ${pack.name} package",
                        ProblemHighlightType.ERROR,
                        isOntheFly,
                        ImportCommandFix(pack.name)
                ))
            }
        }
    }

    /**
     * @author Ruben Schellekens
     */
    private class ImportCommandFix(val import: String) : LocalQuickFix {

        override fun getFamilyName(): String {
            return "Add import for package '$import'"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val command = descriptor.psiElement as LatexCommands
            val latexCommand = LatexCommand.lookup(command.name!!) ?: return
            val file = command.containingFile

            PackageUtils.insertUsepackage(file, latexCommand.dependency)
        }
    }

    /**
     * @author Ruben Schellekens
     */
    private class ImportEnvironmentFix(val import: String) : LocalQuickFix {

        override fun getFamilyName(): String {
            return "Add import for package '$import'"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val environment = descriptor.psiElement as? LatexEnvironment ?: return
            val thingy = DefaultEnvironment.fromPsi(environment) ?: return
            val file = environment.containingFile

            PackageUtils.insertUsepackage(file, thingy.dependency)
        }
    }
}

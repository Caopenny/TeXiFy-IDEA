package nl.rubensten.texifyidea.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import nl.rubensten.texifyidea.run.LatexCompiler.Format;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ruben Schellekens, Sten Wessel
 */
public class LatexRunConfiguration extends RunConfigurationBase implements LocatableConfiguration {

    private static final String TEXIFY_PARENT = "texify";
    private static final String COMPILER = "compiler";
    private static final String COMPILER_PATH = "compiler-path";
    private static final String MAIN_FILE = "main-file";
    private static final String AUX_DIR = "aux-dir";
    private static final String OUTPUT_FORMAT = "output-format";

    private LatexCompiler compiler;
    private String compilerPath = null;
    private VirtualFile mainFile;
    private boolean auxDir = true;
    private Format outputFormat = Format.PDF;

    protected LatexRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new LatexSettingsEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (compiler == null || mainFile == null || outputFormat == null) {
            throw new RuntimeConfigurationError("Run configuration is invalid.");
        }
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        RegexpFilter filter = new RegexpFilter(environment.getProject(), "^$FILE_PATH$:$LINE$");

        LatexCommandLineState state = new LatexCommandLineState(environment, this);
        state.addConsoleFilters(filter);
        return state;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);

        Element parent = element.getChild(TEXIFY_PARENT);
        if (parent == null) {
            return;
        }

        // Read compiler.
        String compilerName = parent.getChildText(COMPILER);
        try {
            this.compiler = LatexCompiler.valueOf(compilerName);
        }
        catch (IllegalArgumentException e) {
            this.compiler = null;
        }

        // Read compiler custom path.
        String compilerPathRead = parent.getChildText(COMPILER_PATH);
        this.compilerPath = (compilerPathRead == null || compilerPathRead.isEmpty()) ? null : compilerPathRead;

        // Read main file.
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        String filePath = parent.getChildText(MAIN_FILE);
        this.mainFile = fileSystem.findFileByPath(filePath);

        // Read auxiliary directories.
        String auxDirBoolean = parent.getChildText(AUX_DIR);
        this.auxDir = Boolean.parseBoolean(auxDirBoolean);

        // Read output format.
        Format format = Format.byNameIgnoreCase(parent.getChildText(OUTPUT_FORMAT));
        this.outputFormat = format == null ? Format.PDF : format;
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);

        Element parent = element.getChild(TEXIFY_PARENT);

        // Create a new parent when there is no parent present.
        if (parent == null) {
            parent = new Element(TEXIFY_PARENT);
            element.addContent(parent);
        }
        // Otherwise overwrite (remove + write).
        else {
            parent.removeContent();
        }

        // Write compiler.
        final Element compilerElt = new Element(COMPILER);
        compilerElt.setText(compiler == null ? "" : compiler.name());
        parent.addContent(compilerElt);

        // Write compiler path.
        final Element compilerPathElt = new Element(COMPILER_PATH);
        compilerPathElt.setText(compilerPath == null ? "" : compilerPath);
        parent.addContent(compilerPathElt);

        // Write main file.
        final Element mainFileElt = new Element(MAIN_FILE);
        mainFileElt.setText((mainFile == null ? "" : mainFile.getPath()));
        parent.addContent(mainFileElt);

        // Write auxiliary directories.
        final Element auxDirElt = new Element(AUX_DIR);
        auxDirElt.setText(Boolean.toString(auxDir));
        parent.addContent(auxDirElt);

        // Write output format.
        final Element outputFormatElt = new Element(OUTPUT_FORMAT);
        outputFormatElt.setText(outputFormat.name());
        parent.addContent(outputFormatElt);
    }

    public LatexCompiler getCompiler() {
        return compiler;
    }

    public void setCompiler(LatexCompiler compiler) {
        this.compiler = compiler;
    }

    public VirtualFile getMainFile() {
        return this.mainFile;
    }

    /**
     * Looks up the corresponding {@link VirtualFile} and calls {@link
     * LatexRunConfiguration#getMainFile()}.
     */
    public void setMainFile(String mainFilePath) {
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        setMainFile(fileSystem.findFileByPath(mainFilePath));
    }

    public void setMainFile(VirtualFile mainFile) {
        this.mainFile = mainFile;
    }

    public boolean hasAuxiliaryDirectories() {
        return this.auxDir;
    }

    public void setAuxiliaryDirectories(boolean auxDir) {
        this.auxDir = auxDir;
    }

    public Format getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(Format format) {
        this.outputFormat = format;
    }

    public void setDefaultCompiler() {
        setCompiler(LatexCompiler.PDFLATEX);
    }

    public void setDefaultAuxiliaryDirectories() {
        setAuxiliaryDirectories(true);
    }

    public void setDefaultOutputFormat() {
        setOutputFormat(Format.PDF);
    }

    public void setSuggestedName() {
        setName(suggestedName());
    }

    public String getCompilerPath() {
        return compilerPath;
    }

    public void setCompilerPath(String compilerPath) {
        this.compilerPath = compilerPath;
    }

    @Override
    public boolean isGeneratedName() {
        if (mainFile == null) {
            return false;
        }

        String name = mainFile.getNameWithoutExtension();
        return name != null && name.equals(getName());
    }

    @Override
    public String getOutputFilePath() {
        return ProjectRootManager.getInstance(getProject()).getFileIndex().getContentRootForFile(mainFile).getPath() + "/out/" + mainFile.getNameWithoutExtension() + "." + outputFormat.toString().toLowerCase();
    }

    @Override
    public void setFileOutputPath(String fileOutputPath) { }

    @Nullable
    @Override
    public String suggestedName() {
        if (mainFile == null) {
            return null;
        }

        return mainFile.getNameWithoutExtension();
    }

    @Override
    public String toString() {
        return "LatexRunConfiguration{" + "compiler=" + compiler +
                ", compilerPath=" + compilerPath +
                ", mainFile=" + mainFile +
                ", auxDir=" + auxDir +
                ", outputFormat=" + outputFormat +
                '}';
    }
}

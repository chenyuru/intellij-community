package com.intellij.jar;

import com.intellij.ide.IdeBundle;
import com.intellij.j2ee.make.*;
import com.intellij.j2ee.module.LibraryLink;
import com.intellij.j2ee.module.ModuleContainer;
import com.intellij.j2ee.module.ModuleLink;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.WindowManager;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

/**
 * @author cdr
 */
public class BuildJarProjectSettings implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.BuildJarProjectSettings");

  public boolean BUILD_JARS_ON_MAKE = false;
  private @NonNls static final String MAIN_CLASS = Attributes.Name.MAIN_CLASS.toString();
  private @NonNls static final String JAR_EXTENSION = ".jar";
  private final Project myProject;

  public static BuildJarProjectSettings getInstance(Project project) {
    return project.getComponent(BuildJarProjectSettings.class);
  }

  public BuildJarProjectSettings(Project project) {
    myProject = project;
  }

  public boolean isBuildJar() {
    return BUILD_JARS_ON_MAKE;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }


  public String getComponentName() {
    return "BuildJarProjectSettings";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void setBuildJar(final boolean buildJar) {
    BUILD_JARS_ON_MAKE = buildJar;
  }

  public void buildJarsWithProgress() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
      public void run() {
        buildJars(ProgressManager.getInstance().getProgressIndicator());
      }
    }, IdeBundle.message("jar.build.progress.title"), true, myProject);
  }
  public void buildJars(final ProgressIndicator progressIndicator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    try {
      for (Module module : modules) {
        BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
        if (buildJarSettings == null || !buildJarSettings.isBuildJar()) continue;
        String presentableJarPath = "'"+buildJarSettings.getJarPath()+"'";
        progressIndicator.setText(IdeBundle.message("jar.build.progress", presentableJarPath));
        buildJar(module, buildJarSettings,progressIndicator);
        WindowManager.getInstance().getStatusBar(myProject).setInfo(IdeBundle.message("jar.build.success.message", presentableJarPath));
      }
    }
    catch (ProcessCanceledException e) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo(IdeBundle.message("jar.build.cancelled"));
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, e.toString(), IdeBundle.message("jar.build.error.title"));
    }
  }

  private static void buildJar(final Module module, final BuildJarSettings buildJarSettings, final ProgressIndicator progressIndicator) throws IOException {
    ModuleContainer moduleContainer = buildJarSettings.getModuleContainer();
    String jarPath = buildJarSettings.getJarPath();
    final File jarFile = new File(jarPath);
    jarFile.delete();

    FileUtil.createParentDirs(jarFile);
    BuildRecipe buildRecipe = MakeUtil.getInstance().createBuildRecipe();
    LibraryLink[] libraries = moduleContainer.getContainingLibraries();
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    for (LibraryLink libraryLink : libraries) {
      MakeUtil.getInstance().addLibraryLink(compileContext, buildRecipe, libraryLink, module, null);
    }
    ModuleLink[] modules = moduleContainer.getContainingModules();
    MakeUtil.getInstance().addJavaModuleOutputs(module, modules, buildRecipe, compileContext, null);
    Manifest manifest = MakeUtil.getInstance().createManifest(buildRecipe);
    String mainClass = buildJarSettings.getMainClass();
    if (manifest != null && !Comparing.strEqual(mainClass, null)) {
      manifest.getMainAttributes().putValue(MAIN_CLASS,mainClass);
    }

    // write temp file and rename it to the jar to avoid deployment of incomplete jar. SCR #30303
    final File tempFile = File.createTempFile("___"+ FileUtil.getNameWithoutExtension(jarFile), JAR_EXTENSION, jarFile.getParentFile());
    final JarOutputStream jarOutputStream = manifest == null ?
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile))) :
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)), manifest);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    final BuildRecipe dependencies = MakeUtil.getInstance().createBuildRecipe();
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          ProgressManager.getInstance().checkCanceled();
          if (instruction instanceof FileCopyInstruction) {
            FileCopyInstruction fileCopyInstruction = (FileCopyInstruction)instruction;
            File file = fileCopyInstruction.getFile();
            if (file == null || !file.exists()) return true;
            String presentablePath = FileUtil.toSystemDependentName(file.getPath());
            progressIndicator.setText2(IdeBundle.message("jar.build.processing.file.progress", presentablePath));
          }
          instruction.addFilesToJar(compileContext, tempFile, jarOutputStream, dependencies, tempWrittenRelativePaths, null);
          return true;
        }
      }, false);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      jarOutputStream.close();
      try {
        FileUtil.rename(tempFile, jarFile);
      }
      catch (IOException e) {
        String message = IdeBundle.message("jar.build.cannot.overwrite.error", FileUtil.toSystemDependentName(jarFile.getPath()),
                                           FileUtil.toSystemDependentName(tempFile.getPath()));
        Messages.showErrorDialog(module.getProject(), message, IdeBundle.message("jar.build.error.title"));
      }
    }
  }
}

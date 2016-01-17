package com.darylteo.gradle.javassist.tasks;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import javassist.NotFoundException;
import javassist.build.IClassTransformer;
import org.gradle.api.GradleException;

import java.io.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by dteo on 30/05/2014.
 */
class TransformationAction {

  private File destinationDir;
  private IClassTransformer transformation;

  private List<File> sources = new LinkedList<File>();
  private Collection<File> classpath = new LinkedList<File>();

  public TransformationAction(File destinationDir, Collection<File> sources, Collection<File> classpath, IClassTransformer transformation) {
    this.destinationDir = destinationDir;
    this.sources.addAll(sources);
    this.classpath.addAll(classpath);
    this.transformation = transformation;
  }

  public boolean execute() {
    // no op if no transformation defined
    if (this.transformation == null) {
      System.out.println("No transformation defined for this task");
      return false;
    }

    if (this.sources == null || this.sources.size() == 0) {
      System.out.println("No source files.");
      return false;
    }

    if (destinationDir == null) {
      System.out.println("No destination directory set");
      return false;
    }

    try {
      final List<CtClass> loadedClasses = preloadClasses();

      this.process(loadedClasses);
    } catch (Exception e) {
      throw new GradleException("Could not execute transformation", e);
    }

    return true;
  }

  private List<CtClass> preloadClasses() throws NotFoundException, IOException {
    final List<CtClass> loadedClasses = new LinkedList<CtClass>();
    final ClassPool pool = new AnnotationLoadingClassPool();

    // set up the classpath for the classpool
    if (classpath != null) {
      for (File f : this.classpath) {
        pool.appendClassPath(f.toString());
      }
    }

    // add the files to process
    for (File f : this.sources) {
      if (!f.isDirectory()) {
        loadedClasses.add(loadClassFile(pool, f));
      }
    }

    return loadedClasses;
  }

  public void process(Collection<CtClass> classes) {
    for (CtClass clazz : classes) {
      processClass(clazz);
    }
  }

  public void processClass(CtClass clazz) {
    try {
      if (transformation.shouldTransform(clazz)) {
        clazz.defrost();
        transformation.applyTransformations(clazz);
        clazz.writeFile(this.destinationDir.toString());
      }
    } catch (Exception e) {
      throw new GradleException("An error occurred while trying to process class file ", e);
    }
  }

  private CtClass loadClassFile(ClassPool pool, File classFile) throws IOException {
    // read the file first to get the classname
    // much easier than trying to extrapolate from the filename (i.e. with anonymous classes etc.)
    InputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(classFile)));
    CtClass clazz = pool.makeClass(stream);

    stream.close();

    return clazz;
  }

  /**
   * This class loader will load annotations encountered in loaded classes
   * using the pool itself.
   * @see <a href="https://github.com/jboss-javassist/javassist/pull/18">Javassist issue 18</a>
   */
  private static class AnnotationLoadingClassPool extends ClassPool {
    public AnnotationLoadingClassPool() {
      super(true);
    }

    @Override public ClassLoader getClassLoader() {
      return new Loader(this);
    }
  }
}

/*
 * Copyright (C) 2022 - 2022 Ashley Scopes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ascopes.jct.compilers.impl;

import io.github.ascopes.jct.compilers.AbstractJctCompiler;
import io.github.ascopes.jct.compilers.FileManagerBuilder;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;

/**
 * Implementation of a {@code javac} compiler.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
@API(since = "0.0.1", status = Status.INTERNAL)
public final class JavacJctCompilerImpl extends AbstractJctCompiler<JavacJctCompilerImpl> {

  private static final String NAME = "JDK Compiler";


  /**
   * Initialize a new Java compiler.
   */
  public JavacJctCompilerImpl() {
    this(ToolProvider.getSystemJavaCompiler());
  }

  /**
   * Initialize a new Java compiler.
   *
   * @param jsr199Compiler the JSR-199 compiler backend to use.
   */
  public JavacJctCompilerImpl(JavaCompiler jsr199Compiler) {
    this(NAME, jsr199Compiler);
  }

  /**
   * Initialize a new Java compiler.
   *
   * @param name the name to give the compiler.
   */
  public JavacJctCompilerImpl(String name) {
    this(name, ToolProvider.getSystemJavaCompiler());
  }

  /**
   * Initialize a new Java compiler.
   *
   * @param name           the name to give the compiler.
   * @param jsr199Compiler the JSR-199 compiler backend to use.
   */
  public JavacJctCompilerImpl(String name, JavaCompiler jsr199Compiler) {
    super(name, new FileManagerBuilder(), jsr199Compiler, new JavacJctFlagBuilderImpl());
    addCompilerOptions("-implicit:class");
  }

  @Override
  public String getDefaultRelease() {
    return Integer.toString(getLatestSupportedVersionInt());
  }

  /**
   * Get the maximum version of Javac that is supported.
   */
  public static int getLatestSupportedVersionInt() {
    return SourceVersion.latestSupported().ordinal();
  }
}
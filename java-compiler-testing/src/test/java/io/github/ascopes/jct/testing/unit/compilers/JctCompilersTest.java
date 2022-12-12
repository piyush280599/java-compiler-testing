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
package io.github.ascopes.jct.testing.unit.compilers;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ascopes.jct.compilers.JctCompilers;
import io.github.ascopes.jct.compilers.javac.JavacJctCompilerImpl;
import io.github.ascopes.jct.testing.helpers.StaticClassTestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link JctCompilers} tests.
 *
 * @author Ashley Scopes
 */
@DisplayName("JctCompilers tests")
class JctCompilersTest implements StaticClassTestTemplate {

  @Override
  public Class<?> getTypeBeingTested() {
    return JctCompilers.class;
  }

  @DisplayName("createPlatformCompiler() creates a JavacJctCompilerImpl instance")
  @Test
  void createPlatformCompilerReturnsTheExpectedInstance() {
    try (var javacJctCompilerImplMock = Mockito.mockConstruction(JavacJctCompilerImpl.class)) {
      // When
      var compiler = JctCompilers.createPlatformCompiler();

      // Then
      assertThat(compiler)
          .isInstanceOf(JavacJctCompilerImpl.class);

      assertThat(javacJctCompilerImplMock.constructed())
          .singleElement()
          // Nested assertion to swap expected/actual args.
          .satisfies(constructed -> assertThat(compiler).isSameAs(constructed));
    }
  }
}

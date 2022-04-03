/*
 * Copyright (C) 2022 Ashley Scopes
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

package com.github.ascopes.jct.compilers;


import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;

/**
 * Function representing a configuration operation that can be applied to a compiler.
 *
 * <p>This can allow encapsulating common configuration logic across tests into a single place.
 *
 * @param <C> the compiler type.
 * @author Ashley Scopes
 * @since 0.0.1
 */
@API(since = "0.0.1", status = Status.EXPERIMENTAL)
@FunctionalInterface
public interface CompilerConfigurer<C extends Compiler<C, ?>> {

  /**
   * Apply configuration logic to the given compiler.
   *
   * @param compiler the compiler.
   */
  void configure(C compiler);
}

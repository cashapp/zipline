/*
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.kotlin

import org.jetbrains.kotlin.ir.types.IrType

/**
 * Type information that instances of `ZiplineServiceAdapter` instances need at construction time.
 * Use [BridgedInterface.adapterConstructorArguments] to create an instance.
 */
class AdapterConstructorArguments(
  /** Types that may use of type variables. These vary with the class generic parameters. */
  val reifiedTypes: List<IrType>,

  /** Types that implement `ZiplineService`. */
  val ziplineServiceTypes: List<IrType>,
)

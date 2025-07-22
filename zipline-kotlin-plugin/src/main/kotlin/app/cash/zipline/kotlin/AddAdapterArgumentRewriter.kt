/*
 * Copyright (C) 2021 Square, Inc.
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
@file:OptIn(DeprecatedForRemovalCompilerApi::class)

package app.cash.zipline.kotlin

import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

/**
 * Rewrites calls to `Zipline.bind()` and `Zipline.take()` to also pass an additional argument, the
 * generated `ZiplineServiceAdapter`.
 *
 * This call:
 *
 * ```
 * val helloService: SampleService = zipline.take(
 *   "helloService",
 *   myZiplineScope,
 * )
 * ```
 *
 * is rewritten to:
 *
 * ```
 * val helloService: SampleService = zipline.take(
 *   "helloService",
 *   myZiplineScope,
 *   SampleService.Companion.Adapter,
 * )
 * ```
 *
 * This rewrites all calls specified by [ZiplineApis.ziplineServiceAdapterFunctions]
 */
internal class AddAdapterArgumentRewriter(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
  private val rewrittenFunction: IrSimpleFunctionSymbol,
) {
  /** The user-defined interface type, like `SampleService` above. */
  private val bridgedInterfaceType: IrType = original.getTypeArgument(0)!!

  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    ziplineApis,
    scope,
    original,
    "Zipline.${original.symbol.owner.name.identifier}()",
    bridgedInterfaceType,
  )

  fun rewrite(): IrCall {
    val adapterExpression = AdapterGenerator(
      pluginContext,
      ziplineApis,
      scope,
      bridgedInterface.typeIrClass,
    ).adapterExpression(bridgedInterfaceType as IrSimpleType)

    return irCall(original, rewrittenFunction).apply {
      arguments += adapterExpression
      patchDeclarationParents(declarationParent)
    }
  }
}

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
package app.cash.zipline.kotlin

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

/**
 * Rewrites calls to `Zipline.getService()` and `Zipline.setService()` to also pass an additional
 * argument, the generated `ZiplineServiceAdapter`.
 *
 * This call:
 *
 * ```
 * val helloService: SampleService = zipline.getService(
 *   "helloService"
 * )
 * ```
 *
 * is rewritten to:
 *
 * ```
 * val helloService: SampleService = zipline.getService(
 *   "helloService",
 *   SampleService.Companion.Adapter
 * )
 * ```
 *
 * This also rewrites calls on `Endpoint`.
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
    original,
    "Zipline.${original.symbol.owner.name.identifier}()",
    bridgedInterfaceType
  )

  fun rewrite(): IrCall {
    // Make sure there's an adapter for this class so we have something to reference.
    val adapterClass = AdapterGenerator(
      pluginContext,
      ziplineApis,
      bridgedInterface.typeIrClass
    ).generateAdapterIfAbsent()

    return irCall(original, rewrittenFunction).apply {
      val irBlockBodyBuilder = irBlockBodyBuilder(pluginContext, scope, original)
      putValueArgument(valueArgumentsCount - 1, irBlockBodyBuilder.irGetObject(adapterClass.symbol))
      patchDeclarationParents(declarationParent)
    }
  }
}

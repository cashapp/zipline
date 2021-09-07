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
package app.cash.zipline.ktbridge.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName

/** Looks up APIs used by the code rewriters. */
internal class KtBridgeApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")
  private val ziplineFqName = packageFqName.child("Zipline")
  private val ziplineCompanionFqName = ziplineFqName.child("Companion")
  private val ktBridgeFqName = bridgeFqName.child("KtBridge")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(FqName("kotlin.Any"))!!

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializationModulesFqName.child("SerializersModule"))!!

  val inboundCall: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("InboundCall"))!!

  val inboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("InboundCall").child("parameter"))
      .single { it.owner.isInline }

  val inboundCallResult: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("InboundCall").child("result"))
      .single { it.owner.isInline }

  val inboundCallUnexpectedFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCall").child("unexpectedFunction")
    ).single()

  val inboundService: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("InboundService"))!!

  val inboundServiceCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundService").child("call")
    ).single()

  val inboundServiceCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundService").child("callSuspending")
    ).single()

  val outboundCallInvoke: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("invoke"))
      .single { it.owner.valueParameters.isEmpty() }

  val outboundCallInvokeSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("OutboundCall").child("invokeSuspending")
    ).single { it.owner.valueParameters.isEmpty() }

  val outboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("parameter"))
      .single { it.owner.valueParameters.size == 1 }

  val outboundCallFactory: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("OutboundCall").child("Factory"))!!

  val outboundCallFactoryCreate: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("OutboundCall").child("Factory").child("create")
    ).single()

  val outboundClientFactory: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("OutboundClientFactory"))!!

  val outboundClientFactoryCreate: IrSimpleFunctionSymbol
    get() = outboundClientFactory.functions.single { it.owner.name.identifier == "create" }

  /** Keys are functions like `Zipline.get()` and values are their rewrite targets. */
  val getRewriteFunctions: Map<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> = buildRewritesMap(
    ziplineFqName.child("get"),
    ziplineCompanionFqName.child("get"),
    ktBridgeFqName.child("get"),
  )

  /** Keys are functions like `Zipline.set()` and values are their rewrite targets. */
  val setRewriteFunctions: Map<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> = buildRewritesMap(
    ziplineFqName.child("set"),
    ziplineCompanionFqName.child("set"),
    ktBridgeFqName.child("set"),
  )

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun buildRewritesMap(
    vararg functionNames: FqName
  ): Map<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> {
    val result = mutableMapOf<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol>()
    for (functionName in functionNames) {
      val overloads = pluginContext.referenceFunctions(functionName)
      if (overloads.isEmpty()) continue // The Companion APIs are JS-only.
      val original = overloads.single {
        it.owner.valueParameters[1].type == serializersModule.defaultType
      }
      val target = overloads.single { it != original }
      result[original] = target
    }
    return result
  }
}

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
package app.cash.quickjs.ktbridge.plugin

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
  private val packageFqName = FqName("app.cash.quickjs")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(FqName("kotlin.Any"))!!

  val jsAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("JsAdapter"))!!

  val inboundCall: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("InboundCall"))!!

  val inboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(packageFqName.child("InboundCall").child("parameter"))
      .single { it.owner.isInline }

  val inboundCallResult: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(packageFqName.child("InboundCall").child("result"))
      .single { it.owner.isInline }

  val inboundCallUnexpectedFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      packageFqName.child("InboundCall").child("unexpectedFunction")
    ).single()

  val inboundService: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("InboundService"))!!

  val inboundServiceCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      packageFqName.child("InboundService").child("call")
    ).single()

  val outboundCallInvoke: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(packageFqName.child("OutboundCall").child("invoke"))
      .single { it.owner.valueParameters.isEmpty() }

  val outboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(packageFqName.child("OutboundCall").child("parameter"))
      .single { it.owner.valueParameters.size == 1 }

  val outboundCallFactory: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("OutboundCall").child("Factory"))!!

  val outboundCallFactoryCreate: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      packageFqName.child("OutboundCall").child("Factory").child("create")
    ).single()

  val outboundClientFactory: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("OutboundClientFactory"))!!

  val outboundClientFactoryCreate: IrSimpleFunctionSymbol
    get() = outboundClientFactory.functions.single { it.owner.name.identifier == "create" }

  val getFunctions: Collection<IrSimpleFunctionSymbol>
    get() = pluginContext.referenceFunctions(packageFqName.child("QuickJs").child("get"))

  val publicGetFunction: IrSimpleFunctionSymbol
    get() = getFunctions.single { it.owner.valueParameters[1].type == jsAdapter.defaultType }

  val rewrittenGetFunction: IrSimpleFunctionSymbol
    get() = getFunctions.single { it.owner.valueParameters[1].type != jsAdapter.defaultType && !it.owner.isOperator }

  val setFunctions: Collection<IrSimpleFunctionSymbol>
    get() = pluginContext.referenceFunctions(packageFqName.child("QuickJs").child("set"))

  val publicSetFunction: IrSimpleFunctionSymbol
    get() = setFunctions.single { it.owner.valueParameters[1].type == jsAdapter.defaultType }

  val rewrittenSetFunction: IrSimpleFunctionSymbol
    get() = setFunctions.single { it.owner.valueParameters[1].type != jsAdapter.defaultType && !it.owner.isOperator }
}

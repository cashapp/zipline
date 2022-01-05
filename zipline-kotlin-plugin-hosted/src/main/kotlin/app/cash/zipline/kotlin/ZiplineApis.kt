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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val serializationFqName = FqName("kotlinx.serialization")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")
  private val serializersModuleFqName = serializationModulesFqName.child("SerializersModule")
  private val ziplineFqName = packageFqName.child("Zipline")
  val ziplineReferenceFqName = packageFqName.child("ZiplineReference")
  val ziplineServiceFqName = packageFqName.child("ZiplineService")
  private val ziplineServiceAdapterFunctionFqName = bridgeFqName.child("ziplineServiceAdapter")
  private val ziplineServiceAdapterFqName = bridgeFqName.child("ZiplineServiceAdapter")
  private val endpointFqName = bridgeFqName.child("Endpoint")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(FqName("kotlin.Any"))!!

  val stringArrayType: IrType =
    pluginContext.symbols.array.typeWith(pluginContext.symbols.string.defaultType)

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqName.child("KSerializer"))!!

  val serializerFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.child("serializer"))
      .single {
        it.owner.extensionReceiverParameter?.type?.classFqName == serializersModuleFqName &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val endpointZiplineReferenceSerializer: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(endpointFqName.child("ziplineReferenceSerializer"))
      .single()

  val inboundCall: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("InboundCall"))!!

  val inboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("InboundCall").child("parameter"))
      .single()

  val inboundCallResult: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("InboundCall").child("result"))
      .single()

  val inboundCallUnexpectedFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCall").child("unexpectedFunction")
    ).single()

  val inboundBridgeContextFqName = bridgeFqName.child("InboundBridge").child("Context")

  val inboundBridgeContext: IrClassSymbol
    get() = pluginContext.referenceClass(inboundBridgeContextFqName)!!

  val inboundBridgeContextSerializersModule: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      inboundBridgeContextFqName.child("serializersModule")
    ).single()

  val inboundBridgeContextEndpoint: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      inboundBridgeContextFqName.child("endpoint")
    ).single()

  val inboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("InboundCallHandler"))!!

  val inboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCallHandler").child("call")
    ).single()

  val inboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCallHandler").child("callSuspending")
    ).single()

  val inboundCallHandlerContext: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      bridgeFqName.child("InboundCallHandler").child("context")
    ).single()

  val outboundCallInvoke: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("invoke"))
      .single()

  val outboundCallInvokeSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("OutboundCall").child("invokeSuspending")
    ).single()

  val outboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("parameter"))
      .single()

  val outboundBridgeContextFqName = bridgeFqName.child("OutboundBridge").child("Context")

  val outboundBridgeContext: IrClassSymbol
    get() = pluginContext.referenceClass(outboundBridgeContextFqName)!!

  val outboundBridgeContextNewCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundBridgeContextFqName.child("newCall")
    ).single()

  val outboundBridgeContextSerializersModule: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundBridgeContextFqName.child("serializersModule")
    ).single()

  val outboundBridgeContextEndpoint: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundBridgeContextFqName.child("endpoint")
    ).single()

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterFqName)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterFqName.child("serialName")
    ).single()

  val ziplineServiceAdapterInboundCallHandler: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "inboundCallHandler"
    }

  val ziplineServiceAdapterOutboundService: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "outboundService"
    }

  /** Keys are functions like `Zipline.get()` and values are their rewrite targets. */
  val ziplineServiceAdapterFunctions: Map<IrFunctionSymbol, IrSimpleFunctionSymbol> = listOf(
    rewritePair(ziplineFqName.child("get")),
    rewritePair(endpointFqName.child("get")),
    rewritePair(ziplineFqName.child("set")),
    rewritePair(endpointFqName.child("set")),
    rewritePair(ziplineReferenceFqName),
    rewritePair(ziplineServiceAdapterFunctionFqName),
  ).toMap()

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: FqName): Pair<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.valueParameters.lastOrNull()?.type?.classFqName == ziplineServiceAdapterFqName
    }
    val original = overloads.single { it != rewriteTarget }
    return original to rewriteTarget
  }
}

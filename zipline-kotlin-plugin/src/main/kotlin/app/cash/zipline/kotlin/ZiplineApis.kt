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
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.FqName

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val serializationFqName = FqName("kotlinx.serialization")
  val contextualFqName = serializationFqName.child("Contextual")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")
  private val serializersModuleFqName = serializationModulesFqName.child("SerializersModule")
  private val ziplineFqName = packageFqName.child("Zipline")
  val ziplineServiceFqName = packageFqName.child("ZiplineService")
  private val ziplineServiceSerializerFunctionFqName =
    packageFqName.child("ziplineServiceSerializer")
  private val ziplineServiceAdapterFunctionFqName = bridgeFqName.child("ziplineServiceAdapter")
  private val ziplineServiceAdapterFqName = bridgeFqName.child("ZiplineServiceAdapter")
  private val endpointFqName = bridgeFqName.child("Endpoint")
  private val suspendCallbackFqName = bridgeFqName.child("SuspendCallback")
  val flowFqName = FqName("kotlinx.coroutines.flow").child("Flow")
  private val collectionsFqName = FqName("kotlin.collections")
  private val listFqName = collectionsFqName.child("List")
  private val reflectFqName = FqName("kotlin.reflect")
  private val ktypeFqName = reflectFqName.child("KType")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(FqName("kotlin.Any"))!!

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqName.child("KSerializer"))!!

  val kType: IrClassSymbol
    get() = pluginContext.referenceClass(ktypeFqName)!!

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializersModuleFqName)!!

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.child("Map"))!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(listFqName)!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  val serializerFunctionTypeParam: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.child("serializer"))
      .single {
        it.owner.extensionReceiverParameter?.type?.classFqName == serializersModuleFqName &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val serializerFunctionNoReceiver: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.child("serializer"))
      .single {
        it.owner.extensionReceiverParameter == null &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val requireContextual: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("requireContextual"))
      .single()

  /** This symbol for `ziplineServiceSerializer(KClass<*>, List<KSerializer<*>>)`. */
  val ziplineServiceSerializerTwoArg: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(ziplineServiceSerializerFunctionFqName)
      .single { it.owner.valueParameters.size == 2 }

  val listOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsFqName.child("listOf"))
      .single { it.owner.valueParameters.firstOrNull()?.isVararg == true }

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      collectionsFqName.child("List").child("get")
    ).single()

  val ziplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.child("ZiplineFunction"))!!

  val returningZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("ReturningZiplineFunction"))!!

  val suspendingZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("SuspendingZiplineFunction"))!!

  val returningZiplineFunctionCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("ReturningZiplineFunction").child("call")
    ).single()

  val suspendingZiplineFunctionCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("SuspendingZiplineFunction").child("callSuspending")
    ).single()

  val outboundCallHandlerFqName = bridgeFqName.child("OutboundCallHandler")

  val outboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(outboundCallHandlerFqName)!!

  val outboundCallHandlerClosed: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundCallHandlerFqName.child("closed")
    ).single()

  val outboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerFqName.child("call")
    ).single()

  val outboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerFqName.child("callSuspending")
    ).single()

  val ziplineService: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceFqName)!!

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterFqName)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterFqName.child("serialName")
    ).single()

  val ziplineServiceAdapterSerializers: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterFqName.child("serializers")
    ).single()

  val ziplineServiceAdapterZiplineFunctions: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "ziplineFunctions"
    }

  val ziplineServiceAdapterOutboundService: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "outboundService"
    }

  val suspendCallback: IrClassSymbol
    get() = pluginContext.referenceClass(suspendCallbackFqName)!!

  /** Keys are functions like `Zipline.take()` and values are their rewrite targets. */
  val ziplineServiceAdapterFunctions: Map<IrFunctionSymbol, IrSimpleFunctionSymbol> = listOf(
    rewritePair(ziplineFqName.child("take")),
    rewritePair(endpointFqName.child("take")),
    rewritePair(ziplineFqName.child("bind")),
    rewritePair(endpointFqName.child("bind")),
    rewritePair(ziplineServiceAdapterFunctionFqName),
    rewritePair(ziplineServiceSerializerFunctionFqName),
  ).toMap()

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: FqName): Pair<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.valueParameters.lastOrNull()?.type?.classFqName == ziplineServiceAdapterFqName
    }
    val original = overloads.single {
      it.owner.valueParameters.size + 1 == rewriteTarget.owner.valueParameters.size
    }
    return original to rewriteTarget
  }
}

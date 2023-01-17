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
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val collectionsFqName = FqName("kotlin.collections")
  private val serializationFqName = FqName("kotlinx.serialization")
  val contextualClassId = serializationFqName.classId("Contextual")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")
  private val serializersModuleClassId = serializationModulesFqName.classId("SerializersModule")
  private val ziplineClassId = packageFqName.classId("Zipline")
  private val outboundServiceClassId = bridgeFqName.classId("OutboundService")
  val ziplineScopedClassId = packageFqName.classId("ZiplineScoped")
  val ziplineServiceClassId = packageFqName.classId("ZiplineService")
  private val ziplineServiceSerializerFunctionCallableId = packageFqName.callableId("ziplineServiceSerializer")
  private val ziplineServiceAdapterFunctionCallableId = bridgeFqName.callableId("ziplineServiceAdapter")
  private val ziplineServiceAdapterClassId = bridgeFqName.classId("ZiplineServiceAdapter")
  private val endpointClassId = bridgeFqName.classId("Endpoint")
  private val suspendCallbackClassId = bridgeFqName.classId("SuspendCallback")
  val flowClassId = FqName("kotlinx.coroutines.flow").classId("Flow")
  private val anyClassId = FqName("kotlin").classId("Any")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(anyClassId)!!

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqName.classId("KSerializer"))!!

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializersModuleClassId)!!

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.classId("Map"))!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.classId("List"))!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  val serializerFunctionTypeParam: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.callableId("serializer"))
      .single {
        it.owner.extensionReceiverParameter?.type?.classId == serializersModuleClassId &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val serializerFunctionNoReceiver: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.callableId("serializer"))
      .single {
        it.owner.extensionReceiverParameter == null &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val requireContextual: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.callableId("requireContextual")
    ).single()

  /** This symbol for `ziplineServiceSerializer(KClass<*>, List<KSerializer<*>>)`. */
  val ziplineServiceSerializerTwoArg: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(ziplineServiceSerializerFunctionCallableId)
      .single { it.owner.valueParameters.size == 2 }

  val listOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsFqName.callableId("listOf"))
      .single { it.owner.valueParameters.firstOrNull()?.isVararg == true }

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      collectionsFqName.classId("List").callableId("get")
    ).single()

  val ziplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.classId("ZiplineFunction"))!!

  val returningZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.classId("ReturningZiplineFunction"))!!

  val suspendingZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.classId("SuspendingZiplineFunction"))!!

  val returningZiplineFunctionCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.classId("ReturningZiplineFunction").callableId("call")
    ).single()

  val suspendingZiplineFunctionCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.classId("SuspendingZiplineFunction").callableId("callSuspending")
    ).single()

  private val outboundCallHandlerClassId = bridgeFqName.classId("OutboundCallHandler")

  val outboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(outboundCallHandlerClassId)!!

  val outboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.callableId("call")
    ).single()

  val outboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.callableId("callSuspending")
    ).single()

  val outboundService: IrClassSymbol
    get() = pluginContext.referenceClass(outboundServiceClassId)!!

  val outboundServiceCallHandler: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundServiceClassId.callableId("callHandler")
    ).single()

  val ziplineService: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceClassId)!!

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterClassId)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.callableId("serialName")
    ).single()

  val ziplineServiceAdapterSerializers: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.callableId("serializers")
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
    get() = pluginContext.referenceClass(suspendCallbackClassId)!!

  /** Keys are renderings of functions like `Zipline.take()` and values are their rewrite targets. */
  val ziplineServiceAdapterFunctions: Map<String, IrSimpleFunctionSymbol> = listOf(
    rewritePair(ziplineClassId.callableId("take")),
    rewritePair(endpointClassId.callableId("take")),
    rewritePair(ziplineClassId.callableId("bind")),
    rewritePair(endpointClassId.callableId("bind")),
    rewritePair(ziplineServiceAdapterFunctionCallableId),
    rewritePair(ziplineServiceSerializerFunctionCallableId),
  ).toMap()

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: CallableId): Pair<String, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.valueParameters.lastOrNull()?.type?.classId == ziplineServiceAdapterClassId
    }
    val original = overloads.single {
      it.owner.valueParameters.size + 1 == rewriteTarget.owner.valueParameters.size
    }
    return original.toString() to rewriteTarget
  }
}

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
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.*

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val serializationFqName = FqName("kotlinx.serialization")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")

  private val ziplineServiceSerializerFunctionCallableId =
    packageFqName.childFunctionId("ziplineServiceSerializer")
  private val ziplineServiceAdapterFunctionFqName = bridgeFqName.childFunctionId("ziplineServiceAdapter")

  val flowFqName = FqName("kotlinx.coroutines.flow").child("Flow")
  private val serializersModuleFqName = serializationModulesFqName.child("SerializersModule")
  val ziplineScopedFqName = packageFqName.child("ZiplineScoped")
  val contextualFqName = serializationFqName.child("Contextual")

  private val outboundServiceClassId = bridgeFqName.childClassId("OutboundService")
  val ziplineServiceClassId = packageFqName.childClassId("ZiplineService")
  private val ziplineServiceAdapterClassId = bridgeFqName.childClassId("ZiplineServiceAdapter")
  private val suspendCallbackClassId= bridgeFqName.childClassId("SuspendCallback")
  private val collectionsClassId = StandardClassIds.BASE_COLLECTIONS_PACKAGE
  private val endpointClassId= bridgeFqName.childClassId("Endpoint")
  private val ziplineClassId= packageFqName.childClassId("Zipline")




  val any: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Any)!!

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqName.childClassId("KSerializer"))!!

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializationModulesFqName.childClassId("SerializersModule"))!!

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Map)!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.List)!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  val serializerFunctionTypeParam: IrSimpleFunctionSymbol
      // should work because this fqname refers to a package as oppose to a class
    get() = pluginContext.referenceFunctions(serializationFqName.childFunctionId("serializer"))
      .single {
        it.owner.extensionReceiverParameter?.type?.classFqName == serializersModuleFqName &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val serializerFunctionNoReceiver: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.childFunctionId("serializer"))
      .single {
        it.owner.extensionReceiverParameter == null &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val requireContextual: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.childFunctionId("requireContextual"))
      .single()

  /** This symbol for `ziplineServiceSerializer(KClass<*>, List<KSerializer<*>>)`. */
  val ziplineServiceSerializerTwoArg: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(ziplineServiceSerializerFunctionCallableId)
      .single { it.owner.valueParameters.size == 2 }

  val listOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsClassId.childFunctionId("listOf"))
      .single { it.owner.valueParameters.firstOrNull()?.isVararg == true }

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      StandardClassIds.List.childFunctionId("get")
    ).single()

  val ziplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(packageFqName.childClassId("ZiplineFunction"))!!

  val returningZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.childClassId("ReturningZiplineFunction"))!!

  val suspendingZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.childClassId("SuspendingZiplineFunction"))!!

  val returningZiplineFunctionCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.childClassId("ReturningZiplineFunction").childFunctionId("call")
    ).single()

  val suspendingZiplineFunctionCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      CallableId(bridgeFqName.childClassId("SuspendingZiplineFunction"), Name.identifier("callSuspending"))
    ).single()

  private val outboundCallHandlerClassId = bridgeFqName.childClassId("OutboundCallHandler")

  val outboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.childClassId("OutboundCallHandler"))!!

  val outboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.childFunctionId("call")
    ).single()

  val outboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.childFunctionId("callSuspending")
    ).single()

  val outboundService: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.childClassId("OutboundService"))!!

  val outboundServiceCallHandler: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundServiceClassId.childFunctionId("callHandler")
    ).single()

  val ziplineService: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceClassId)!!

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterClassId)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.childFunctionId("serialName")
    ).single()

  val ziplineServiceAdapterSerializers: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.childFunctionId("serializers")
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
    rewritePair(ziplineClassId.childFunctionId("take")),
    rewritePair(endpointClassId.childFunctionId("take")),
    rewritePair(ziplineClassId.childFunctionId("bind")),
    rewritePair(endpointClassId.childFunctionId("bind")),
    rewritePair(ziplineServiceAdapterFunctionFqName),
    rewritePair(ziplineServiceSerializerFunctionCallableId),
  ).toMap()

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: CallableId): Pair<String, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.valueParameters.lastOrNull()?.type?.classFqName == ziplineServiceAdapterClassId.asSingleFqName()
    }
    val original = overloads.single {
      it.owner.valueParameters.size + 1 == rewriteTarget.owner.valueParameters.size
    }
    return original.toString() to rewriteTarget
  }
}

fun FqName.childClassId(name: String) = ClassId(this, Name.identifier(name))

// use only when the fqName is a package name
fun FqName.childFunctionId(name: String) = CallableId(this, Name.identifier(name))
fun ClassId.childFunctionId(name: String) = CallableId(this, Name.identifier(name))

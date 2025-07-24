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
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis private constructor(
  private val pluginContext: IrPluginContext,
) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): ZiplineApis? {
      if (pluginContext.referenceClass(ziplineServiceClassId) == null) {
        // If we don't have ZiplineService, we don't have the runtime. Abort!
        return null
      }
      return ZiplineApis(pluginContext)
    }

    private val ziplineFqPackage = FqPackageName("app.cash.zipline")
    private val bridgeFqPackage = FqPackageName("app.cash.zipline.internal.bridge")
    private val serializationFqPackage = FqPackageName("kotlinx.serialization")
    private val serializationBuiltInsFqPackage = FqPackageName("kotlinx.serialization.builtins")
    private val collectionsFqPackage = FqPackageName(StandardClassIds.BASE_COLLECTIONS_PACKAGE)
    val contextualClassId = serializationFqPackage.classId("Contextual")
    private val serializationModulesFqPackage = FqPackageName("kotlinx.serialization.modules")
    private val serializersModuleClassId = serializationModulesFqPackage.classId("SerializersModule")
    private val ziplineClassId = ziplineFqPackage.classId("Zipline")
    private val outboundServiceClassId = bridgeFqPackage.classId("OutboundService")
    val ziplineScopedClassId = ziplineFqPackage.classId("ZiplineScoped")
    val ziplineServiceClassId = ziplineFqPackage.classId("ZiplineService")
    private val ziplineServiceSerializerFunctionCallableId = ziplineFqPackage.callableId("ziplineServiceSerializer")
    private val ziplineServiceAdapterFunctionCallableId = bridgeFqPackage.callableId("ziplineServiceAdapter")
    private val ziplineServiceAdapterClassId = bridgeFqPackage.classId("ZiplineServiceAdapter")
    private val endpointClassId = bridgeFqPackage.classId("Endpoint")
    private val suspendCallbackClassId = bridgeFqPackage.classId("SuspendCallback")
    private val flowFqPackage = FqPackageName("kotlinx.coroutines.flow")
    val flowClassId = flowFqPackage.classId("Flow")
    val stateFlowClassId = flowFqPackage.classId("StateFlow")
  }

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Any)!!

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqPackage.classId("KSerializer"))!!

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializersModuleClassId)!!

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Map)!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.List)!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  /** This symbol for `SerializersModule.serializer<T>()`. */
  val serializerFunctionTypeParam: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqPackage.callableId("serializer"))
      .single {
        it.owner.typeParameters.size == 1 &&
          it.owner.parameters.size == 1 &&
          it.owner.parameters[0].kind == IrParameterKind.ExtensionReceiver &&
          it.owner.parameters[0].type.getClass()?.classId == serializersModuleClassId
      }

  /** This symbol for `serializer<T>()`. */
  val serializerFunctionNoReceiver: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqPackage.callableId("serializer"))
      .single {
        it.owner.parameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val serialNameFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqPackage.callableId("serialName"))
      .single()

  val requireContextual: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqPackage.callableId("requireContextual"),
    ).single()

  val nullableSerializer: IrPropertySymbol
    get() = pluginContext.referenceProperties(serializationBuiltInsFqPackage.callableId("nullable"))
      .single()

  /** The symbol for `ziplineServiceSerializer(KClass<*>, List<KSerializer<*>>)`. */
  val ziplineServiceSerializerTwoArg: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(ziplineServiceSerializerFunctionCallableId)
      .single {
        it.owner.parameters.size == 2
      }

  /** The symbol for `listOf(vararg T)`. */
  val listOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsFqPackage.callableId("listOf"))
      .single { it.owner.parameters.firstOrNull()?.isVararg == true }

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      StandardClassIds.List.callableId("get"),
    ).single()

  val ziplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineFqPackage.classId("ZiplineFunction"))!!

  val returningZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqPackage.classId("ReturningZiplineFunction"))!!

  val suspendingZiplineFunction: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqPackage.classId("SuspendingZiplineFunction"))!!

  val returningZiplineFunctionCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqPackage.classId("ReturningZiplineFunction").callableId("call"),
    ).single()

  val suspendingZiplineFunctionCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqPackage.classId("SuspendingZiplineFunction").callableId("callSuspending"),
    ).single()

  private val outboundCallHandlerClassId = bridgeFqPackage.classId("OutboundCallHandler")

  val outboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(outboundCallHandlerClassId)!!

  val outboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.callableId("call"),
    ).single()

  val outboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundCallHandlerClassId.callableId("callSuspending"),
    ).single()

  val outboundService: IrClassSymbol
    get() = pluginContext.referenceClass(outboundServiceClassId)!!

  val outboundServiceCallHandler: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundServiceClassId.callableId("callHandler"),
    ).single()

  val ziplineService: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceClassId)!!

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterClassId)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.callableId("serialName"),
    ).single()

  val ziplineServiceAdapterSerializers: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.callableId("serializers"),
    ).single()

  val ziplineServiceAdapterSimpleName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterClassId.callableId("simpleName"),
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
  val ziplineServiceAdapterFunctions: Map<String, IrSimpleFunctionSymbol> = mapOf(
    rewritePair(ziplineClassId.callableId("take")),
    rewritePair(endpointClassId.callableId("take")),
    rewritePair(ziplineClassId.callableId("bind")),
    rewritePair(endpointClassId.callableId("bind")),
    rewritePair(ziplineServiceAdapterFunctionCallableId),
    rewritePair(ziplineServiceSerializerFunctionCallableId),
  )

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: CallableId): Pair<String, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.parameters.lastOrNull()?.type?.getClass()?.classId == ziplineServiceAdapterClassId
    }
    val original = overloads.single {
      it.owner.parameters.size + 1 == rewriteTarget.owner.parameters.size
    }
    return original.toString() to rewriteTarget
  }
}

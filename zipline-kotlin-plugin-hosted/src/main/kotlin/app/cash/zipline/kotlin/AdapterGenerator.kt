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
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.Name

/**
 * Adds an `Adapter` nested class to the companion object of an interface that extends
 * `ZiplineService`. See `SampleService.Companion.ManualAdapter` for a sample implementation
 * that this class attempts to generate.
 */
internal class AdapterGenerator(
  private val pluginContext: IrPluginContext,
  private val messageCollector: MessageCollector,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val original: IrClass
) {
  private val irFactory = pluginContext.irFactory
  private val irTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

  /** Returns an expression that references the adapter, creating it if necessary. */
  fun adapterExpression(): IrExpression {
    val adapterClass = generateAdapterIfAbsent()
    val irBlockBodyBuilder = irBlockBodyBuilder(pluginContext, scope, original)
    return irBlockBodyBuilder.irGetObject(adapterClass.symbol)
  }

  /** Creates the adapter if necessary. */
  fun generateAdapterIfAbsent(): IrClass {
    val companion = getOrCreateCompanion(original, pluginContext)
    return getOrCreateAdapterClass(companion)
  }

  private fun getOrCreateAdapterClass(
    companion: IrClass
  ): IrClass {
    // object Adapter : ZiplineServiceAdapter<SampleService>(), KSerializer<SampleService> {
    //   ...
    // }
    val existing = companion.declarations.firstOrNull {
      it is IrClass && it.name.identifier == "Adapter"
    }
    if (existing != null) return existing as IrClass

    val adapterClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("Adapter")
      kind = ClassKind.OBJECT
      visibility = DescriptorVisibilities.PUBLIC
    }.apply {
      parent = companion
      superTypes = listOf(
        ziplineApis.ziplineServiceAdapter.typeWith(original.defaultType),
        ziplineApis.kSerializer.typeWith(original.defaultType),
      )
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    adapterClass.addConstructor {
      initDefaults(adapterClass)
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.ziplineServiceAdapter.constructors.single(),
          typeArgumentsCount = 1
        ) {
          putTypeArgument(0, original.defaultType)
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = adapterClass.symbol,
        )
      }
    }

    val serialNameProperty = irSerialNameProperty(adapterClass)
    adapterClass.declarations += serialNameProperty

    val inboundBridgedInterface = BridgedInterface.create(
      pluginContext,
      messageCollector,
      ziplineApis,
      scope,
      original,
      "Zipline.take()",
      original.defaultType
    )
    val outboundBridgedInterface = BridgedInterface.create(
      pluginContext,
      messageCollector,
      ziplineApis,
      scope,
      original,
      "Zipline.take()",
      original.defaultType
    )

    var nextId = 0
    val ziplineFunctionClasses = inboundBridgedInterface.bridgedFunctions.associateWith {
      irZiplineFunctionClass(
        "ZiplineFunction${nextId++}",
        inboundBridgedInterface,
        adapterClass,
        it,
      )
    }

    adapterClass.declarations += ziplineFunctionClasses.values

    val ziplineFunctionsFunction = irZiplineFunctionsFunction(
      bridgedInterface = inboundBridgedInterface,
      adapterClass = adapterClass,
      ziplineFunctionClasses = ziplineFunctionClasses
    )

    val outboundServiceClass = irOutboundServiceClass(outboundBridgedInterface, adapterClass)
    val outboundServiceFunction = irOutboundServiceFunction(
      bridgedInterface = outboundBridgedInterface,
      adapterClass = adapterClass,
      outboundServiceClass = outboundServiceClass,
    )

    adapterClass.declarations += outboundServiceClass

    adapterClass.addFakeOverrides(
      irTypeSystemContext,
      listOf(
        serialNameProperty,
        ziplineFunctionsFunction,
        outboundServiceFunction
      )
    )

    companion.declarations += adapterClass
    companion.patchDeclarationParents(original)
    return adapterClass
  }

  /**
   * Override `ZiplineServiceAdapter.serialName`. The constant value is the service's simple name,
   * like "SampleService".
   */
  private fun irSerialNameProperty(adapterClass: IrClass): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = pluginContext.symbols.string.defaultType,
      declaringClass = adapterClass,
      propertyName = ziplineApis.ziplineServiceAdapterSerialName.owner.name,
      overriddenProperty = ziplineApis.ziplineServiceAdapterSerialName,
    ) {
      irExprBody(irString(original.name.identifier))
    }
  }

  /** Override `ZiplineServiceAdapter.ziplineFunctions()`. */
  private fun irZiplineFunctionsFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    ziplineFunctionClasses: Map<IrSimpleFunctionSymbol, IrClass>,
  ): IrSimpleFunction {
    // override fun ziplineFunctions(
    //   serializersModule: SerializersModule,
    // ): Map<String, ZiplineFunction<SampleService>> { ... }
    val ziplineFunctionT = ziplineApis.ziplineFunction.typeWith(bridgedInterface.type)
    val mapOfStringZiplineFunctionT = ziplineApis.map.typeWith(
      pluginContext.symbols.string.defaultType,
      ziplineFunctionT,
    )
    val mutableMapOfStringZiplineFunctionT = ziplineApis.mutableMap.typeWith(
      pluginContext.symbols.string.defaultType,
      ziplineFunctionT,
    )

    val ziplineFunctionsFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterZiplineFunctions.owner.name
      returnType = mapOfStringZiplineFunctionT
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("serializersModule")
        type = ziplineApis.serializersModule.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterZiplineFunctions)
    }

    ziplineFunctionsFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = ziplineFunctionsFunction.symbol,
    ) {
      val serializers = bridgedInterface.declareSerializerTemporaries(
        statementsBuilder = this@irFunctionBody,
        serializersModuleParameter = ziplineFunctionsFunction.valueParameters[0]
      )

      val result = irTemporary(
        value = irCall(ziplineApis.mutableMapOfFunction).apply {
          putTypeArgument(0, pluginContext.symbols.string.defaultType)
          putTypeArgument(1, ziplineFunctionT)
          type = mutableMapOfStringZiplineFunctionT
        },
        nameHint = "result",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      // Each bridged function gets its own set() on the map.
      for ((bridgedFunction, handlerClass) in ziplineFunctionClasses) {
        // ZiplineFunction0(
        //   listOf<KSerializer<*>>(
        //     serializer1,
        //     serializer2,
        //   ),
        //   sampleResponseSerializer,
        // )
        +irCall(ziplineApis.mutableMapPutFunction).apply {
          dispatchReceiver = irGet(result)
          putValueArgument(0, irString(bridgedFunction.owner.signature))
          putValueArgument(1, irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
            +irCall(
              callee = handlerClass.constructors.single().symbol,
              type = handlerClass.defaultType
            ).apply {
              putValueArgument(0, irCall(ziplineApis.listOfFunction).apply {
                putTypeArgument(0, ziplineApis.kSerializer.starProjectedType)
                putValueArgument(0,
                  irVararg(
                    ziplineApis.kSerializer.starProjectedType,
                    bridgedFunction.owner.valueParameters.map { irGet(serializers[it.type]!!) }
                  )
                )
              })
              putValueArgument(1, irGet(serializers[bridgedFunction.owner.returnType]!!))
            }
          })
        }
      }

      +irReturn(irGet(result))
    }

    return ziplineFunctionsFunction
  }

  // class ZiplineFunction0(
  //   argSerializers: List<KSerializer<out Any?>>,
  //   resultSerializer: KSerializer<out Any?>,
  // ) : ZiplineFunction<SampleService>(argSerializers, resultSerializer) {
  //   ...
  // }
  private fun irZiplineFunctionClass(
    className: String,
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrClass {
    val ziplineFunction = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier(className)
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      superTypes = listOf(ziplineApis.ziplineFunction.typeWith(bridgedInterface.type))
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    ziplineFunction.addConstructor {
      initDefaults(adapterClass)
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("argSerializers")
        type = ziplineApis.listOfKSerializerStar
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("resultSerializer")
        type = ziplineApis.kSerializer.starProjectedType
      }
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.ziplineFunction.constructors.single(),
          valueArgumentsCount = 2,
        ) {
          putValueArgument(0, irGet(valueParameters[0]))
          putValueArgument(1, irGet(valueParameters[1]))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = ziplineFunction.symbol,
        )
      }
    }

    val callFunction = irCallFunction(
      ziplineFunctionClass = ziplineFunction,
      callSuspending = bridgedFunction.isSuspend,
      bridgedInterface = bridgedInterface,
    )

    // We add overrides here so we can call them below.
    ziplineFunction.addFakeOverrides(
      irTypeSystemContext,
      listOf(callFunction)
    )

    callFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callFunction.symbol,
    ) {
      +irReturn(
        irCallServiceFunction(
          bridgedInterface = bridgedInterface,
          callFunction = callFunction,
          bridgedFunction = bridgedFunction,
        )
      )
    }

    return ziplineFunction
  }

  /** Override either `ZiplineFunction.call()` or `ZiplineFunction.callSuspending()`. */
  private fun irCallFunction(
    ziplineFunctionClass: IrClass,
    bridgedInterface: BridgedInterface,
    callSuspending: Boolean,
  ): IrSimpleFunction {
    // override fun call(service: SampleService, args: List<*>): Any? {
    // }
    val ziplineFunctionCall = when {
      callSuspending -> ziplineApis.ziplineFunctionCallSuspending
      else -> ziplineApis.ziplineFunctionCall
    }
    return ziplineFunctionClass.addFunction {
      initDefaults(original)
      name = ziplineFunctionCall.owner.name
      returnType = pluginContext.symbols.any.defaultType.makeNullable()
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = ziplineFunctionClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("service")
        type = bridgedInterface.type
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("args")
        type = ziplineApis.list.starProjectedType
      }
      overriddenSymbols = listOf(ziplineFunctionCall)
    }
  }

  /** service.function(args[0] as Arg1Type, args[1] as Arg2Type) */
  private fun IrBuilderWithScope.irCallServiceFunction(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    return irCall(
      type = bridgedInterface.resolveTypeParameters(bridgedFunction.owner.returnType),
      callee = bridgedFunction,
      valueArgumentsCount = bridgedFunction.owner.valueParameters.size,
    ).apply {
      dispatchReceiver = irGet(callFunction.valueParameters[0])

      for (p in bridgedFunction.owner.valueParameters.indices) {
        putValueArgument(
          p,
          irAs(
            irCall(ziplineApis.listGetFunction).apply {
              dispatchReceiver = irGet(callFunction.valueParameters[1])
              putValueArgument(0, irInt(p))
            },
            bridgedInterface.resolveTypeParameters(
              bridgedFunction.owner.valueParameters[p].type
            ),
          )
        )
      }
    }
  }

  /** Override `ZiplineServiceAdapter.outboundService()`. */
  private fun irOutboundServiceFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    outboundServiceClass: IrClass,
  ): IrSimpleFunction {
    // override fun outboundService(context: OutboundBridge.Context): SampleService
    val outboundServiceFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterOutboundService.owner.name
      returnType = bridgedInterface.type
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("context")
        type = ziplineApis.outboundBridgeContext.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterOutboundService)
    }
    outboundServiceFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = outboundServiceFunction.symbol,
    ) {
      +irReturn(
        irCall(
          callee = outboundServiceClass.constructors.single().symbol,
          type = outboundServiceClass.defaultType
        ).apply {
          putValueArgument(0, irGet(outboundServiceFunction.valueParameters[0]))
        }
      )
    }
    return outboundServiceFunction
  }

  // private class GeneratedOutboundService(
  //   private val context: OutboundBridge.Context
  // ) : SampleService {
  //   private val serializer_0 = context.serializersModule.serializer<SampleRequest>()
  //   private val serializer_1 = context.serializersModule.serializer<SampleResponse>()
  //
  //   override fun ping(request: SampleRequest): SampleResponse { ... }
  // }
  private fun irOutboundServiceClass(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
  ): IrClass {
    val outboundServiceClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("GeneratedOutboundService")
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      superTypes = listOf(bridgedInterface.type)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = outboundServiceClass.addConstructor {
      initDefaults(original)
    }.apply {
      addValueParameter {
        initDefaults(adapterClass)
        name = Name.identifier("context")
        type = ziplineApis.outboundBridgeContext.defaultType
      }
    }
    constructor.irConstructorBody(pluginContext) { statements ->
      statements += irDelegatingConstructorCall(
        context = pluginContext,
        symbol = ziplineApis.any.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = pluginContext,
        classSymbol = outboundServiceClass.symbol,
      )
    }

    val contextProperty = irOutboundContextProperty(
      outboundServiceClass,
      constructor.valueParameters[0]
    )
    outboundServiceClass.declarations += contextProperty

    bridgedInterface.declareSerializerProperties(
      outboundServiceClass,
      constructor.valueParameters[0]
    )

    for (overridesList in bridgedInterface.bridgedFunctionsWithOverrides.values) {
      outboundServiceClass.irBridgedFunction(
        bridgedInterface = bridgedInterface,
        outboundContextProperty = contextProperty,
        overridesList = overridesList,
      )
    }

    outboundServiceClass.addFakeOverrides(
      irTypeSystemContext,
      outboundServiceClass.functions.toList(),
    )

    return outboundServiceClass
  }

  // override val context: OutboundBridge.Context = context
  private fun irOutboundContextProperty(
    outboundCallHandlerClass: IrClass,
    irContextParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.outboundBridgeContext.defaultType,
      declaringClass = outboundCallHandlerClass,
      propertyName = Name.identifier("context")
    ) {
      irExprBody(irGet(irContextParameter))
    }
  }

  private fun IrClass.irBridgedFunction(
    bridgedInterface: BridgedInterface,
    outboundContextProperty: IrProperty,
    overridesList: List<IrSimpleFunctionSymbol>,
  ): IrSimpleFunction {
    val bridgedFunction = overridesList[0].owner
    val functionReturnType = bridgedInterface.resolveTypeParameters(bridgedFunction.returnType)
    val result = addFunction {
      initDefaults(original)
      name = bridgedFunction.name
      isSuspend = bridgedFunction.isSuspend
      returnType = functionReturnType
    }.apply {
      overriddenSymbols = overridesList
      addDispatchReceiver {
        initDefaults(original)
        type = defaultType
      }
    }

    for (valueParameter in bridgedFunction.valueParameters) {
      result.addValueParameter {
        initDefaults(original)
        name = valueParameter.name
        type = bridgedInterface.resolveTypeParameters(valueParameter.type)
      }
    }

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val contextLocal = irTemporary(
        value = irCall(
          callee = outboundContextProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(result.dispatchReceiverParameter!!)
        },
        nameHint = "context",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      val newCall = irCall(ziplineApis.outboundBridgeContextNewCall).apply {
        dispatchReceiver = irGet(contextLocal)
        putValueArgument(0, irString(bridgedFunction.signature))
        putValueArgument(1, irInt(bridgedFunction.valueParameters.size))
      }

      // If this is the close() function, tell the OutboundContext that this instance is closed.
      //   context.close = true
      if (bridgedFunction.isZiplineClose()) {
        +irCall(ziplineApis.outboundBridgeContextClosed.owner.setter!!).apply {
          dispatchReceiver = irGet(contextLocal)
          putValueArgument(0, irTrue())
        }
      }

      val outboundCallLocal = irTemporary(
        value = newCall,
        nameHint = "outboundCall",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      // outboundCall.parameter<SampleRequest>(serializer_0, request)
      for (valueParameter in result.valueParameters) {
        val parameterType = bridgedInterface.resolveTypeParameters(valueParameter.type)
        +irCall(callee = ziplineApis.outboundCallParameter).apply {
          dispatchReceiver = irGet(outboundCallLocal)
          putTypeArgument(0, parameterType)
          putValueArgument(
            0,
            bridgedInterface.serializerExpression(
              this@irFunctionBody,
              parameterType,
              result.dispatchReceiverParameter!!
            )
          )
          putValueArgument(
            1,
            irGet(
              type = valueParameter.type,
              variable = valueParameter.symbol,
            )
          )
        }
      }

      // One of:
      //   return outboundCall.<SampleResponse>invoke(serializer_2)
      //   return outboundCall.<SampleResponse>invokeSuspending(serializer_2)
      val invoke = when {
        bridgedFunction.isSuspend -> ziplineApis.outboundCallInvokeSuspending
        else -> ziplineApis.outboundCallInvoke
      }
      +irReturn(
        value = irCall(callee = invoke).apply {
          dispatchReceiver = irGet(outboundCallLocal)
          type = functionReturnType
          putTypeArgument(0, result.returnType)
          putValueArgument(
            0,
            irGet(result.dispatchReceiverParameter!!)
          )
          putValueArgument(
            1,
            bridgedInterface.serializerExpression(
              this@irFunctionBody,
              functionReturnType,
              result.dispatchReceiverParameter!!
            )
          )
        },
        returnTargetSymbol = result.symbol,
        type = pluginContext.irBuiltIns.nothingType,
      )
    }

    return result
  }

  private fun IrSimpleFunction.isZiplineClose() =
    name.asString() == "close" && valueParameters.isEmpty()
}

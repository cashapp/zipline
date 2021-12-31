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
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
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
    // object Adapter : ZiplineServiceAdapter<SampleService>() {
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
      superTypes = listOf(ziplineApis.ziplineServiceAdapter.typeWith(original.defaultType))
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
      "Zipline.get()",
      original.defaultType
    )
    val outboundBridgedInterface = BridgedInterface.create(
      pluginContext,
      messageCollector,
      ziplineApis,
      scope,
      original,
      "Zipline.get()",
      original.defaultType
    )

    val inboundCallHandlerClass = irInboundCallHandlerClass(inboundBridgedInterface, adapterClass)
    val inboundCallHandlerFunction = irInboundCallHandlerFunction(
      bridgedInterface = inboundBridgedInterface,
      adapterClass = adapterClass,
      inboundCallHandlerClass = inboundCallHandlerClass,
    )

    val outboundServiceClass = irOutboundServiceClass(outboundBridgedInterface, adapterClass)
    val outboundServiceFunction = irOutboundServiceFunction(
      bridgedInterface = outboundBridgedInterface,
      adapterClass = adapterClass,
      outboundServiceClass = outboundServiceClass,
    )

    adapterClass.declarations += inboundCallHandlerClass
    adapterClass.declarations += outboundServiceClass

    adapterClass.addFakeOverrides(
      irTypeSystemContext,
      listOf(
        serialNameProperty,
        inboundCallHandlerFunction,
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

  /** Override `ZiplineServiceAdapter.inboundCallHandler()`. */
  private fun irInboundCallHandlerFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    inboundCallHandlerClass: IrClass,
  ): IrSimpleFunction {
    // override fun inboundCallHandler(context: InboundBridge.Context): InboundCallHandler {
    // }
    val inboundCallHandlerFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterInboundCallHandler.owner.name
      returnType = ziplineApis.inboundCallHandler.defaultType
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("service")
        type = bridgedInterface.type
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("context")
        type = ziplineApis.inboundBridgeContext.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterInboundCallHandler)
    }
    inboundCallHandlerFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = inboundCallHandlerFunction.symbol,
    ) {
      +irReturn(
        irCall(
          callee = inboundCallHandlerClass.constructors.single().symbol,
          type = inboundCallHandlerClass.defaultType
        ).apply {
          putValueArgument(0, irGet(inboundCallHandlerFunction.valueParameters[0]))
          putValueArgument(1, irGet(inboundCallHandlerFunction.valueParameters[1]))
        }
      )
    }
    return inboundCallHandlerFunction
  }

  // private class GeneratedInboundCallHandler(
  //   private val service: SampleService,
  //   override val context: InboundBridge.Context,
  // ) : InboundCallHandler {
  //   private val serializer_0 = context.serializersModule.serializer<SampleRequest>()
  //   private val serializer_1 = context.serializersModule.serializer<SampleResponse>()
  //
  //   override fun call(inboundCall: InboundCall): Array<String> { ... }
  //   override suspend fun callSuspending(inboundCall: InboundCall): Array<String> { ... }
  // }
  private fun irInboundCallHandlerClass(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass
  ): IrClass {
    val inboundCallHandler = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("GeneratedInboundCallHandler")
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      superTypes = listOf(ziplineApis.inboundCallHandler.defaultType)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = inboundCallHandler.addConstructor {
      initDefaults(adapterClass)
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("service")
        type = bridgedInterface.type
      }
      addValueParameter {
        initDefaults(adapterClass)
        name = Name.identifier("context")
        type = ziplineApis.inboundBridgeContext.defaultType
      }
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.any.constructors.single(),
        )
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = inboundCallHandler.symbol,
        )
      }
    }

    val serviceProperty = irInboundServiceProperty(
      bridgedInterface,
      inboundCallHandler,
      constructor.valueParameters[0]
    )
    inboundCallHandler.declarations += serviceProperty

    val contextProperty = irInboundContextProperty(
      inboundCallHandler,
      constructor.valueParameters[1]
    )
    inboundCallHandler.declarations += contextProperty

    bridgedInterface.declareSerializerProperties(
      inboundCallHandler,
      constructor.valueParameters[1]
    )

    val callFunction = irCallFunction(
      inboundCallHandler = inboundCallHandler,
      callSuspending = false
    )
    val callSuspendingFunction = irCallFunction(
      inboundCallHandler = inboundCallHandler,
      callSuspending = true
    )

    // We add overrides here so we can call them below.
    inboundCallHandler.addFakeOverrides(
      irTypeSystemContext,
      listOf(contextProperty, callFunction, callSuspendingFunction)
    )

    callFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callFunction.symbol,
    ) {
      +irReturn(
        irCallFunctionBody(bridgedInterface, callFunction, serviceProperty)
      )
    }
    callSuspendingFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callSuspendingFunction.symbol,
    ) {
      +irReturn(
        irCallFunctionBody(bridgedInterface, callSuspendingFunction, serviceProperty)
      )
    }

    return inboundCallHandler
  }

  // private val service: SampleService = service
  private fun irInboundServiceProperty(
    bridgedInterface: BridgedInterface,
    inboundCallHandlerClass: IrClass,
    irServiceParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = bridgedInterface.type,
      declaringClass = inboundCallHandlerClass,
      propertyName = Name.identifier("service"),
    ) {
      irExprBody(irGet(irServiceParameter))
    }
  }

  // override val context: InboundBridge.Context = context
  private fun irInboundContextProperty(
    inboundCallHandlerClass: IrClass,
    irContextParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.inboundBridgeContext.defaultType,
      declaringClass = inboundCallHandlerClass,
      propertyName = Name.identifier("context"),
      overriddenProperty = ziplineApis.inboundCallHandlerContext,
    ) {
      irExprBody(irGet(irContextParameter))
    }
  }

  /** Override either `InboundCallHandler.call()` or `InboundCallHandler.callSuspending()`. */
  private fun irCallFunction(
    inboundCallHandler: IrClass,
    callSuspending: Boolean,
  ): IrSimpleFunction {
    // override fun call(inboundCall: InboundCall): Array<String> {
    // }
    val inboundBridgeCall = when {
      callSuspending -> ziplineApis.inboundCallHandlerCallSuspending
      else -> ziplineApis.inboundCallHandlerCall
    }
    return inboundCallHandler.addFunction {
      initDefaults(original)
      name = inboundBridgeCall.owner.name
      returnType = ziplineApis.stringArrayType
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = inboundCallHandler.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("inboundCall")
        type = ziplineApis.inboundCall.defaultType
      }
      overriddenSymbols = listOf(inboundBridgeCall)
    }
  }

  /**
   * The body of either `InboundCallHandler.call()` or `InboundCallHandler.callSuspending()`.
   *
   * ```
   * when {
   *   inboundCall.funName == "function1" -> ...
   *   inboundCall.funName == "function2" -> ...
   *   ...
   *   else -> ...
   * }
   * ```
   */
  private fun IrBlockBodyBuilder.irCallFunctionBody(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    serviceProperty: IrProperty,
  ): IrExpression {
    val result = mutableListOf<IrBranch>()
    val inboundBridgeThis = callFunction.dispatchReceiverParameter!!

    // Each bridged function gets its own branch in the when() expression.
    for (bridgedFunction in bridgedInterface.bridgedFunctions) {
      if (callFunction.isSuspend != bridgedFunction.isSuspend) continue

      result += irBranch(
        condition = irEquals(
          arg1 = irFunName(callFunction),
          arg2 = irString(bridgedFunction.owner.name.identifier)
        ),
        result = irBlock {
          +irCallEncodeResult(
            bridgedInterface = bridgedInterface,
            callFunction = callFunction,
            resultExpression = irCallServiceFunction(
              bridgedInterface = bridgedInterface,
              callFunction = callFunction,
              inboundCallHandlerThis = inboundBridgeThis,
              serviceProperty = serviceProperty,
              bridgedFunction = bridgedFunction
            )
          )
        }
      )
    }

    // Add an else clause that calls unexpectedFunction().
    result += irElseBranch(irCallUnexpectedFunction(callFunction))

    return irWhen(
      type = ziplineApis.stringArrayType,
      branches = result
    )
  }

  /** `inboundCall.funName` */
  private fun IrBuilderWithScope.irFunName(
    callFunction: IrSimpleFunction,
  ): IrExpression {
    return irCall(
      callee = ziplineApis.inboundCall.getPropertyGetter("funName")!!,
      type = pluginContext.symbols.string.defaultType,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(
        callFunction = callFunction,
      )
    }
  }

  /** `service.function1(...)` */
  private fun IrBuilderWithScope.irCallServiceFunction(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    inboundCallHandlerThis: IrValueParameter,
    serviceProperty: IrProperty,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    val getServiceCall = irService(inboundCallHandlerThis, serviceProperty)
    val returnType = bridgedFunction.owner.returnType

    return irCall(
      type = bridgedInterface.resolveTypeParameters(returnType),
      callee = bridgedFunction,
      valueArgumentsCount = bridgedFunction.owner.valueParameters.size,
    ).apply {
      dispatchReceiver = getServiceCall

      for (p in bridgedFunction.owner.valueParameters.indices) {
        putValueArgument(
          p,
          irCallDecodeParameter(
            bridgedInterface = bridgedInterface,
            callFunction = callFunction,
            valueParameter = bridgedFunction.owner.valueParameters[p]
          ),
        )
      }
    }
  }

  /** `inboundBridge.service` */
  private fun IrBuilderWithScope.irService(
    inboundCallHandlerThis: IrValueParameter,
    serviceProperty: IrProperty,
  ): IrExpression {
    return irCall(
      callee = serviceProperty.getter!!,
    ).apply {
      dispatchReceiver = irGet(inboundCallHandlerThis)
    }
  }

  /** `inboundCall.parameter(...)` */
  private fun IrBuilderWithScope.irCallDecodeParameter(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    valueParameter: IrValueParameter,
  ): IrExpression {
    val valueParameterType = bridgedInterface.resolveTypeParameters(valueParameter.type)
    return irCall(
      type = valueParameterType,
      callee = ziplineApis.inboundCallParameter,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
      putTypeArgument(0, valueParameterType)
      putValueArgument(
        0,
        bridgedInterface.serializerExpression(
          this@irCallDecodeParameter,
          valueParameterType,
          callFunction.dispatchReceiverParameter!!
        )
      )
    }
  }

  /** `inboundCall` */
  private fun IrBuilderWithScope.irGetInboundCallParameter(
    callFunction: IrSimpleFunction
  ): IrGetValue {
    return irGet(
      type = ziplineApis.inboundCall.defaultType,
      variable = callFunction.valueParameters[0].symbol,
    )
  }

  /** `inboundCall.result(...)` */
  private fun IrBuilderWithScope.irCallEncodeResult(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    resultExpression: IrExpression
  ): IrExpression {
    return irCall(
      type = ziplineApis.stringArrayType,
      callee = ziplineApis.inboundCallResult,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
      putTypeArgument(0, resultExpression.type)
      putValueArgument(
        0,
        bridgedInterface.serializerExpression(
          this@irCallEncodeResult,
          resultExpression.type,
          callFunction.dispatchReceiverParameter!!
        )
      )
      putValueArgument(1, resultExpression)
    }
  }

  /** `inboundCall.unexpectedFunction()` */
  private fun IrBuilderWithScope.irCallUnexpectedFunction(
    callFunction: IrSimpleFunction,
  ): IrExpression {
    return irCall(
      type = ziplineApis.stringArrayType,
      callee = ziplineApis.inboundCallUnexpectedFunction,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
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
    inboundCallHandlerClass: IrClass,
    irContextParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.outboundBridgeContext.defaultType,
      declaringClass = inboundCallHandlerClass,
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
      val newCall = irCall(ziplineApis.outboundBridgeContextNewCall).apply {
        dispatchReceiver = irCall(
          callee = outboundContextProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(result.dispatchReceiverParameter!!)
        }
        putValueArgument(0, irString(bridgedFunction.name.identifier))
        putValueArgument(1, irInt(bridgedFunction.valueParameters.size))
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
}

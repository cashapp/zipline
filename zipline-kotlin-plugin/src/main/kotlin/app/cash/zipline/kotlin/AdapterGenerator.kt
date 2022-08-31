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
import org.jetbrains.kotlin.backend.common.ir.remapTypeParameters
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
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
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val original: IrClass
) {
  private val irFactory = pluginContext.irFactory
  private val irTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    ziplineApis,
    scope,
    original,
    "Zipline.take()",
    original.defaultType
  )

  /** Returns an expression that references the adapter, generating it if necessary. */
  fun adapterExpression(type: IrSimpleType): IrExpression {
    val adapterClass = generateAdapterIfAbsent()
    val irBlockBodyBuilder = irBlockBodyBuilder(pluginContext, scope, original)
    return irBlockBodyBuilder.adapterExpression(adapterClass, type)
  }

  /**
   * Given a type like `GenericEchoService<String, Long>`, this generates a call to the constructor
   * of that type.
   */
  private fun IrBuilderWithScope.adapterExpression(
    adapterClass: IrClass,
    adapterType: IrSimpleType,
  ): IrExpression {
    // listOf(
    //   serializer<String>(),
    //   serializer<Long>(),
    // )
    val serializersExpressions = adapterType.arguments.map { argumentType ->
      irCall(
        callee = ziplineApis.serializerFunctionNoReceiver,
        type = ziplineApis.kSerializer.typeWith(argumentType as IrType),
      ).apply {
        putTypeArgument(0, argumentType as IrType)
      }
    }
    val serializersList = irCall(ziplineApis.listOfFunction).apply {
      type = ziplineApis.listOfKSerializerStar
      putTypeArgument(0, ziplineApis.kSerializer.starProjectedType)
      putValueArgument(
        0,
        irVararg(
          ziplineApis.kSerializer.starProjectedType,
          serializersExpressions,
        )
      )
    }

    // Adapter<String, Long>(...)
    return irCallConstructor(
      callee = adapterClass.constructors.single().symbol,
      typeArguments = adapterType.arguments.map { it as IrType },
    ).apply {
      putValueArgument(0, serializersList)
    }
  }

  /**
   * Given a type like `GenericEchoService` and a list of type parameters like
   * `listOf(String.serializer(), Long.serializer())`, this generates a call to the constructor
   * of that type.
   */
  fun adapterExpression(
    serializersListExpression: IrExpression
  ): IrFunctionAccessExpression {
    val adapterClass = generateAdapterIfAbsent()
    return with(irBlockBodyBuilder(pluginContext, scope, original)) {
      irCall(
        callee = adapterClass.constructors.single(),
      ).apply {
        putValueArgument(
          0,
          serializersListExpression
        )
      }
    }
  }

  /** Creates the adapter if necessary. */
  fun generateAdapterIfAbsent(): IrClass {
    val companion = getOrCreateCompanion(original, pluginContext)
    return getOrCreateAdapterClass(companion)
  }

  /**
   * Copy the type parameters on one type or function to another type or function. This adds a
   * suffix to the type parameter to make it easier to see what's happening in dumped code.
   */
  private fun IrTypeParametersContainer.copyTypeParametersFromOriginal(
    suffix: String,
    isReified: Boolean = false,
  ) {
    for (typeParameter in original.typeParameters) {
      addTypeParameter {
        this.name = Name.identifier("${typeParameter.name.identifier}$suffix")
        this.superTypes += typeParameter.superTypes
        this.variance = typeParameter.variance
        this.isReified = isReified
      }
    }
  }

  private fun getOrCreateAdapterClass(
    companion: IrClass
  ): IrClass {
    // class Adapter : ZiplineServiceAdapter<SampleService>(
    //   val serializers: List<KSerializer<*>>,
    // ), KSerializer<SampleService> {
    //   ...
    // }
    val existing = companion.declarations.firstOrNull {
      it is IrClass && it.name.identifier == "Adapter"
    }
    if (existing != null) return existing as IrClass

    val adapterClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("Adapter")
      visibility = DescriptorVisibilities.INTERNAL
    }.apply {
      copyTypeParametersFromOriginal("X")
      parent = companion
      val serviceT = original.defaultType.remapTypeParameters(original, this@apply)
      superTypes = listOf(
        ziplineApis.ziplineServiceAdapter.typeWith(serviceT),
        ziplineApis.kSerializer.typeWith(serviceT),
      )
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = adapterClass.addConstructor {
      initDefaults(original)
      visibility = DescriptorVisibilities.INTERNAL
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("serializers")
        type = ziplineApis.listOfKSerializerStar
      }
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.ziplineServiceAdapter.constructors.single(),
          typeArgumentsCount = 1
        ) {
          putTypeArgument(0, original.defaultType.remapTypeParameters(original, adapterClass))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = adapterClass.symbol,
        )
      }
    }

    val serialNameProperty = irSerialNameProperty(adapterClass)
    adapterClass.declarations += serialNameProperty

    val serializersProperty = irSerializersProperty(adapterClass, constructor)
    adapterClass.declarations += serializersProperty

    var nextId = 0
    val ziplineFunctionClasses = bridgedInterface.bridgedFunctions.associateWith {
      irZiplineFunctionClass(
        "ZiplineFunction${nextId++}",
        bridgedInterface,
        adapterClass,
        it,
      )
    }

    adapterClass.declarations += ziplineFunctionClasses.values

    val ziplineFunctionsFunction = irZiplineFunctionsFunction(
      bridgedInterface = bridgedInterface,
      adapterClass = adapterClass,
      ziplineFunctionClasses = ziplineFunctionClasses,
      serializersProperty = serializersProperty,
    )

    val outboundServiceClass = irOutboundServiceClass(bridgedInterface, adapterClass)
    val outboundServiceFunction = irOutboundServiceFunction(
      bridgedInterface = bridgedInterface,
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
    // override val serialName: String = "SampleService"
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

  private fun irSerializersProperty(adapterClass: IrClass, value: IrConstructor): IrProperty {
    // override val serializers: List<KSerializer<*>>
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.listOfKSerializerStar,
      declaringClass = adapterClass,
      propertyName = ziplineApis.ziplineServiceAdapterSerializers.owner.name,
      overriddenProperty = ziplineApis.ziplineServiceAdapterSerializers,
    ) {
      irExprBody(irGet(value.valueParameters[0]))
    }
  }

  /** Override `ZiplineServiceAdapter.ziplineFunctions()`. */
  private fun irZiplineFunctionsFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    ziplineFunctionClasses: Map<IrSimpleFunctionSymbol, IrClass>,
    serializersProperty: IrProperty,
  ): IrSimpleFunction {
    // override fun ziplineFunctions(
    //   serializersModule: SerializersModule,
    // ): List<ZiplineFunction<SampleService>> { ... }
    val bridgedInterfaceT = bridgedInterface.type
      .remapTypeParameters(original, adapterClass)
    val ziplineFunctionT = ziplineApis.ziplineFunction.typeWith(bridgedInterfaceT)
    val listOfZiplineFunctionT = ziplineApis.list.typeWith(ziplineFunctionT)

    val ziplineFunctionsFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterZiplineFunctions.owner.name
      returnType = listOfZiplineFunctionT
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.typeWith(adapterClass.typeParameters.map { it.defaultType })
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
      val serializersLocal = irTemporary(
        value = irCall(
          callee = serializersProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(ziplineFunctionsFunction.dispatchReceiverParameter!!)
        },
        nameHint = "serializers",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      val serializers = bridgedInterface.declareSerializerTemporaries(
        statementsBuilder = this@irFunctionBody,
        serializersModuleParameter = ziplineFunctionsFunction.valueParameters[0],
        serializersExpression = serializersLocal,
      )

      // Call each ZiplineFunction constructor.
      val expressions = mutableListOf<IrExpression>()
      for ((bridgedFunction, handlerClass) in ziplineFunctionClasses) {
        // ZiplineFunction0(
        //   listOf<KSerializer<*>>(
        //     serializer1,
        //     serializer2,
        //   ),
        //   sampleResponseSerializer,
        // )
        expressions += irCallConstructor(
          callee = handlerClass.constructors.single().symbol,
          typeArguments = adapterClass.typeParameters.map { it.defaultType },
        ).apply {
          type = ziplineFunctionT
          putValueArgument(0, irCall(ziplineApis.listOfFunction).apply {
            putTypeArgument(0, ziplineApis.kSerializer.starProjectedType)
            putValueArgument(0,
              irVararg(
                ziplineApis.kSerializer.starProjectedType,
                bridgedFunction.owner.valueParameters.map { irGet(serializers[it.type]!!) }
              )
            )
          })
          val returnType = bridgedFunction.owner.returnType
          val resultSerializerType = when {
            bridgedFunction.isSuspend -> ziplineApis.suspendCallback.typeWith(returnType)
            else -> returnType
          }
          putValueArgument(1, irGet(serializers[resultSerializerType]!!))
        }
      }

      // return listOf<ZiplineFunction<SampleService>>(
      //   ZiplineFunction0(...),
      //   ZiplineFunction1(...),
      // )
      +irReturn(irCall(ziplineApis.listOfFunction).apply {
        putTypeArgument(0, ziplineFunctionT)
        putValueArgument(0,
          irVararg(
            ziplineFunctionT,
            expressions,
          )
        )
      })
    }

    return ziplineFunctionsFunction
  }

  // class ZiplineFunction0(
  //   argSerializers: List<KSerializer<out Any?>>,
  //   resultSerializer: KSerializer<out Any?>,
  // ) : ReturningZiplineFunction<SampleService>(
  //   "fun ping(app.cash.zipline.SampleRequest): app.cash.zipline.SampleResponse",
  //   argSerializers,
  //   resultSerializer,
  // ) {
  //   ...
  // }
  private fun irZiplineFunctionClass(
    className: String,
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrClass {
    val functionClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier(className)
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      createImplicitParameterDeclarationWithWrappedDescriptor()
      copyTypeParametersFromOriginal("F")
      thisReceiver?.type = defaultDispatchReceiver
    }

    val supertype = when {
      bridgedFunction.isSuspend -> ziplineApis.suspendingZiplineFunction
      else -> ziplineApis.returningZiplineFunction
    }

    val bridgedInterfaceT = bridgedInterface.type
      .remapTypeParameters(original, functionClass)
    functionClass.superTypes = listOf(supertype.typeWith(bridgedInterfaceT))

    functionClass.addConstructor {
      initDefaults(original)
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
          symbol = supertype.constructors.single(),
          valueArgumentsCount = 3,
          typeArgumentsCount = 1,
        ) {
          putTypeArgument(0, bridgedInterfaceT)
          putValueArgument(0, irString(bridgedFunction.owner.signature))
          putValueArgument(1, irGet(valueParameters[0]))
          putValueArgument(2, irGet(valueParameters[1]))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = functionClass.symbol,
        )
      }
    }

    val callFunction = irCallFunction(
      ziplineFunctionClass = functionClass,
      callSuspending = bridgedFunction.isSuspend,
      bridgedInterfaceT = bridgedInterfaceT,
    )

    // We add overrides here so we can call them below.
    functionClass.addFakeOverrides(
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
          ziplineFunctionClass = functionClass,
          callFunction = callFunction,
          bridgedFunction = bridgedFunction,
        )
      )
    }

    return functionClass
  }

  /**
   * Override either `ReturningZiplineFunction.call()` or
   * `SuspendingZiplineFunction.callSuspending()`.
   */
  private fun irCallFunction(
    ziplineFunctionClass: IrClass,
    callSuspending: Boolean,
    bridgedInterfaceT: IrType,
  ): IrSimpleFunction {
    // override fun call(service: SampleService, args: List<*>): Any? {
    // }
    val ziplineFunctionCall = when {
      callSuspending -> ziplineApis.suspendingZiplineFunctionCallSuspending
      else -> ziplineApis.returningZiplineFunctionCall
    }
    return ziplineFunctionClass.addFunction {
      initDefaults(original)
      name = ziplineFunctionCall.owner.name
      returnType = pluginContext.symbols.any.defaultType.makeNullable()
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = ziplineFunctionClass.defaultDispatchReceiver
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("service")
        type = bridgedInterfaceT
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
    ziplineFunctionClass: IrClass,
    callFunction: IrSimpleFunction,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    return irCall(
      type = bridgedInterface.resolveTypeParameters(bridgedFunction.owner.returnType)
        .remapTypeParameters(original, ziplineFunctionClass),
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
            bridgedInterface.resolveTypeParameters(bridgedFunction.owner.valueParameters[p].type)
              .remapTypeParameters(original, ziplineFunctionClass),
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
    val bridgedInterfaceT = bridgedInterface.type
      .remapTypeParameters(original, adapterClass)
    // override fun outboundService(callHandler: OutboundCallHandler): SampleService
    val outboundServiceFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterOutboundService.owner.name
      returnType = bridgedInterfaceT
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultDispatchReceiver
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("callHandler")
        type = ziplineApis.outboundCallHandler.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterOutboundService)
    }
    outboundServiceFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = outboundServiceFunction.symbol,
    ) {
      +irReturn(
        irCallConstructor(
          callee = outboundServiceClass.constructors.single().symbol,
          typeArguments = adapterClass.typeParameters.map { it.defaultType },
        ).apply {
          putValueArgument(0, irGet(outboundServiceFunction.valueParameters[0]))
          type = bridgedInterfaceT
        }
      )
    }
    return outboundServiceFunction
  }

  // private class GeneratedOutboundService(
  //   private val callHandler: OutboundCallHandler
  // ) : SampleService {
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
      createImplicitParameterDeclarationWithWrappedDescriptor()
      copyTypeParametersFromOriginal("S")
      thisReceiver?.type = defaultDispatchReceiver
    }
    val bridgedInterfaceT = bridgedInterface.type
      .remapTypeParameters(original, outboundServiceClass)
    outboundServiceClass.superTypes = listOf(bridgedInterfaceT)

    val constructor = outboundServiceClass.addConstructor {
      initDefaults(original)
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("callHandler")
        type = ziplineApis.outboundCallHandler.defaultType
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

    // override val callHandler: OutboundCallHandler = callHandler
    val callHandlerProperty = outboundServiceClass.addPropertyFromConstructorParameter(
      "callHandler",
      constructor.valueParameters[0],
    )

    for ((i, overridesList) in bridgedInterface.bridgedFunctionsWithOverrides.values.withIndex()) {
      outboundServiceClass.irBridgedFunction(
        functionIndex = i,
        bridgedInterface = bridgedInterface,
        callHandlerProperty = callHandlerProperty,
        overridesList = overridesList,
      )
    }

    outboundServiceClass.addFakeOverrides(
      irTypeSystemContext,
      outboundServiceClass.functions.toList(),
    )

    return outboundServiceClass
  }

  private fun IrClass.addPropertyFromConstructorParameter(
    name: String,
    constructorParameter: IrValueParameter
  ): IrProperty {
    val result = irVal(
      pluginContext = pluginContext,
      propertyType = constructorParameter.type,
      declaringClass = this,
      propertyName = Name.identifier(name)
    ) {
      irExprBody(irGet(constructorParameter))
    }
    declarations += result
    return result
  }

  private fun IrClass.irBridgedFunction(
    functionIndex: Int,
    bridgedInterface: BridgedInterface,
    callHandlerProperty: IrProperty,
    overridesList: List<IrSimpleFunctionSymbol>,
  ): IrSimpleFunction {
    // override fun ping(request: SampleRequest): SampleResponse {
    //   return callHandler.call(this, 0, request) as SampleResponse
    // }
    val bridgedFunction = overridesList[0].owner
    val functionReturnType = bridgedInterface.resolveTypeParameters(bridgedFunction.returnType)
      .remapTypeParameters(original, this@irBridgedFunction)
    val result = addFunction {
      initDefaults(original)
      name = bridgedFunction.name
      isSuspend = bridgedFunction.isSuspend
      returnType = functionReturnType
    }.apply {
      overriddenSymbols = overridesList
      addDispatchReceiver {
        initDefaults(original)
        type = defaultDispatchReceiver
      }
    }

    for (valueParameter in bridgedFunction.valueParameters) {
      result.addValueParameter {
        initDefaults(original)
        name = valueParameter.name
        type = bridgedInterface.resolveTypeParameters(valueParameter.type)
          .remapTypeParameters(original, this@irBridgedFunction)
      }
    }

    // return callHandler.call(this, 0, request) as SampleResponse
    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val callHandlerLocal = irTemporary(
        value = irCall(
          callee = callHandlerProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(result.dispatchReceiverParameter!!)
        },
        nameHint = "callHandler",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      // If this is the close() function, tell the OutboundCallHandler that this instance is closed.
      //   callHandler.close = true
      if (bridgedFunction.isZiplineClose()) {
        +irCall(ziplineApis.outboundCallHandlerClosed.owner.setter!!).apply {
          dispatchReceiver = irGet(callHandlerLocal)
          putValueArgument(0, irTrue())
        }
      }

      // One of:
      //   return callHandler.call(service, index, arg0, arg1, arg2)
      //   return callHandler.callSuspending(service, index, arg0, arg1, arg2)
      val callFunction = when {
        bridgedFunction.isSuspend -> ziplineApis.outboundCallHandlerCallSuspending
        else -> ziplineApis.outboundCallHandlerCall
      }
      val call = irCall(callFunction).apply {
        dispatchReceiver = irGet(callHandlerLocal)
        putValueArgument(
          0,
          irGet(result.dispatchReceiverParameter!!),
        )
        putValueArgument(
          1,
          irInt(functionIndex),
        )
        putValueArgument(
          2,
          irVararg(
            elementType = pluginContext.symbols.any.defaultType.makeNullable(),
            values = result.valueParameters.map {
              irGet(
                type = it.type,
                variable = it.symbol,
              )
            }
          )
        )
      }

      +irReturn(
        value = irAs(
          argument = call,
          type = functionReturnType
        ),
        returnTargetSymbol = result.symbol,
        type = pluginContext.irBuiltIns.nothingType,
      )
    }

    return result
  }

  private val IrClass.defaultDispatchReceiver
    get() = typeWith(typeParameters.map { it.defaultType })

  private fun IrSimpleFunction.isZiplineClose() =
    name.asString() == "close" && valueParameters.isEmpty()
}

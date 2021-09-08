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

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
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
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `KtBridge.set()` that takes a name, a `SerializersModule`, and a service:
 *
 * ```
 * ktBridge.set("helloService", EchoSerializersModule, TestingEchoService("hello"))
 * ```
 *
 * to the overload that takes a name and an `InboundService`:
 *
 * ```
 * ktBridge.set(
 *   "helloService",
 *   object : InboundService<EchoService>(EchoSerializersModule) {
 *     val service: EchoService = TestingEchoService("hello")
 *     override fun call(inboundCall: InboundCall): ByteArray {
 *       return when {
 *         inboundCall.funName == "echo" -> {
 *           inboundCall.result(
 *             service.echo(
 *               inboundCall.parameter()
 *             )
 *           )
 *         }
 *         else -> inboundCall.unexpectedFunction()
 *       }
 *     }
 *   }
 * )
 * ```
 *
 * For suspending functions, everything is the same except overridden method is `callSuspending()`.
 * Both methods area always overridden, but may contain only the `unexpectedFunction()` case.
 */
internal class KtBridgeSetRewriter(
  private val pluginContext: IrPluginContext,
  private val ktBridgeApis: KtBridgeApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
  private val rewrittenSetFunction: IrSimpleFunctionSymbol,
) {
  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    original,
    "KtBridge.set()",
    original.getTypeArgument(0)!!
  )

  private val irFactory: IrFactory
    get() = pluginContext.irFactory

  fun rewrite(): IrCall {
    return IrCallImpl(
      startOffset = original.startOffset,
      endOffset = original.endOffset,
      type = original.type,
      symbol = rewrittenSetFunction,
      typeArgumentsCount = 0,
      valueArgumentsCount = 2,
      origin = original.origin,
      superQualifierSymbol = original.superQualifierSymbol
    ).apply {
      dispatchReceiver = original.dispatchReceiver
      putValueArgument(0, original.getValueArgument(0))
      putValueArgument(1, irNewInboundService())
    }
  }

  private fun irNewInboundService(): IrContainerExpression {
    val inboundServiceOfT = ktBridgeApis.inboundService.typeWith(bridgedInterface.type)
    val inboundServiceSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = declarationParent
      superTypes = listOf(inboundServiceOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    // InboundService<EchoService>(EchoSerializersModule)
    val superConstructor = ktBridgeApis.inboundService.constructors.single()
    val constructor = inboundServiceSubclass.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor,
          typeArgumentsCount = 1,
          valueArgumentsCount = 1
        ) {
          putTypeArgument(0, bridgedInterface.type)
          putValueArgument(0, original.getValueArgument(1))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = inboundServiceSubclass.symbol,
        )
      }
    }

    val serviceProperty = irServiceProperty(inboundServiceSubclass)

    inboundServiceSubclass.declarations += serviceProperty

    val callFunction = irCallFunction(
      inboundServiceSubclass = inboundServiceSubclass,
      serviceProperty = serviceProperty,
      callSuspending = false
    )
    val callSuspendingFunction = irCallFunction(
      inboundServiceSubclass = inboundServiceSubclass,
      serviceProperty = serviceProperty,
      callSuspending = true
    )

    inboundServiceSubclass.addFakeOverrides(
      pluginContext.irBuiltIns,
      listOf(callFunction, callSuspendingFunction)
    )

    return IrBlockBodyBuilder(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      context = pluginContext,
      scope = scope.scope,
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = inboundServiceSubclass.defaultType
      +inboundServiceSubclass
      +irCall(constructor.symbol, type = inboundServiceSubclass.defaultType)
    }
  }

  /** Override either `InboundService.call()` or `InboundService.callSuspending()`. */
  private fun irCallFunction(
    inboundServiceSubclass: IrClass,
    serviceProperty: IrProperty,
    callSuspending: Boolean,
  ): IrSimpleFunction {
    // override fun call(inboundCall: InboundCall): ByteArray {
    // }
    val inboundServiceCall = when {
      callSuspending -> ktBridgeApis.inboundServiceCallSuspending
      else -> ktBridgeApis.inboundServiceCall
    }
    val result = inboundServiceSubclass.addFunction {
      name = inboundServiceCall.owner.name
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = pluginContext.symbols.byteArrayType
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        type = inboundServiceSubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("inboundCall")
        type = ktBridgeApis.inboundCall.defaultType
      }
      overriddenSymbols += inboundServiceCall
    }

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol,
    ) {
      +irReturn(
        irCallFunctionBody(result, serviceProperty)
      )
    }

    return result
  }

  // val service: EchoService = TestingEchoService("hello")
  private fun irServiceProperty(inboundServiceSubclass: IrClass): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = bridgedInterface.type,
      declaringClass = inboundServiceSubclass,
      initializer = irFactory.createExpressionBody(original.getValueArgument(2)!!),
      propertyName = Name.identifier("service")
    )
  }

  /**
   * The body of either `InboundService.call()` or `InboundService.callSuspending()`.
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
    callFunction: IrSimpleFunction,
    serviceProperty: IrProperty,
  ): IrExpression {
    val result = mutableListOf<IrBranch>()

    // Each bridged function gets its own branch in the when() expression.
    for (bridgedFunction in bridgedInterface.classSymbol.functions) {
      // TODO(jwilson): find a better way to skip equals()/hashCode()/toString()
      if (bridgedFunction.owner.isFakeOverride) continue
      if (callFunction.isSuspend != bridgedFunction.isSuspend) continue

      result += irBranch(
        condition = irEquals(
          arg1 = irFunName(callFunction),
          arg2 = irString(bridgedFunction.owner.name.identifier)
        ),
        result = irBlock {
          +irCallEncodeResult(
            callFunction = callFunction,
            resultExpression = irCallServiceFunction(
              callFunction = callFunction,
              serviceProperty = serviceProperty,
              bridgedFunction = bridgedFunction
            )
          )
        }
      )
    }

    // Add an else clause that calls unexpectedFunction().
    result += irBranch(
      condition = irTrue(),
      result = irCallUnexpectedFunction(
        callFunction = callFunction,
      )
    )

    return irWhen(
      type = pluginContext.symbols.byteArrayType,
      branches = result
    )
  }

  /** `inboundCall.funName` */
  private fun IrBuilderWithScope.irFunName(
    callFunction: IrSimpleFunction,
  ): IrExpression {
    return irCall(
      callee = ktBridgeApis.inboundCall.getPropertyGetter("funName")!!,
      type = pluginContext.symbols.string.defaultType,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(
        callFunction = callFunction,
      )
    }
  }

  /** `inboundCall.service.function1(...)` */
  private fun IrBuilderWithScope.irCallServiceFunction(
    callFunction: IrSimpleFunction,
    serviceProperty: IrProperty,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    val getServiceCall = irService(callFunction, serviceProperty)
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
            callFunction = callFunction,
            valueParameter = bridgedFunction.owner.valueParameters[p]
          ),
        )
      }
    }
  }

  /** `inboundCall.service` */
  private fun IrBuilderWithScope.irService(
    callFunction: IrSimpleFunction,
    serviceProperty: IrProperty,
  ): IrExpression {
    return irCall(
      callee = serviceProperty.getter!!,
    ).apply {
      dispatchReceiver = irGet(callFunction.dispatchReceiverParameter!!)
    }
  }

  /** `inboundCall.parameter(...)` */
  private fun IrBuilderWithScope.irCallDecodeParameter(
    callFunction: IrSimpleFunction,
    valueParameter: IrValueParameter,
  ): IrExpression {
    val valueParameterType = bridgedInterface.resolveTypeParameters(valueParameter.type)
    return irCall(
      type = valueParameterType,
      callee = ktBridgeApis.inboundCallParameter,
      typeArgumentsCount = 1,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
      putTypeArgument(0, valueParameterType)
    }
  }

  /** `inboundCall` */
  private fun IrBuilderWithScope.irGetInboundCallParameter(
    callFunction: IrSimpleFunction
  ): IrGetValue {
    return irGet(
      type = ktBridgeApis.inboundCall.defaultType,
      variable = callFunction.valueParameters[0].symbol,
    )
  }

  /** `inboundCall.result(...)` */
  private fun IrBuilderWithScope.irCallEncodeResult(
    callFunction: IrSimpleFunction,
    resultExpression: IrExpression
  ): IrExpression {
    return irCall(
      type = pluginContext.symbols.byteArrayType,
      callee = ktBridgeApis.inboundCallResult,
      typeArgumentsCount = 1,
      valueArgumentsCount = 1,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
      putTypeArgument(0, resultExpression.type)
      putValueArgument(0, resultExpression)
    }
  }

  /** `inboundCall.unexpectedFunction()` */
  private fun IrBuilderWithScope.irCallUnexpectedFunction(
    callFunction: IrSimpleFunction,
  ): IrExpression {
    return irCall(
      type = pluginContext.symbols.byteArrayType,
      callee = ktBridgeApis.inboundCallUnexpectedFunction,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
    }
  }
}

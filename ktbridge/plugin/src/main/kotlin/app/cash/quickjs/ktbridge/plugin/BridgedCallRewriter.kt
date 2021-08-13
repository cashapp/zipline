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
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `createBridgeToJs` with two arguments:
 *
 * ```
 * val helloService = createBridgeToJs(JsEchoService("hello"), EchoJsAdapter)
 * ```
 *
 * to the overload that takes three arguments:
 *
 * ```
 * val helloService = createBridgeToJs(
 *   service = JsEchoService("hello"),
 *   jsAdapter = EchoJsAdapter,
 *   block = fun (bridgedCall: BridgedCall<JsEchoService>): ByteArray {
 *     return when {
 *       bridgedCall.funName == "echo" -> {
 *         bridgedCall.result(
 *           bridgedCall.service.echo(
 *             bridgedCall.parameter()
 *           )
 *         )
 *       }
 *       else -> bridgedCall.unexpectedFunction()
 *     }
 *   }
 * )
 * ```
 *
 * This synthesizes a function that routes calls by their name, decodes parameters, and encodes the
 * result.
 */
@ObsoleteDescriptorBasedAPI // TODO(jwilson): is there an alternative?
class BridgedCallRewriter(
  private val pluginContext: IrPluginContext,
  private val backingField: IrField,
  private val initializer: IrExpressionBody,
  private val initializerCall: IrCall,
) {
  private val irFactory: IrFactory
    get() = pluginContext.irFactory

  private val createBridgeToJsFunction3Arg = pluginContext.referenceFunctions(CREATE_BRIDGE_TO_JS)
    .single { it.descriptor.valueParameters.size == 3 }
  private val classBridgedCall = pluginContext.referenceClass(BRIDGED_CALL) ?: error("TODO")
  private val serviceInterface = initializerCall.getTypeArgument(0) ?: error("TODO")
  private val bridgedCallOfTType = classBridgedCall.typeWith(serviceInterface)

  private val function: IrSimpleFunction = irFactory.buildFun {
    name = Name.special("<no name provided>")
    returnType = pluginContext.symbols.byteArrayType
  }.apply {
    parent = backingField
    addValueParameter(
      name = "bridgedCall",
      type = bridgedCallOfTType,
      origin = IrDeclarationOrigin.DEFINED
    )
  }

  fun rewrite() {
    val irBuilder = DeclarationIrBuilder(
      generatorContext = pluginContext,
      symbol = IrSimpleFunctionSymbolImpl(),
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET
    )

    val iBridgedFunctionsWhen = irBuilder.irBridgedFunctionsWhen()

    function.body = IrBlockBodyImpl(
      startOffset = irBuilder.startOffset,
      endOffset = irBuilder.endOffset,
    ).apply {
      statements += irBuilder.irReturn(iBridgedFunctionsWhen)
    }

    initializer.expression = irCall(initializerCall, createBridgeToJsFunction3Arg).apply {
      putValueArgument(2, irBuilder.irFunction1Expression())
    }
  }

  /**
   * ```
   * when {
   *   bridgedCall.funName == "function1" -> ...
   *   bridgedCall.funName == "function2" -> ...
   *   ...
   *   else -> ...
   * }
   * ```
   */
  private fun IrBuilderWithScope.irBridgedFunctionsWhen(): IrExpression {
    val result = mutableListOf<IrBranch>()

    val serviceInterface = pluginContext.referenceClass(serviceInterface.classFqName!!)
      ?: error("TODO")

    // Each bridged function gets its own branch in the when() expression.
    for (bridgedFunction in serviceInterface.functions) {
      // TODO(jwilson): find a better way to skip equals()/hashCode()/toString()
      if (bridgedFunction.owner.isFakeOverride) continue

      result += irBranch(
        condition = irEquals(
          arg1 = irFunName(),
          arg2 = irString(bridgedFunction.descriptor.name.identifier)
        ),
        result = irCallEncodeResult(
          resultExpression = irCallServiceFunction(bridgedFunction)
        )
      )
    }

    // Add an else clause that calls unexpectedFunction().
    result += irBranch(
      condition = irTrue(),
      result = irCallUnexpectedFunction()
    )

    return irWhen(
      type = pluginContext.symbols.byteArrayType,
      branches = result
    )
  }

  /** `bridgedCall.funName` */
  private fun IrBuilderWithScope.irFunName(): IrExpression {
    return irCall(
      callee = classBridgedCall.getPropertyGetter("funName")!!,
      type = pluginContext.symbols.string.defaultType,
    ).apply {
      dispatchReceiver = irGetBridgedCallParameter()
    }
  }

  /** `bridgedCall.service.function1(...)` */
  private fun IrBuilderWithScope.irCallServiceFunction(
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    val getServiceCall = irService()
    val returnType = pluginContext.typeTranslator.translateType(
      bridgedFunction.descriptor.returnType ?: error("TODO")
    )

    return irCall(
      type = returnType,
      callee = bridgedFunction,
      valueArgumentsCount = bridgedFunction.descriptor.valueParameters.size,
    ).apply {
      dispatchReceiver = getServiceCall

      for (p in bridgedFunction.descriptor.valueParameters.indices) {
        putValueArgument(
          p,
          irCallDecodeParameter(
            valueParameter = bridgedFunction.descriptor.valueParameters[p]
          ),
        )
      }
    }
  }

  /** `bridgedCall.service` */
  private fun IrBuilderWithScope.irService(): IrExpression {
    return irCall(
      type = serviceInterface,
      callee = classBridgedCall.getPropertyGetter("service")!!,
    ).apply {
      dispatchReceiver = irGetBridgedCallParameter()
    }
  }

  /** `bridgedCall.decode(...)` */
  private fun IrBuilderWithScope.irCallDecodeParameter(
    valueParameter: ValueParameterDescriptor
  ): IrExpression {
    val parameterFunction = pluginContext.referenceFunctions(
      BRIDGED_CALL.child(
        Name.identifier("parameter")
      )
    ).single { it.descriptor.isInline }

    val parameterType = pluginContext.typeTranslator.translateType(valueParameter.type)
    return irCall(
      type = parameterType,
      callee = parameterFunction,
      typeArgumentsCount = 1,
    ).apply {
      dispatchReceiver = irGetBridgedCallParameter()
      putTypeArgument(0, parameterType)
    }
  }

  /** `bridgedCall` */
  private fun IrBuilderWithScope.irGetBridgedCallParameter(): IrGetValue {
    return irGet(
      type = bridgedCallOfTType,
      variable = function.valueParameters[0].symbol,
    )
  }

  /** `bridgedCall.result(...)` */
  private fun IrBuilderWithScope.irCallEncodeResult(
    resultExpression: IrExpression
  ): IrExpression {
    val resultFunction = pluginContext.referenceFunctions(
      BRIDGED_CALL.child(Name.identifier("result"))
    ).single { it.descriptor.isInline }

    return irCall(
      type = pluginContext.symbols.byteArrayType,
      callee = resultFunction,
      typeArgumentsCount = 1,
      valueArgumentsCount = 1,
    ).apply {
      dispatchReceiver = irGetBridgedCallParameter()
      putTypeArgument(0, resultExpression.type)
      putValueArgument(0, resultExpression)
    }
  }

  /** `bridgedCall.unexpectedFunction()` */
  private fun IrBuilderWithScope.irCallUnexpectedFunction(): IrExpression {
    val unexpectedCallFunction = pluginContext.referenceFunctions(
      BRIDGED_CALL.child(Name.identifier("unexpectedFunction"))
    ).single()

    return irCall(
      type = pluginContext.symbols.byteArrayType,
      callee = unexpectedCallFunction,
    ).apply {
      dispatchReceiver = irGetBridgedCallParameter()
    }
  }

  /** `return ...` */
  private fun IrBuilderWithScope.irReturn(value: IrExpression): IrReturn {
    return IrReturnImpl(
      startOffset = startOffset,
      endOffset = endOffset,
      type = value.type,
      returnTargetSymbol = function.symbol,
      value = value,
    )
  }

  /** Express [function] as a `Function1<BridgedCall<JsEchoService>, ByteArray>`. */
  private fun IrBuilderWithScope.irFunction1Expression(): IrFunctionExpression {
    val function1 = pluginContext.referenceClass(FqName("kotlin.Function1"))!!
    val function1WithTypeParameters = function1.typeWith(
      bridgedCallOfTType,
      pluginContext.symbols.byteArrayType,
    )

    return IrFunctionExpressionImpl(
      startOffset = startOffset,
      endOffset = endOffset,
      type = function1WithTypeParameters,
      function = function,
      origin = IrStatementOrigin.ANONYMOUS_FUNCTION,
    )
  }

  companion object {
    val CREATE_BRIDGE_TO_JS = FqName("app.cash.quickjs.ktbridge.createBridgeToJs")
    val BRIDGE_TO_JS = FqName("app.cash.quickjs.ktbridge.BridgeToJs")
    val BRIDGED_CALL = FqName("app.cash.quickjs.ktbridge.BridgedCall")
  }
}

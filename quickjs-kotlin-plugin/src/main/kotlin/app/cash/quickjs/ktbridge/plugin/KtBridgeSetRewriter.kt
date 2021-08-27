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

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.createDispatchReceiverParameter
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
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
import org.jetbrains.kotlin.ir.builders.irGetField
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
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrPropertySymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `QuickJs.set()` that takes a name, a `JsAdapter`, and a service:
 *
 * ```
 * quickJs.set("helloService", EchoJsAdapter, TestingEchoService("hello"))
 * ```
 *
 * to the overload that takes a name and an `InboundService`:
 *
 * ```
 * quickJs.set(
 *   "helloService",
 *   object : InboundService<EchoService>(EchoJsAdapter) {
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
 */
internal class KtBridgeSetRewriter(
  private val pluginContext: IrPluginContext,
  private val ktBridgeApis: KtBridgeApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall
) {
  private val serviceInterface = original.getTypeArgument(0)!!
  private val serviceInterfaceType = serviceInterfaceType()

  private val irFactory: IrFactory
    get() = pluginContext.irFactory

  fun rewrite(): IrCall {
    return IrCallImpl(
      startOffset = original.startOffset,
      endOffset = original.endOffset,
      type = original.type,
      symbol = ktBridgeApis.rewrittenSetFunction,
      typeArgumentsCount = 0,
      valueArgumentsCount = 2,
      origin = original.origin,
      superQualifierSymbol = original.superQualifierSymbol
    ).apply {
      dispatchReceiver = original.dispatchReceiver
      // putTypeArgument(0, original.getTypeArgument(0))
      putValueArgument(0, original.getValueArgument(0))
      putValueArgument(1, irNewInboundService())
    }
  }

  /** Returns the interface of the type argument for the original call. */
  private fun serviceInterfaceType(): IrClassSymbol {
    val result = pluginContext.referenceClass(serviceInterface.classFqName ?: FqName.ROOT)
    if (result == null || !result.owner.isInterface) {
      throw KtBridgeCompilationException(
        element = original,
        message = "The type argument to QuickJs.set() must be an interface type",
      )
    }
    return result
  }

  private fun irNewInboundService(): IrContainerExpression {
    val inboundServiceOfT = ktBridgeApis.inboundService.typeWith(serviceInterface)
    val inboundServiceSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = declarationParent
      superTypes = listOf(inboundServiceOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    // InboundService<EchoService>(EchoJsAdapter)
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
          putTypeArgument(0, serviceInterface)
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

    // override fun call(inboundCall: InboundCall): ByteArray {
    // }
    val callFunction = inboundServiceSubclass.addFunction {
      name = Name.identifier("call")
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = pluginContext.symbols.byteArrayType
    }.apply {
      addDispatchReceiver {
        type = inboundServiceSubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("inboundCall")
        type = ktBridgeApis.inboundCall.defaultType
      }
      overriddenSymbols += ktBridgeApis.inboundServiceCall
    }

    callFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callFunction.symbol,
    ) {
      +irReturn(
        irCallFunctionBody(callFunction, serviceProperty)
      )
    }

    inboundServiceSubclass.addFakeOverrides(pluginContext.irBuiltIns, listOf(callFunction))

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

  // val service: EchoService = TestingEchoService("hello")
  private fun irServiceProperty(inboundServiceSubclass: IrClass): IrProperty {
    val result = irFactory.createProperty(
      startOffset = inboundServiceSubclass.startOffset,
      endOffset = inboundServiceSubclass.endOffset,
      origin = IrDeclarationOrigin.DEFINED,
      symbol = IrPropertySymbolImpl(),
      name = Name.identifier("service"),
      visibility = DescriptorVisibilities.PRIVATE,
      modality = Modality.FINAL,
      isVar = false,
      isConst = false,
      isLateinit = false,
      isDelegated = false,
      isExternal = false,
      isExpect = false,
      isFakeOverride = false,
      containerSource = null,
    ).apply {
      parent = inboundServiceSubclass
    }

    result.backingField = irFactory.createField(
      startOffset = inboundServiceSubclass.startOffset,
      endOffset = inboundServiceSubclass.endOffset,
      origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD,
      symbol = IrFieldSymbolImpl(),
      name = result.name,
      type = serviceInterface,
      visibility = DescriptorVisibilities.PRIVATE,
      isFinal = true,
      isExternal = false,
      isStatic = false
    ).apply {
      parent = inboundServiceSubclass
      correspondingPropertySymbol = result.symbol
      initializer = irFactory.createExpressionBody(original.getValueArgument(2)!!)
    }

    result.getter = irFactory.createFunction(
      startOffset = inboundServiceSubclass.startOffset,
      endOffset = inboundServiceSubclass.endOffset,
      origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR,
      name = Name.special("<get-service>"),
      visibility = DescriptorVisibilities.PRIVATE,
      isExternal = false,
      symbol = IrSimpleFunctionSymbolImpl(),
      modality = Modality.FINAL,
      returnType = serviceInterface,
      isInline = false,
      isTailrec = false,
      isSuspend = false,
      isOperator = false,
      isInfix = false,
      isExpect = false,
      isFakeOverride = false,
      containerSource = null
    ).apply {
      parent = inboundServiceSubclass
      correspondingPropertySymbol = result.symbol
      createDispatchReceiverParameter()
      irFunctionBody(
        context = pluginContext,
        scopeOwnerSymbol = symbol
      ) {
        +irReturn(
          value = irGetField(
            irGet(dispatchReceiverParameter!!),
            result.backingField!!
          )
        )
      }
    }

    return result
  }

  /**
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
    for (bridgedFunction in serviceInterfaceType.functions) {
      // TODO(jwilson): find a better way to skip equals()/hashCode()/toString()
      if (bridgedFunction.owner.isFakeOverride) continue

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
      type = returnType,
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
    return irCall(
      type = valueParameter.type,
      callee = ktBridgeApis.inboundCallParameter,
      typeArgumentsCount = 1,
    ).apply {
      dispatchReceiver = irGetInboundCallParameter(callFunction)
      putTypeArgument(0, valueParameter.type)
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

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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
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
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
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
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.putClassTypeArgument
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `Zipline.set()` that takes a name and a service:
 *
 * ```
 * zipline.set("helloService", TestingEchoService("hello"))
 * ```
 *
 * to the overload that takes a name and an `InboundBridge`:
 *
 * ```
 * zipline.set(
 *   "helloService",
 *   object : InboundBridge<EchoService>() {
 *     override val service: EchoService = TestingEchoService("hello")
 *     override fun create(context: Context): InboundCallHandler {
 *       return object : InboundCallHandler {
 *         val serializer_0 = context.serializersModule.serializer<EchoRequest>()
 *         val serializer_1 = context.serializersModule.serializer<EchoResponse>()
 *         override val context: Context = context
 *         override fun call(inboundCall: InboundCall): Array<String> {
 *           return when {
 *             inboundCall.funName == "echo" -> {
 *               inboundCall.result(
 *                 serializer_1,
 *                 s.echo(
 *                   inboundCall.parameter(serializer_0)
 *                 )
 *               )
 *             }
 *             else -> inboundCall.unexpectedFunction()
 *           }
 *         }
 *       }
 *     }
 *   }
 * )
 * ```
 *
 * For suspending functions, everything is the same except overridden method is `callSuspending()`.
 * Both methods area always overridden, but may contain only the `unexpectedFunction()` case.
 *
 * This also rewrites calls to `ZiplineReference()`, which is similar to `Zipline.set()` but without
 * a leading name parameter:
 *
 * ```
 * val reference = ZiplineReference<EchoService>(
 *   TestingEchoService("hello")
 * )
 * ```
 *
 * The above is rewritten to:
 *
 * ```
 * val reference = InboundZiplineReference<EchoService>(
 *   object : InboundBridge<EchoService>() {
 *     ...
 *   }
 * )
 * ```
 */
internal class InboundBridgeRewriter(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
  private val rewrittenFunction: IrFunctionSymbol,
) {
  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    ziplineApis,
    original,
    "Zipline.set()",
    original.getTypeArgument(0)!!
  )

  private val irFactory: IrFactory = pluginContext.irFactory

  fun rewrite(): IrFunctionAccessExpression {
    when (rewrittenFunction) {
      is IrSimpleFunctionSymbol -> {
        // Call zipline.set(...).
        return IrCallImpl(
          startOffset = original.startOffset,
          endOffset = original.endOffset,
          type = original.type,
          symbol = rewrittenFunction,
          typeArgumentsCount = 1,
          valueArgumentsCount = 2,
          origin = original.origin,
          superQualifierSymbol = original.superQualifierSymbol,
        ).apply {
          dispatchReceiver = original.dispatchReceiver
          putTypeArgument(0, bridgedInterface.type)
          putValueArgument(0, original.getValueArgument(0))
          putValueArgument(1, irNewInboundBridge())
          patchDeclarationParents(declarationParent)
        }
      }
      is IrConstructorSymbol -> {
        // Construct InboundZiplineReference<EchoService>(...).
        return IrConstructorCallImpl(
          startOffset = original.startOffset,
          endOffset = original.endOffset,
          type = rewrittenFunction.owner.parentAsClass.typeWith(bridgedInterface.type),
          symbol = rewrittenFunction,
          typeArgumentsCount = 1,
          constructorTypeArgumentsCount = 0,
          valueArgumentsCount = 1,
          origin = original.origin,
        ).apply {
          putValueArgument(0, irNewInboundBridge())
          putClassTypeArgument(0, bridgedInterface.type)
          patchDeclarationParents(declarationParent)
        }
      }
      else -> error("unexpected function")
    }
  }

  // object : InboundBridge<EchoService>() {
  // }
  private fun irNewInboundBridge(): IrContainerExpression {
    val inboundBridgeOfT = ziplineApis.inboundBridge.typeWith(bridgedInterface.type)
    val inboundBridgeSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
      startOffset = original.startOffset
      endOffset = original.endOffset
    }.apply {
      superTypes = listOf(inboundBridgeOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    // InboundBridge<EchoService>()
    val superConstructor = ziplineApis.inboundBridge.constructors.single()
    val constructor = inboundBridgeSubclass.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }.apply {
      irConstructorBody(pluginContext, original.startOffset, original.endOffset) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor,
          typeArgumentsCount = 1,
          valueArgumentsCount = 0,
        ) {
          putTypeArgument(0, bridgedInterface.type)
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = inboundBridgeSubclass.symbol,
        )
      }
    }

    val serviceProperty = irServiceProperty(inboundBridgeSubclass)
    inboundBridgeSubclass.declarations += serviceProperty

    val createFunction = irCreateFunction(inboundBridgeSubclass, serviceProperty)

    // We add overrides here so we can use them below.
    inboundBridgeSubclass.addFakeOverrides(
      pluginContext.irBuiltIns,
      listOf(serviceProperty, createFunction)
    )

    return IrBlockBodyBuilder(
      startOffset = original.startOffset,
      endOffset = original.endOffset,
      context = pluginContext,
      scope = scope.scope,
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = inboundBridgeSubclass.defaultType
      +inboundBridgeSubclass
      +irCall(constructor.symbol, type = inboundBridgeSubclass.defaultType)
    }
  }

  /** Override `InboundBridge.create()`. */
  private fun irCreateFunction(
    inboundBridgeSubclass: IrClass,
    serviceProperty: IrProperty,
  ): IrSimpleFunction {
    // override fun create(context: Context): InboundCallHandler {
    // }
    val result = inboundBridgeSubclass.addFunction {
      name = ziplineApis.inboundBridgeCreate.owner.name
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = ziplineApis.inboundCallHandler.defaultType
    }.apply {
      addDispatchReceiver {
        type = inboundBridgeSubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("context")
        type = ziplineApis.inboundBridgeContext.defaultType
      }
      overriddenSymbols += ziplineApis.inboundBridgeCreate
    }
    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol,
      startOffset = original.startOffset,
      endOffset = original.endOffset,
    ) {
      +irReturn(
        irNewInboundCallHandler(
          contextParameter = result.valueParameters[0],
          inboundBridgeThis = result.dispatchReceiverParameter!!,
          serviceProperty = serviceProperty,
        )
      )
    }
    return result
  }

  private fun irNewInboundCallHandler(
    contextParameter: IrValueParameter,
    inboundBridgeThis: IrValueParameter,
    serviceProperty: IrProperty,
  ): IrContainerExpression {
    // object : InboundCallHandler {
    // }
    val inboundCallHandler = ziplineApis.inboundCallHandler.defaultType
    val inboundCallHandlerSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
      startOffset = original.startOffset
      endOffset = original.endOffset
    }.apply {
      superTypes = listOf(inboundCallHandler)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = inboundCallHandlerSubclass.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }.apply {
      irConstructorBody(pluginContext, original.startOffset, original.endOffset) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.any.constructors.single(),
          typeArgumentsCount = 0,
          valueArgumentsCount = 0
        )
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = inboundCallHandlerSubclass.symbol,
        )
      }
    }

    val contextProperty = irContextProperty(inboundCallHandlerSubclass, contextParameter)
    inboundCallHandlerSubclass.declarations += contextProperty

    bridgedInterface.declareSerializerProperties(inboundCallHandlerSubclass, contextParameter)

    val callFunction = irCallFunction(
      inboundCallHandler = inboundCallHandlerSubclass,
      callSuspending = false
    )
    val callSuspendingFunction = irCallFunction(
      inboundCallHandler = inboundCallHandlerSubclass,
      callSuspending = true
    )

    // We add overrides here so we can call them below.
    inboundCallHandlerSubclass.addFakeOverrides(
      pluginContext.irBuiltIns,
      listOf(contextProperty, callFunction, callSuspendingFunction)
    )

    callFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callFunction.symbol,
      startOffset = original.startOffset,
      endOffset = original.endOffset,
    ) {
      +irReturn(
        irCallFunctionBody(callFunction, inboundBridgeThis, serviceProperty)
      )
    }
    callSuspendingFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callSuspendingFunction.symbol,
      startOffset = original.startOffset,
      endOffset = original.endOffset,
    ) {
      +irReturn(
        irCallFunctionBody(callSuspendingFunction, inboundBridgeThis, serviceProperty)
      )
    }

    return IrBlockBodyBuilder(
      startOffset = original.startOffset,
      endOffset = original.endOffset,
      context = pluginContext,
      scope = scope.scope,
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = inboundCallHandlerSubclass.defaultType
      +inboundCallHandlerSubclass
      +irCall(constructor.symbol, type = inboundCallHandlerSubclass.defaultType)
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
      name = inboundBridgeCall.owner.name
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = ziplineApis.stringArrayType
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        type = inboundCallHandler.defaultType
      }
      addValueParameter {
        name = Name.identifier("inboundCall")
        type = ziplineApis.inboundCall.defaultType
      }
      overriddenSymbols += inboundBridgeCall
    }
  }

  // override val service: EchoService = TestingEchoService("hello")
  private fun irServiceProperty(inboundBridgeSubclass: IrClass): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = bridgedInterface.type,
      declaringClass = inboundBridgeSubclass,
      propertyName = Name.identifier("service"),
      overriddenProperty = ziplineApis.inboundBridgeService,
    ) {
      irExprBody(original.getValueArgument(original.valueArgumentsCount - 1)!!)
    }
  }

  // override val context: InboundCallHandler.Context = context
  private fun irContextProperty(
    inboundCallHandlerSubclass: IrClass,
    contextParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.inboundCallHandlerContext.owner.getter!!.returnType,
      declaringClass = inboundCallHandlerSubclass,
      propertyName = Name.identifier("context"),
      overriddenProperty = ziplineApis.inboundCallHandlerContext
    ) {
      irExprBody(irGet(contextParameter))
    }
  }

  /**
   * The body of either `InboundBridge.call()` or `InboundBridge.callSuspending()`.
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
    inboundBridgeThis: IrValueParameter,
    serviceProperty: IrProperty,
  ): IrExpression {
    val result = mutableListOf<IrBranch>()

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
            callFunction = callFunction,
            resultExpression = irCallServiceFunction(
              callFunction = callFunction,
              inboundBridgeThis = inboundBridgeThis,
              serviceProperty = serviceProperty,
              bridgedFunction = bridgedFunction
            )
          )
        }
      )
    }

    // Add an else clause that calls unexpectedFunction().
    result += irElseBranch(
      expression = irCallUnexpectedFunction(
        callFunction = callFunction,
      )
    )

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
    callFunction: IrSimpleFunction,
    inboundBridgeThis: IrValueParameter,
    serviceProperty: IrProperty,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    val getServiceCall = irService(inboundBridgeThis, serviceProperty)
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

  /** `inboundBridge.service` */
  private fun IrBuilderWithScope.irService(
    inboundBridgeThis: IrValueParameter,
    serviceProperty: IrProperty,
  ): IrExpression {
    return irCall(
      callee = serviceProperty.getter!!,
    ).apply {
      dispatchReceiver = irGet(inboundBridgeThis)
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
}

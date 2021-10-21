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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `Zipline.get()` that takes a name:
 *
 * ```
 * val helloService: EchoService = zipline.get("helloService")
 * ```
 *
 * to the overload that takes a name and an `OutboundBridge`:
 *
 * ```
 * val helloService: EchoService = zipline.get(
 *   "helloService",
 *   object : OutboundBridge<EchoService>() {
 *     override fun create(context: Context): EchoService {
 *       val serializer_0 = context.serializersModule.serializer<EchoRequest>()
 *       val serializer_1 = context.serializersModule.serializer<EchoResponse>()
 *       return object : EchoService {
 *         override fun echo(request: EchoRequest): EchoResponse {
 *           val outboundCall = context.newCall("echo", 1)
 *           outboundCall.parameter<EchoRequest>(serializer_0, request)
 *           return outboundCall.invoke(serializer_1)
 *         }
 *       }
 *     }
 *   }
 * )
 * ```
 *
 * For suspending functions, everything is the same except the call is to `invokeSuspending()`.
 *
 * This also rewrites calls to `ZiplineReference.get()`, which is similar to
 * `ZiplineReference.get()` but without a leading name parameter:
 *
 * ```
 * val reference: ZiplineReference<EchoService> = ...
 * val helloService = reference.get()
 * ```
 *
 * The above is rewritten to:
 *
 * ```
 * val reference: ZiplineReference<EchoService> = ...
 * val helloService = reference.get(
 *   object : OutboundBridge<EchoService>() {
 *     ...
 *   }
 * )
 * ```
 */
internal class OutboundBridgeRewriter(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
  private val rewrittenGetFunction: IrSimpleFunctionSymbol,
) {
  /** The user-defined interface type, like `EchoService` above. */
  private val bridgedInterfaceType: IrType = run {
    if (original.typeArgumentsCount == 1) {
      // If it's Zipline.get(), the interface type is on get().
      original.getTypeArgument(0)!!
    } else {
      // If it's ZiplineReference.get(), the interface type is on ZiplineReference.
      val ziplineReferenceType = original.dispatchReceiver!!.type as IrSimpleType
      ziplineReferenceType.arguments[0].typeOrNull!!
    }
  }

  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    ziplineApis,
    original,
    "Zipline.get()",
    bridgedInterfaceType
  )

  private val irFactory: IrFactory
    get() = pluginContext.irFactory

  fun rewrite(): IrCall {
    return irCall(original, rewrittenGetFunction).apply {
      putValueArgument(valueArgumentsCount - 1, irNewOutboundBridge())
      patchDeclarationParents(declarationParent)
    }
  }

  private fun irNewOutboundBridge(): IrContainerExpression {
    val outboundBridgeOfT = ziplineApis.outboundBridge.typeWith(bridgedInterface.type)
    val outboundBridgeSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      superTypes = listOf(outboundBridgeOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    // OutboundBridge<EchoService>()
    val superConstructor = ziplineApis.outboundBridge.constructors.single()
    val constructor = outboundBridgeSubclass.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }.apply {
      irConstructorBody(pluginContext) { statements ->
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
          classSymbol = outboundBridgeSubclass.symbol,
        )
      }
    }

    // override fun create(callFactory: OutboundCall.Factory): EchoService {
    // }
    val createFunction = outboundBridgeSubclass.addFunction {
      name = Name.identifier("create")
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = bridgedInterface.type
    }.apply {
      addDispatchReceiver {
        type = outboundBridgeSubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("context")
        type = ziplineApis.outboundBridgeContext.defaultType
      }
      overriddenSymbols += ziplineApis.outboundBridgeCreate
    }

    // We add overrides here so we can use them below.
    outboundBridgeSubclass.addFakeOverrides(pluginContext.irBuiltIns, listOf(createFunction))

    createFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = scope.scope.scopeOwnerSymbol
    ) {
      irCreateFunctionBody(createFunction, createFunction.valueParameters[0])
    }

    return IrBlockBodyBuilder(
      startOffset = SYNTHETIC_OFFSET,
      endOffset = SYNTHETIC_OFFSET,
      context = pluginContext,
      scope = scope.scope,
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = outboundBridgeSubclass.defaultType
      +outboundBridgeSubclass
      +irCall(constructor.symbol, type = outboundBridgeSubclass.defaultType)
    }
  }

  private fun IrBlockBodyBuilder.irCreateFunctionBody(
    createFunction: IrSimpleFunction,
    contextParameter: IrValueParameter,
  ) {
    // return object : EchoService {
    //   ...
    // }
    val clientImplementation = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = createFunction
      superTypes = listOf(bridgedInterface.type)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = clientImplementation.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }
    constructor.irConstructorBody(pluginContext) { statements ->
      statements += irDelegatingConstructorCall(
        context = pluginContext,
        symbol = ziplineApis.any.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = pluginContext,
        classSymbol = clientImplementation.symbol,
      )
    }

    bridgedInterface.declareSerializerProperties(clientImplementation, contextParameter)

    for (bridgedFunction in bridgedInterface.bridgedFunctions) {
      clientImplementation.irBridgedFunction(
        createFunction = createFunction,
        bridgedFunction = bridgedFunction.owner,
      )
    }

    clientImplementation.addFakeOverrides(pluginContext.irBuiltIns, listOf())

    +irReturn(
      value = irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
        resultType = clientImplementation.defaultType

        +clientImplementation
        +irCall(constructor.symbol, type = clientImplementation.defaultType)
      },
      returnTargetSymbol = createFunction.symbol,
      type = pluginContext.irBuiltIns.nothingType,
    )
  }

  private fun IrClass.irBridgedFunction(
    createFunction: IrSimpleFunction,
    bridgedFunction: IrSimpleFunction,
  ): IrSimpleFunction {
    val functionReturnType = bridgedInterface.resolveTypeParameters(bridgedFunction.returnType)
    val result = addFunction {
      name = bridgedFunction.name
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      isSuspend = bridgedFunction.isSuspend
      returnType = functionReturnType
    }.apply {
      overriddenSymbols = listOf(bridgedFunction.symbol)
      addDispatchReceiver {
        type = defaultType
      }
    }

    for (valueParameter in bridgedFunction.valueParameters) {
      result.addValueParameter {
        name = valueParameter.name
        type = bridgedInterface.resolveTypeParameters(valueParameter.type)
      }
    }

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val newCall = irCall(ziplineApis.outboundBridgeContextNewCall).apply {
        dispatchReceiver = irGet(createFunction.valueParameters[0])
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

      // outboundCall.parameter<EchoRequest>(serializer_0, request)
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
      //   return outboundCall.<EchoResponse>invoke(serializer_2)
      //   return outboundCall.<EchoResponse>invokeSuspending(serializer_2)
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

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
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
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
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `QuickJs.get()` that takes a name and a `JsAdapter`:
 *
 * ```
 * val helloService: EchoService = quickJs.get("helloService", EchoJsAdapter)
 * ```
 *
 * to the overload that takes a name and an `OutboundClientFactory`:
 *
 * ```
 * val helloService: EchoService = quickJs.get(
 *   "helloService",
 *   object : OutboundClientFactory<EchoService>(EchoJsAdapter) {
 *     override fun create(outboundCallFactory: OutboundCall.Factory): EchoService {
 *       return object : EchoService {
 *         override fun echo(request: EchoRequest): EchoResponse {
 *           val outboundCall = outboundCallFactory.create("echo", 1)
 *           outboundCall.parameter(request)
 *           return outboundCall.invoke()
 *         }
 *       }
 *     }
 *   })
 * ```
 */
internal class KtBridgeGetRewriter(
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
    return irCall(original, ktBridgeApis.rewrittenGetFunction).apply {
      putValueArgument(1, irNewOutboundClientFactory())
    }
  }

  /** Returns the interface of the type argument for the original call. */
  private fun serviceInterfaceType(): IrClassSymbol {
    val result = pluginContext.referenceClass(serviceInterface.classFqName ?: FqName.ROOT)
    if (result == null || !result.owner.isInterface) {
      throw KtBridgeCompilationException(
        element = original,
        message = "The type argument to QuickJs.get() must be an interface type",
      )
    }
    return result
  }

  private fun irNewOutboundClientFactory(): IrContainerExpression {
    val outboundClientFactoryOfT = ktBridgeApis.outboundClientFactory.typeWith(serviceInterface)
    val outboundClientFactorySubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = declarationParent
      superTypes = listOf(outboundClientFactoryOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    // OutboundClientFactory<EchoService>(EchoJsAdapter)
    val superConstructor = ktBridgeApis.outboundClientFactory.constructors.single()
    val constructor = outboundClientFactorySubclass.addConstructor {
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
          classSymbol = outboundClientFactorySubclass.symbol,
        )
      }
    }

    // override fun create(callFactory: OutboundCall.Factory): EchoService {
    // }
    val createFunction = outboundClientFactorySubclass.addFunction {
      name = Name.identifier("create")
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = serviceInterface
    }.apply {
      addDispatchReceiver {
        type = outboundClientFactorySubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("outboundCallFactory")
        type = ktBridgeApis.outboundCallFactory.defaultType
      }
      overriddenSymbols += ktBridgeApis.outboundClientFactoryCreate
    }

    // Add overrides before defining create()'s body, so we can use overridden properties.
    outboundClientFactorySubclass.addFakeOverrides(pluginContext.irBuiltIns, listOf(createFunction))

    createFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = scope.scope.scopeOwnerSymbol
    ) {
      irCreateFunctionBody(createFunction)
    }

    return IrBlockBodyBuilder(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      context = pluginContext,
      scope = scope.scope,
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = outboundClientFactorySubclass.defaultType
      +outboundClientFactorySubclass
      +irCall(constructor.symbol, type = outboundClientFactorySubclass.defaultType)
    }
  }

  private fun IrBlockBodyBuilder.irCreateFunctionBody(createFunction: IrSimpleFunction) {
    // return object : EchoService {
    //   ...
    // }
    val clientImplementation = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = createFunction
      superTypes = listOf(serviceInterface)
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
        symbol = ktBridgeApis.any.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = pluginContext,
        classSymbol = clientImplementation.symbol,
      )
    }

    for (bridgedFunction in serviceInterfaceType.functions.toList()) {
      if (bridgedFunction.owner.isFakeOverride) continue

      clientImplementation.irBridgedFunction(
        createFunction = createFunction,
        bridgedFunction = bridgedFunction.owner
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
    bridgedFunction: IrSimpleFunction
  ): IrSimpleFunction {
    val result = addFunction {
      name = bridgedFunction.name
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = bridgedFunction.returnType
    }.apply {
      overriddenSymbols = listOf(bridgedFunction.symbol)
      addDispatchReceiver {
        type = defaultType
      }
    }

    for (valueParameter in bridgedFunction.valueParameters) {
      result.addValueParameter {
        name = valueParameter.name
        type = valueParameter.type
      }
    }

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val callCreate = irCall(ktBridgeApis.outboundCallFactoryCreate).apply {
        dispatchReceiver = irGet(createFunction.valueParameters[0])
        putValueArgument(0, irString(bridgedFunction.name.identifier))
        putValueArgument(1, irInt(bridgedFunction.valueParameters.size))
      }

      val outboundCallLocal = irTemporary(
        value = callCreate,
        nameHint = "outboundCall",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      for (valueParameter in result.valueParameters) {
        +irCall(callee = ktBridgeApis.outboundCallParameter).apply {
          dispatchReceiver = irGet(outboundCallLocal)
          putTypeArgument(0, valueParameter.type)
          putValueArgument(
            0,
            irGet(
              type = valueParameter.type,
              variable = valueParameter.symbol,
            )
          )
        }
      }

      +irReturn(
        value = irCall(callee = ktBridgeApis.outboundCallInvoke).apply {
          dispatchReceiver = irGet(outboundCallLocal)
          type = bridgedFunction.returnType
          putTypeArgument(0, result.returnType)
        },
        returnTargetSymbol = result.symbol,
        type = pluginContext.irBuiltIns.nothingType,
      )
    }

    return result
  }
}

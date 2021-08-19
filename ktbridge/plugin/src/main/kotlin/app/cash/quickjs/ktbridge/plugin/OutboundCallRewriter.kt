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
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.Scope
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
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites calls to `createJsService` with two arguments:
 *
 * ```
 * val helloService = createJsClient<EchoService>(EchoJsAdapter, "testing")
 * ```
 *
 * to create a new subtype of JsClient:
 *
 * ```
 * val helloService = object : JsClient<EchoService>(
 *   jsAdapter = EchoJsAdapter,
 *   webpackModuleName = "testing",
 *   packageName = "app.cash.quickjs.ktbridge.testing",
 *   propertyName = "yoService"
 * ) {
 *   override fun get(internalBridge: InternalBridge): EchoService {
 *     return object : EchoService {
 *       override fun echo(request: EchoRequest): EchoResponse {
 *         val outboundCall = OutboundCall(jsAdapter, internalBridge, "echo", 1)
 *         outboundCall.parameter(request)
 *         return outboundCall.invoke()
 *       }
 *     }
 *   }
 * }
 * ```
 */
class OutboundCallRewriter(
  private val pluginContext: IrPluginContext,
  private val backingField: IrField,
) {
  private val initializer: IrExpressionBody = backingField.initializer!!
  private val initializerCall: IrCall = initializer.expression as IrCall

  private val irFactory: IrFactory
    get() = pluginContext.irFactory

  private val jsClientClass = pluginContext.referenceClass(JS_CLIENT) ?: error("TODO")
  private val propertyJsClientJsAdapter = pluginContext.referenceProperties(JS_CLIENT_JS_ADAPTER).single()
  private val anyClass = pluginContext.referenceClass(FqName("kotlin.Any")) ?: error("TODO")
  private val classInternalBridge = pluginContext.referenceClass(INTERNAL_BRIDGE) ?: error("TODO")
  private val classOutboundCall = pluginContext.referenceClass(OUTBOUND_CALL) ?: error("TODO")
  private val functionOutboundCallParameter = pluginContext.referenceFunctions(OUTBOUND_CALL_PARAMETER)
    .single { it.owner.valueParameters.size == 1 }
  private val functionOutboundCallInvoke = pluginContext.referenceFunctions(OUTBOUND_CALL_INVOKE)
    .single { it.owner.valueParameters.isEmpty() }
  private val serviceInterface = initializerCall.getTypeArgument(0) ?: error("TODO")

  fun rewrite() {
    val jsClientClassOfT = jsClientClass.typeWith(serviceInterface)
    val jsClientSubclass = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = backingField
      superTypes = listOf(jsClientClassOfT)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    /*
     * ```
     * JsClient<EchoService>(
     *   jsAdapter = EchoJsAdapter,
     *   webpackModuleName = "testing",
     *   packageName = "app.cash.quickjs.ktbridge.testing",
     *   propertyName = "yoService"
     * )
     * ```
     */
    val superConstructor = jsClientClass.constructors.single()
    val packageName = backingField.getPackageFragment()?.fqName?.toString() ?: ""
    val propertyName = backingField.symbol.owner.name.identifier
    val constructor = jsClientSubclass.addConstructor {
      origin = IrDeclarationOrigin.DEFINED
      visibility = DescriptorVisibilities.PUBLIC
      isPrimary = true
    }.apply {
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = superConstructor,
          typeArgumentsCount = 1,
          valueArgumentsCount = 4
        ) {
          putTypeArgument(0, serviceInterface)
          putValueArgument(0, initializerCall.getValueArgument(0))
          putValueArgument(1, initializerCall.getValueArgument(1))
          putValueArgument(2, irString(packageName))
          putValueArgument(3, irString(propertyName))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = jsClientSubclass.symbol,
        )
      }
    }

    /*
     * ```
     * override fun get(internalBridge: InternalBridge): EchoService {
     * ```
     */
    val getFunction = jsClientSubclass.addFunction {
      name = Name.identifier("get")
      visibility = DescriptorVisibilities.PUBLIC
      modality = Modality.OPEN
      returnType = serviceInterface
    }.apply {
      addDispatchReceiver {
        type = jsClientSubclass.defaultType
      }
      addValueParameter {
        name = Name.identifier("internalBridge")
        type = classInternalBridge.defaultType
      }
      overriddenSymbols += jsClientClass.functions
        .single { it.owner.name.identifier == "get" && it.owner.modality == Modality.ABSTRACT }
    }

    // Add overrides before defining the get() function's body, so we can use overridden properties.
    jsClientSubclass.addFakeOverrides(pluginContext.irBuiltIns, listOf(getFunction))
    val jsAdapterProperty = jsClientSubclass.properties
      .single { it.name == JS_CLIENT_JS_ADAPTER.shortName() }
    jsAdapterProperty.overriddenSymbols += propertyJsClientJsAdapter

    getFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = backingField.symbol
    ) {
      irGetFunctionBody(getFunction)
    }

    initializer.expression = IrBlockBodyBuilder(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      context = pluginContext,
      scope = Scope(backingField.symbol),
    ).irBlock(origin = IrStatementOrigin.OBJECT_LITERAL) {
      resultType = jsClientSubclass.defaultType
      +jsClientSubclass
      +irCall(constructor.symbol, type = jsClientSubclass.defaultType)
    }
  }

  /**
   * ```
   * return object : EchoService {
   *   ...
   * }
   * ```
   */
  private fun IrBlockBodyBuilder.irGetFunctionBody(getFunction: IrSimpleFunction) {
    val clientImplementation = irFactory.buildClass {
      name = Name.special("<no name provided>")
      visibility = DescriptorVisibilities.LOCAL
    }.apply {
      parent = getFunction
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
        symbol = anyClass.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = pluginContext,
        classSymbol = clientImplementation.symbol,
      )
    }

    val serviceInterfaceType = pluginContext.referenceClass(serviceInterface.classFqName!!)
      ?: error("TODO")

    for (bridgedFunction in serviceInterfaceType.functions.toList()) {
      if (bridgedFunction.owner.isFakeOverride) continue

      clientImplementation.irBridgedFunction(
        getFunction = getFunction,
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
      returnTargetSymbol = getFunction.symbol,
      type = pluginContext.irBuiltIns.nothingType,
    )
  }

  private fun IrClass.irBridgedFunction(
    getFunction: IrSimpleFunction,
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

    val primaryConstructor = classOutboundCall.owner.primaryConstructor!!

    val jsClientSubclass = getFunction.parent as IrClass
    val jsAdapterProperty = jsClientSubclass.properties
      .single { it.name == JS_CLIENT_JS_ADAPTER.shortName() }

    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val callConstructor = irCall(primaryConstructor.symbol).apply {
        putValueArgument(0,
          irCall(
            callee = jsAdapterProperty.getter!!.symbol,
          ).apply {
            dispatchReceiver = irGet(getFunction.dispatchReceiverParameter!!)
          }
        )
        putValueArgument(1, irGet(
          type = getFunction.valueParameters[0].type,
          variable = getFunction.valueParameters[0].symbol,
        ))
        putValueArgument(2, irString(bridgedFunction.name.identifier))
        putValueArgument(3, irInt(bridgedFunction.valueParameters.size))
      }

      val outboundCallLocal = irTemporary(
        value = callConstructor,
        nameHint = "outboundCall",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      for (valueParameter in result.valueParameters) {
        +irCall(callee = functionOutboundCallParameter).apply {
          dispatchReceiver = irGet(outboundCallLocal)
          putTypeArgument(0, valueParameter.type)
          putValueArgument(0, irGet(
            type = valueParameter.type,
            variable = valueParameter.symbol,
          ))
        }
      }

      +irReturn(
        value = irCall(callee = functionOutboundCallInvoke).apply {
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

  companion object {
    val JS_CLIENT = FqName("app.cash.quickjs.ktbridge.JsClient")
    val JS_CLIENT_JS_ADAPTER = JS_CLIENT.child(Name.identifier("jsAdapter"))
    val CREATE_JS_CLIENT = FqName("app.cash.quickjs.ktbridge.createJsClient")
    val OUTBOUND_CALL = FqName("app.cash.quickjs.ktbridge.OutboundCall")
    val OUTBOUND_CALL_PARAMETER = OUTBOUND_CALL.child(Name.identifier("parameter"))
    val OUTBOUND_CALL_INVOKE = OUTBOUND_CALL.child(Name.identifier("invoke"))
    val INTERNAL_BRIDGE = FqName("app.cash.quickjs.ktbridge.InternalBridge")
  }
}

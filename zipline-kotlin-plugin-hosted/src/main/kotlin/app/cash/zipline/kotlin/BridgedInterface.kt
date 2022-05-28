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
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * A user-defined interface (like `EchoService` or `Callback<String>`) and support for generating
 * its `ZiplineServiceAdapter`.
 *
 * This class tracks the interface type (like `EchoService` or `Callback<String>`) and its
 * implementation class (that doesn't know its generic parameters).
 *
 * This class can declare a `KSerializer` property for each unique parameter type and return type on
 * the interface. Use [declareSerializerProperties] to create these, then [serializerExpression] to
 * access a specific serializer. Declaring serializers is useful to fast fail if ever a serializer
 * is required but not configured.
 */
internal class BridgedInterface(
  private val pluginContext: IrPluginContext,
  private val messageCollector: MessageCollector,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,

  /** A specific type identifier that knows the values of its generic parameters. */
  val type: IrType,

  /** A potentially-generic declaration that doesn't have values for its generic parameters. */
  private val classSymbol: IrClassSymbol,
) {
  /** Maps types to the property holding the corresponding serializer. */
  private val typeToSerializerProperty = mutableMapOf<IrType, IrProperty>()

  val typeIrClass = classSymbol.owner

  // TODO(jwilson): support overloaded functions?
  val bridgedFunctionsWithOverrides: Map<String, List<IrSimpleFunctionSymbol>>
    get() {
      val result = mutableMapOf<String, MutableList<IrSimpleFunctionSymbol>>()
      for (supertype in listOf(classSymbol.owner.defaultType) + classSymbol.owner.superTypes) {
        val supertypeClass = supertype.getClass() ?: continue
        for (function in supertypeClass.functions) {
          if (function.name.identifier in NON_INTERFACE_FUNCTION_NAMES) continue
          val overrides = result.getOrPut(function.name.identifier) { mutableListOf() }
          overrides += function.symbol
        }
      }
      return result
    }

  val bridgedFunctions: List<IrSimpleFunctionSymbol>
    get() = bridgedFunctionsWithOverrides.values.map { it[0] }

  /** Declares properties for all the serializers needed to bridge this interface. */
  fun declareSerializerProperties(
    declaringClass: IrClass,
    contextParameter: IrValueParameter,
  ) {
    check(typeToSerializerProperty.isEmpty()) { "declareSerializerProperties() called twice?" }

    for (serializedType in serializedTypes()) {
      val serializerProperty = irSerializerProperty(
        declaringClass = declaringClass,
        contextParameter = contextParameter,
        type = serializedType,
        name = Name.identifier("serializer_${typeToSerializerProperty.size}")
      )
      declaringClass.declarations += serializerProperty
      typeToSerializerProperty[serializedType] = serializerProperty
    }
  }

  private fun irSerializerProperty(
    declaringClass: IrClass,
    contextParameter: IrValueParameter,
    type: IrType,
    name: Name
  ): IrProperty {
    // val serializer_0: KSerializer<EchoRequest> = ...
    val kSerializerOfT = ziplineApis.kSerializer.typeWith(type)
    return irVal(
      pluginContext = pluginContext,
      propertyType = kSerializerOfT,
      declaringClass = declaringClass,
      propertyName = name,
    ) {
      irExprBody(
        serializerExpression(type, contextParameter)
      )
    }
  }

  /** Declares local vars for all the serializers needed to bridge this interface. */
  fun declareSerializerTemporaries(
    statementsBuilder: IrStatementsBuilder<*>,
    serializersModuleParameter: IrValueParameter,
  ): Map<IrType, IrVariable> {
    return serializedTypes().associateWith {
      statementsBuilder.irTemporary(
        value = statementsBuilder.serializerExpression(it, serializersModuleParameter),
        nameHint = "serializer",
        isMutable = false,
      )
    }
  }

  private fun serializedTypes(): MutableSet<IrType> {
    val serializedTypes = mutableSetOf<IrType>()
    for (bridgedFunction in bridgedFunctions) {
      for (valueParameter in bridgedFunction.owner.valueParameters) {
        serializedTypes += resolveTypeParameters(valueParameter.type)
      }
      serializedTypes += resolveTypeParameters(bridgedFunction.owner.returnType)
    }
    return serializedTypes
  }

  private fun IrBuilderWithScope.serializerExpression(
    type: IrType,
    serializersModuleParameter: IrValueParameter,
  ): IrExpression {
    val kSerializerOfT = ziplineApis.kSerializer.typeWith(type)
    when {
      type.isSubtypeOfClass(ziplineApis.ziplineService) -> {
        // EchoService.Companion.Adapter
        // TODO(jwilson): this could be recursive (and fail with a stackoverflow) if a service
        //     has functions that take its own type. Fix by recursively applying the adapter
        //     transform?
        return AdapterGenerator(
          pluginContext,
          messageCollector,
          ziplineApis,
          this@BridgedInterface.scope,
          type.getClass()!!,
        ).adapterExpression()
      }

      type.classFqName == ziplineApis.flowFqName -> {
        // flowSerializer<Message>(context.serializersModule.serializer<Message>())
        val flowType = (type as IrSimpleType).arguments[0] as IrType
        return irCall(
          callee = ziplineApis.flowSerializerFunction,
          type = kSerializerOfT,
        ).apply {
          putTypeArgument(0, flowType)
          putValueArgument(0, serializerExpression(flowType, serializersModuleParameter))
        }
      }

      else -> {
        // One of:
        //   context.serializersModule.serializer<EchoRequest>()
        //   serializersModule.serializer<EchoRequest>()
        return irCall(
          callee = ziplineApis.serializerFunction,
          type = kSerializerOfT,
        ).apply {
          putTypeArgument(0, type)

          extensionReceiver = when (serializersModuleParameter.type.classFqName) {
            ziplineApis.outboundBridgeContextFqName -> {
              irCall(
                callee = ziplineApis.outboundBridgeContextSerializersModule.owner.getter!!,
              ).apply {
                dispatchReceiver = irGet(serializersModuleParameter)
              }
            }
            ziplineApis.serializersModule.defaultType.classFqName -> {
              irGet(serializersModuleParameter)
            }
            else -> error("unexpected Context type")
          }
        }
      }
    }
  }

  /** Call this on any declaration returned by [classSymbol] to fill in the generic parameters. */
  fun resolveTypeParameters(type: IrType): IrType {
    val simpleType = this.type as? IrSimpleType ?: return type
    val parameters = typeIrClass.typeParameters
    val arguments = simpleType.arguments.map { it as IrType }
    return type.substitute(parameters, arguments)
  }

  /** Returns an expression that returns the requested serializer. */
  fun serializerExpression(
    irBuilder: IrBuilderWithScope,
    serializerType: IrType,
    declaringInstance: IrValueParameter,
  ): IrFunctionAccessExpression {
    val property = typeToSerializerProperty[serializerType]!!
    return irBuilder.irCall(
      callee = property.getter!!
    ).apply {
      dispatchReceiver = irBuilder.irGet(declaringInstance)
    }
  }

  companion object {
    /** Don't bridge these. */
    private val NON_INTERFACE_FUNCTION_NAMES = mutableSetOf(
      "equals",
      "hashCode",
      "toString",
    )

    fun create(
      pluginContext: IrPluginContext,
      messageCollector: MessageCollector,
      ziplineApis: ZiplineApis,
      scope: ScopeWithIr,
      element: IrElement,
      functionName: String,
      type: IrType,
    ): BridgedInterface {
      val classSymbol = pluginContext.referenceClass(type.classFqName ?: FqName.ROOT)
      if (classSymbol == null || !classSymbol.owner.isInterface) {
        throw ZiplineCompilationException(
          element = element,
          message = "The type argument to $functionName must be an interface type" +
            " (but was ${type.classFqName})",
        )
      }

      return BridgedInterface(
        pluginContext,
        messageCollector,
        ziplineApis,
        scope,
        type,
        classSymbol
      )
    }
  }
}

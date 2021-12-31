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
import org.jetbrains.kotlin.backend.common.ir.createDispatchReceiverParameter
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.IrClassBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrPropertySymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun FqName.child(name: String) = child(Name.identifier(name))

/** Thrown on invalid or unexpected input code. */
class ZiplineCompilationException(
  override val message: String,
  val element: IrElement? = null,
  val severity: CompilerMessageSeverity = CompilerMessageSeverity.ERROR,
) : Exception(message)

/** Finds the line and column of [irElement] within this file. */
fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
  val sourceRangeInfo = fileEntry.getSourceRangeInfo(
    beginOffset = irElement?.startOffset ?: SYNTHETIC_OFFSET,
    endOffset = irElement?.endOffset ?: SYNTHETIC_OFFSET
  )
  return CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null
  )!!
}

/** `return ...` */
internal fun IrBuilderWithScope.irReturn(
  value: IrExpression,
  returnTargetSymbol: IrReturnTargetSymbol,
  type: IrType = value.type,
): IrReturn {
  return IrReturnImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = type,
    returnTargetSymbol = returnTargetSymbol,
    value = value,
  )
}

/** Set up reasonable defaults for a generated function or constructor. */
fun IrFunctionBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset
  this.endOffset = original.endOffset
  this.origin = IrDeclarationOrigin.DEFINED
  this.visibility = DescriptorVisibilities.PUBLIC
  this.modality = Modality.OPEN
  this.isPrimary = true
}

/** Set up reasonable defaults for a generated class. */
fun IrClassBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset
  this.endOffset = original.endOffset
  this.name = Name.special("<no name provided>")
  this.visibility = DescriptorVisibilities.LOCAL
}

/** Set up reasonable defaults for a value parameter. */
fun IrValueParameterBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset
  this.endOffset = original.endOffset
}

fun IrConstructor.irConstructorBody(
  context: IrGeneratorContext,
  blockBody: DeclarationIrBuilder.(MutableList<IrStatement>) -> Unit
) {
  val constructorIrBuilder = DeclarationIrBuilder(
    generatorContext = context,
    symbol = IrSimpleFunctionSymbolImpl(),
    startOffset = startOffset,
    endOffset = endOffset
  )
  body = context.irFactory.createBlockBody(
    startOffset = constructorIrBuilder.startOffset,
    endOffset = constructorIrBuilder.endOffset,
  ) {
    constructorIrBuilder.blockBody(statements)
  }
}

fun DeclarationIrBuilder.irDelegatingConstructorCall(
  context: IrGeneratorContext,
  symbol: IrConstructorSymbol,
  typeArgumentsCount: Int = 0,
  valueArgumentsCount: Int = 0,
  block: IrDelegatingConstructorCall.() -> Unit = {},
): IrDelegatingConstructorCall {
  val result = IrDelegatingConstructorCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.unitType,
    symbol = symbol,
    typeArgumentsCount = typeArgumentsCount,
    valueArgumentsCount = valueArgumentsCount
  )
  result.block()
  return result
}

fun DeclarationIrBuilder.irInstanceInitializerCall(
  context: IrGeneratorContext,
  classSymbol: IrClassSymbol,
): IrInstanceInitializerCall {
  return IrInstanceInitializerCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    classSymbol = classSymbol,
    type = context.irBuiltIns.unitType,
  )
}

fun IrSimpleFunction.irFunctionBody(
  context: IrGeneratorContext,
  scopeOwnerSymbol: IrSymbol,
  blockBody: IrBlockBodyBuilder.() -> Unit
) {
  val bodyBuilder = IrBlockBodyBuilder(
    startOffset = startOffset,
    endOffset = endOffset,
    context = context,
    scope = Scope(scopeOwnerSymbol),
  )
  body = bodyBuilder.blockBody {
    blockBody()
  }
}

/** Create a private val with a backing field and an accessor function. */
fun irVal(
  pluginContext: IrPluginContext,
  propertyType: IrType,
  declaringClass: IrClass,
  propertyName: Name,
  overriddenProperty: IrPropertySymbol? = null,
  initializer: IrBlockBuilder.() -> IrExpressionBody,
): IrProperty {
  val irFactory = pluginContext.irFactory
  val result = irFactory.createProperty(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.DEFINED,
    symbol = IrPropertySymbolImpl(),
    name = propertyName,
    visibility = overriddenProperty?.owner?.visibility ?: DescriptorVisibilities.PRIVATE,
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
    overriddenSymbols = listOfNotNull(overriddenProperty)
    parent = declaringClass
  }

  result.backingField = irFactory.createField(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD,
    symbol = IrFieldSymbolImpl(),
    name = result.name,
    type = propertyType,
    visibility = DescriptorVisibilities.PRIVATE,
    isFinal = true,
    isExternal = false,
    isStatic = false
  ).apply {
    parent = declaringClass
    correspondingPropertySymbol = result.symbol
    val initializerBuilder = IrBlockBuilder(
      startOffset = declaringClass.startOffset,
      endOffset = declaringClass.endOffset,
      context = pluginContext,
      scope = Scope(symbol),
    )
    this.initializer = initializerBuilder.initializer()
  }

  result.getter = irFactory.createFunction(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR,
    name = Name.special("<get-${propertyName.identifier}>"),
    visibility = overriddenProperty?.owner?.getter?.visibility ?: DescriptorVisibilities.PRIVATE,
    isExternal = false,
    symbol = IrSimpleFunctionSymbolImpl(),
    modality = Modality.FINAL,
    returnType = propertyType,
    isInline = false,
    isTailrec = false,
    isSuspend = false,
    isOperator = false,
    isInfix = false,
    isExpect = false,
    isFakeOverride = false,
    containerSource = null
  ).apply {
    parent = declaringClass
    correspondingPropertySymbol = result.symbol
    overriddenSymbols = listOfNotNull(overriddenProperty?.owner?.getter?.symbol)
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

fun irBlockBodyBuilder(
  irPluginContext: IrGeneratorContext,
  scopeWithIr: ScopeWithIr,
  original: IrElement
): IrBlockBodyBuilder {
  return IrBlockBodyBuilder(
    irPluginContext,
    scopeWithIr.scope,
    original.startOffset,
    original.endOffset
  )
}

/** This creates `companion object` if it doesn't exist already. */
fun getOrCreateCompanion(
  enclosing: IrClass,
  irPluginContext: IrPluginContext,
): IrClass {
  val existing = enclosing.declarations.firstOrNull {
    it is IrClass && it.name.identifier == "Companion"
  }
  if (existing != null) return existing as IrClass

  val irFactory = irPluginContext.irFactory
  val anyType = irPluginContext.referenceClass(irPluginContext.irBuiltIns.anyType.classFqName!!)!!
  val companionClass = irFactory.buildClass {
    initDefaults(enclosing)
    name = Name.identifier("Companion")
    visibility = DescriptorVisibilities.PUBLIC
    kind = ClassKind.OBJECT
    isCompanion = true
  }.apply {
    parent = enclosing
    superTypes = listOf(irPluginContext.irBuiltIns.anyType)
    createImplicitParameterDeclarationWithWrappedDescriptor()
  }

  companionClass.addConstructor {
    initDefaults(enclosing)
    visibility = DescriptorVisibilities.PRIVATE
  }.apply {
    irConstructorBody(irPluginContext) { statements ->
      statements += irDelegatingConstructorCall(
        context = irPluginContext,
        symbol = anyType.constructors.single()
      )
      statements += irInstanceInitializerCall(
        context = irPluginContext,
        classSymbol = companionClass.symbol,
      )
    }
  }

  enclosing.declarations.add(companionClass)
  return companionClass
}

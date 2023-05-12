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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isInterface

class ZiplineIrGenerationExtension(
  private val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val ziplineApis = ZiplineApis(pluginContext)

    val transformer = object : IrElementTransformerVoidWithContext() {
      override fun visitClassNew(declaration: IrClass): IrStatement {
        val declaration = super.visitClassNew(declaration) as IrClass

        try {
          if (declaration.isInterface &&
            declaration.superTypes.any { it.getClass()?.classId == ziplineApis.ziplineScopedClassId }
          ) {
            throw ZiplineCompilationException(
              element = declaration,
              message = "Only classes may implement ZiplineScoped, but" +
                " ${declaration.fqNameWhenAvailable} is an interface",
            )
          }

          if (declaration.isInterface &&
            declaration.superTypes.any { it.getClass()?.classId == ziplineApis.ziplineServiceClassId }
          ) {
            AdapterGenerator(
              pluginContext,
              ziplineApis,
              currentScope!!,
              declaration,
            ).generateAdapterIfAbsent()
            // When iterating on Kotlin compiler plugin, set breakpoint here and
            //  run `declarataion.dump()` to get current compiler parse tree.
          }
        } catch (e: ZiplineCompilationException) {
          messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
        }

        return declaration
      }

      override fun visitCall(expression: IrCall): IrExpression {
        val expression = super.visitCall(expression) as IrCall

        try {
          val takeOrBindFunction = ziplineApis.ziplineServiceAdapterFunctions[expression.symbol.toString()]
          if (takeOrBindFunction != null) {
            return AddAdapterArgumentRewriter(
              pluginContext,
              ziplineApis,
              currentScope!!,
              currentDeclarationParent!!,
              expression,
              takeOrBindFunction,
            ).rewrite()
          }
        } catch (e: ZiplineCompilationException) {
          messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
        }

        try {
          if (expression.symbol == ziplineApis.ziplineServiceSerializerTwoArg) {
            return CallAdapterConstructorRewriter(
              pluginContext,
              ziplineApis,
              currentScope!!,
              currentDeclarationParent!!,
              expression,
            ).rewrite()
          }
        } catch (e: ZiplineCompilationException) {
          messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
        }

        return expression
      }
    }

    moduleFragment.transform(transformer, null)
  }
}

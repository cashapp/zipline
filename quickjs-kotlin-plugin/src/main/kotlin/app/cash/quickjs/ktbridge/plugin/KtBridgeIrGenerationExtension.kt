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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

class KtBridgeIrGenerationExtension(
  private val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val ktBridgeApis = KtBridgeApis(pluginContext)

    val transformer = object : IrElementTransformerVoidWithContext() {
      override fun visitCall(expression: IrCall): IrExpression {
        val expression = super.visitCall(expression) as IrCall

        try {
          return when (expression.symbol) {
            ktBridgeApis.publicGetFunction -> {
              KtBridgeGetRewriter(
                pluginContext,
                ktBridgeApis,
                currentScope!!,
                currentDeclarationParent!!,
                expression
              ).rewrite()
            }

            ktBridgeApis.publicSetFunction -> {
              KtBridgeSetRewriter(
                pluginContext,
                ktBridgeApis,
                currentScope!!,
                currentDeclarationParent!!,
                expression
              ).rewrite()
            }

            else -> expression
          }
        } catch (e: KtBridgeCompilationException) {
          messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
          return expression
        }
      }
    }

    moduleFragment.transform(transformer, null)
  }
}

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

import app.cash.quickjs.ktbridge.plugin.BridgedCallRewriter.Companion.CREATE_BRIDGE_TO_JS
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName

@ObsoleteDescriptorBasedAPI // TODO(jwilson): is there an alternative?
class KtBridgeIrGenerationExtension(
  private val messageCollector: MessageCollector,
  private val string: String,
  private val file: String
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    messageCollector.report(CompilerMessageSeverity.INFO, "Argument 'string' = $string")
    messageCollector.report(CompilerMessageSeverity.INFO, "Argument 'file' = $file")

    val createBridgeToJsFunction2Arg = pluginContext.referenceFunctions(CREATE_BRIDGE_TO_JS)
        .single { it.descriptor.valueParameters.size == 2 }

    // Find top-level properties of type `BridgeToJs<T>` that are initialized with a call to
    // the two-argument overload of createBridgeToJs(). Rewrite these.

    for (file in moduleFragment.files) {
      for (declaration in file.declarations) {
        if (declaration !is IrProperty) continue
        val backingField = declaration.backingField ?: continue
        if (backingField.type.classFqName != BridgedCallRewriter.BRIDGE_TO_JS) continue
        val initializer = backingField.initializer ?: continue
        val initializerCall = initializer.expression
        if (initializerCall !is IrCall) continue
        if (initializerCall.symbol != createBridgeToJsFunction2Arg) continue

        BridgedCallRewriter(pluginContext, backingField, initializer, initializerCall).rewrite()
      }
    }
  }
}

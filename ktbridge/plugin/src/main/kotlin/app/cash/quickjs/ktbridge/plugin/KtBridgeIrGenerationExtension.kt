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

import app.cash.quickjs.ktbridge.plugin.InboundCallRewriter.Companion.CREATE_JS_SERVICE
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

    val createJsServiceFunction2Arg = pluginContext.referenceFunctions(CREATE_JS_SERVICE)
        .singleOrNull { it.descriptor.valueParameters.size == 2 }
      ?: return // If this function is absent, there's nothing to do. Perhaps it isn't Kotlin/JS?

    // Find top-level properties of type `BridgeToJs<T>` that are initialized with a call to
    // the two-argument overload of createJsService(). Rewrite these.

    for (file in moduleFragment.files) {
      for (declaration in file.declarations) {
        if (declaration !is IrProperty) continue
        val backingField = declaration.backingField ?: continue
        if (backingField.type.classFqName != InboundCallRewriter.BRIDGE_TO_JS) continue
        val initializer = backingField.initializer ?: continue
        val initializerCall = initializer.expression
        if (initializerCall !is IrCall) continue
        if (initializerCall.symbol != createJsServiceFunction2Arg) continue

        InboundCallRewriter(pluginContext, backingField, initializer, initializerCall).rewrite()
      }
    }
  }
}

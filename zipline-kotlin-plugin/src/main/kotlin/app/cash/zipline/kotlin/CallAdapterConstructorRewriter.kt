/*
 * Copyright (C) 2022 Block, Inc.
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

/**
 * Rewrites calls to `ziplineServiceSerializer(KClass<SampleService>, List<KSerializer<*>>)` to
 * directly call the constructor of the generated adapter class.
 *
 * This call:
 *
 * ```
 * val serializer = ziplineServiceSerializer(SampleService::class, serializers)
 * ```
 *
 * is rewritten to:
 *
 * ```
 * val serializer = ServiceType.Companion.Adapter(
 *   serializers,
 *   serialName("com.example.SampleService", serializers),
 * )
 * ```
 *
 * Note this only works if the `KClass` argument is a constant, as that's how this decides which
 * constructor to invoke.
 */
internal class CallAdapterConstructorRewriter(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
) {
  fun rewrite(): IrExpression {
    val kClassArgument = original.arguments[0] ?: return original
    val serializersListExpression = original.arguments[1] ?: return original

    val bridgedInterfaceType = when (kClassArgument) {
      is IrClassReference -> kClassArgument.classType
      else -> return original
    }

    if (!bridgedInterfaceType.isSubtypeOfClass(ziplineApis.ziplineService)) return original

    val bridgedInterface = BridgedInterface.create(
      pluginContext,
      ziplineApis,
      scope,
      original,
      "ziplineServiceSerializer()",
      bridgedInterfaceType,
    )

    val result = AdapterGenerator(
      pluginContext,
      ziplineApis,
      scope,
      bridgedInterface.typeIrClass,
    ).adapterExpression(
      serializersListExpression = serializersListExpression,
      adapterType = original.typeArguments[0]!!,
    )
    result.patchDeclarationParents(declarationParent)
    return result
  }
}

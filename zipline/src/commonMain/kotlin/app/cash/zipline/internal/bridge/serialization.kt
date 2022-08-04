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
package app.cash.zipline.internal.bridge

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@OptIn(ExperimentalSerializationApi::class)
fun SerializersModule.contextualSerializerForType(type: KType): KSerializer<*> {
  return getContextual(
    type.classifier as KClass<*>? ?: error("no contextual serializer for $type"),
    type.arguments.map { serializer(it.type ?: error("no contextual serializer for $type")) }
  ) ?: error("no contextual serializer for $type")
}

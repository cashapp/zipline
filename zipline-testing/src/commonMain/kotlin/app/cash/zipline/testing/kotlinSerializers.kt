/*
 * Copyright 2017-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package app.cash.zipline.testing

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule

/*
 *
 * This file contains built-in serializers from Kotlin Serialization. It's here to work around a gap
 * in the Kotlin Serialization API - there's no public API like this:
 *
 *   fun serializer(
 *     kClass: KClass<*>,
 *     typeArgumentsSerializers: List<KSerializer<*>>
 *   )
 *
 * The API gets close! There's an API to get a built-in serializer with a fully-specified KType, and
 * there's an API to do this for a contextual serializer.
 *
 * There's something that's almost perfect but it's private, SerializersModule.builtinSerializer().
 *
 * This is used by Zipline when we have a serializer for a type parameter 'T' and need to create a
 * serializer for a `List<T>`.
 *
 * TODO(jwilson): delete this once kotlinx.serialization has a public API for this.
 *
 */

val kotlinBuiltInSerializersModule = SerializersModule {
  contextual(List::class) { serializers ->
    ListSerializer(serializers[0])
  }
}

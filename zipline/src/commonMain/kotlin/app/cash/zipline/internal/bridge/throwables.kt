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
package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal expect fun stacktraceString(throwable: Throwable): String

internal expect fun toInboundThrowable(
  stacktraceString: String,
  constructor: (String) -> Throwable,
): Throwable

/**
 * Serialize a [Throwable] in two parts:
 *
 *  * A list of types in preference-order. We'll only need one type in almost all cases, but if we
 *    want to introduce new throwable subtypes in the future this enables backwards-compatibility.
 *
 *  * The throwable message and stacktrace, all together. This may be prefixed with the name
 *    of the throwable class.
 */
@Serializable
internal class ThrowableSurrogate(
  /**
   * A list of types in preference order to decode this throwable into. If a type is unrecognized
   * it should be skipped, ultimately falling back to [Exception] if no types are recognized.
   */
  val types: List<String>,

  /**
   * The result of [Throwable.stackTraceToString], potentially with code from Zipline's bridges
   * stripped.
   */
  val stacktraceString: String,
)

internal object ThrowableSerializer : KSerializer<Throwable> {
  private val surrogateSerializer = ThrowableSurrogate.serializer()
  override val descriptor = surrogateSerializer.descriptor

  override fun serialize(encoder: Encoder, value: Throwable) {
    val stacktraceString = stacktraceString(value)
    val typeNames = knownTypeNames(value)

    encoder.encodeSerializableValue(
      surrogateSerializer,
      ThrowableSurrogate(
        types = typeNames,
        stacktraceString = stacktraceString,
      ),
    )
  }

  override fun deserialize(decoder: Decoder): Throwable {
    val surrogate = decoder.decodeSerializableValue(surrogateSerializer)

    // Find a throwable type to decode as.
    val typeNameToConstructor: Pair<String, (String) -> Throwable> =
      surrogate.types.firstNotNullOfOrNull { typeName ->
        val constructor = knownTypeConstructor(typeName) ?: return@firstNotNullOfOrNull null
        typeName to constructor
      } ?: ("ZiplineException" to { message -> ZiplineException(message) })
    val (typeName, constructor) = typeNameToConstructor

    // Strip off "ZiplineApiMismatchException: " prefix (or similar) so it isn't repeated.
    var stacktraceString = surrogate.stacktraceString
    if (stacktraceString.startsWith(typeName) &&
      stacktraceString.regionMatches(typeName.length, ": ", 0, 2)
    ) {
      stacktraceString = stacktraceString.substring(typeName.length + 2)
    }

    return toInboundThrowable(stacktraceString, constructor)
  }

  private fun knownTypeNames(throwable: Throwable): List<String> {
    return when (throwable) {
      is ZiplineApiMismatchException -> listOf("ZiplineApiMismatchException")
      else -> listOf()
    }
  }

  private fun knownTypeConstructor(typeName: String): ((String) -> Throwable)? {
    return when (typeName) {
      "ZiplineApiMismatchException" -> ::ZiplineApiMismatchException
      else -> null
    }
  }
}

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
package app.cash.zipline

import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

/**
 * Kotlinx.serialization doesn't cover Zipline's use case, so we need to emulate it for interface
 * parameters and return types. This covers how its annotations are supported by Zipline.
 */
internal class SerializationAnnotationsTest {
  @Test
  fun serializableWithOnParameterType() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : ServiceWithSerializableParameter {
      override fun price(pizza: Pizza): Int {
        return 10 + pizza.toppings.size * 2
      }
    }

    endpointA.bind<ServiceWithSerializableParameter>("pizzaService", service)
    val client = endpointB.take<ServiceWithSerializableParameter>("pizzaService")

    val response = client.price(RealPizza(toppings = listOf("mushrooms", "olives")))
    assertEquals(14, response)
  }

  @Test
  fun contextualOnParameterType() = runBlocking {
    val serializersModule = SerializersModule {
      contextual(Pizza::class, PizzaSerializer)
    }
    val (endpointA, endpointB) = newEndpointPair(this, serializersModule)

    val service = object : ServiceWithContextualParameter {
      override fun price(pizza: Pizza): Int {
        return 10 + pizza.toppings.size * 2
      }
    }

    endpointA.bind<ServiceWithContextualParameter>("pizzaService", service)
    val client = endpointB.take<ServiceWithContextualParameter>("pizzaService")

    val response = client.price(RealPizza(toppings = listOf("mushrooms", "olives")))
    assertEquals(14, response)
  }
}

interface Pizza {
  val toppings: List<String>
}

@Serializable
class RealPizza(
  override val toppings: List<String>
) : Pizza

interface ServiceWithSerializableParameter : ZiplineService {
  fun price(pizza: @Serializable(with = PizzaSerializer::class) Pizza): Int
}

interface ServiceWithContextualParameter : ZiplineService {
  fun price(pizza: @Contextual Pizza): Int
}

object PizzaSerializer : KSerializer<Pizza> {
  override val descriptor = RealPizza.serializer().descriptor

  override fun deserialize(decoder: Decoder): Pizza {
    return decoder.decodeSerializableValue(RealPizza.serializer())
  }

  override fun serialize(encoder: Encoder, value: Pizza) {
    return encoder.encodeSerializableValue(RealPizza.serializer(), RealPizza(value.toppings))
  }
}

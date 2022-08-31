/*
 * Copyright 2017-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package app.cash.zipline.testing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection
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

val contextualListModule = SerializersModule {
  contextual(List::class) { serializers ->
    ArrayListSerializer(serializers[0])
  }
}

private const val ARRAY_LIST_NAME = "kotlin.collections.ArrayList"

private class ArrayListSerializer<E>(element: KSerializer<E>) : CollectionSerializer<E, List<E>, ArrayList<E>>(element) {
  override val descriptor: SerialDescriptor = ArrayListClassDesc(element.descriptor)

  override fun builder(): ArrayList<E> = arrayListOf()
  override fun ArrayList<E>.builderSize(): Int = size
  override fun ArrayList<E>.toResult(): List<E> = this
  override fun List<E>.toBuilder(): ArrayList<E> = this as? ArrayList<E> ?: ArrayList(this)
  override fun ArrayList<E>.checkCapacity(size: Int): Unit = ensureCapacity(size)
  override fun ArrayList<E>.insert(index: Int, element: E) { add(index, element) }
}

private abstract class CollectionSerializer<E, C: Collection<E>, B>(element: KSerializer<E>) : CollectionLikeSerializer<E, C, B>(element) {
  override fun C.collectionSize(): Int = size
  override fun C.collectionIterator(): Iterator<E> = iterator()
}

private sealed class CollectionLikeSerializer<Element, Collection, Builder>(
  private val elementSerializer: KSerializer<Element>
) : AbstractCollectionSerializer<Element, Collection, Builder>() {

  protected abstract fun Builder.insert(index: Int, element: Element)
  abstract override val descriptor: SerialDescriptor

  override fun serialize(encoder: Encoder, value: Collection) {
    val size = value.collectionSize()
    encoder.encodeCollection(descriptor, size) {
      val iterator = value.collectionIterator()
      for (index in 0 until size)
        encodeSerializableElement(descriptor, index, elementSerializer, iterator.next())
    }
  }

  final override fun readAll(decoder: CompositeDecoder, builder: Builder, startIndex: Int, size: Int) {
    require(size >= 0) { "Size must be known in advance when using READ_ALL" }
    for (index in 0 until size)
      readElement(decoder, startIndex + index, builder, checkIndex = false)
  }

  override fun readElement(decoder: CompositeDecoder, index: Int, builder: Builder, checkIndex: Boolean) {
    builder.insert(index, decoder.decodeSerializableElement(descriptor, index, elementSerializer))
  }
}

private abstract class AbstractCollectionSerializer<Element, Collection, Builder> : KSerializer<Collection> {
  protected abstract fun Collection.collectionSize(): Int
  protected abstract fun Collection.collectionIterator(): Iterator<Element>
  protected abstract fun builder(): Builder
  protected abstract fun Builder.builderSize(): Int
  protected abstract fun Builder.toResult(): Collection
  protected abstract fun Collection.toBuilder(): Builder
  protected abstract fun Builder.checkCapacity(size: Int)

  abstract override fun serialize(encoder: Encoder, value: Collection)

  fun merge(decoder: Decoder, previous: Collection?): Collection {
    val builder = previous?.toBuilder() ?: builder()
    val startIndex = builder.builderSize()
    val compositeDecoder = decoder.beginStructure(descriptor)
    if (compositeDecoder.decodeSequentially()) {
      readAll(compositeDecoder, builder, startIndex, readSize(compositeDecoder, builder))
    } else {
      while (true) {
        val index = compositeDecoder.decodeElementIndex(descriptor)
        if (index == CompositeDecoder.DECODE_DONE) break
        readElement(compositeDecoder, startIndex + index, builder)
      }
    }
    compositeDecoder.endStructure(descriptor)
    return builder.toResult()
  }

  override fun deserialize(decoder: Decoder): Collection = merge(decoder, null)

  private fun readSize(decoder: CompositeDecoder, builder: Builder): Int {
    val size = decoder.decodeCollectionSize(descriptor)
    builder.checkCapacity(size)
    return size
  }

  protected abstract fun readElement(decoder: CompositeDecoder, index: Int, builder: Builder, checkIndex: Boolean = true)

  protected abstract fun readAll(decoder: CompositeDecoder, builder: Builder, startIndex: Int, size: Int)
}

private class ArrayListClassDesc(elementDesc: SerialDescriptor) : ListLikeDescriptor(elementDesc) {
  override val serialName: String get() = ARRAY_LIST_NAME
}

private abstract class ListLikeDescriptor(val elementDescriptor: SerialDescriptor) : SerialDescriptor {
  override val kind: SerialKind get() = StructureKind.LIST
  override val elementsCount: Int = 1

  override fun getElementName(index: Int): String = index.toString()
  override fun getElementIndex(name: String): Int =
    name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid list index")

  override fun isElementOptional(index: Int): Boolean {
    require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
    return false
  }

  override fun getElementAnnotations(index: Int): List<Annotation> {
    require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
    return emptyList()
  }

  override fun getElementDescriptor(index: Int): SerialDescriptor {
    require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
    return elementDescriptor
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ListLikeDescriptor) return false
    if (elementDescriptor == other.elementDescriptor && serialName == other.serialName) return true
    return false
  }

  override fun hashCode(): Int {
    return elementDescriptor.hashCode() * 31 + serialName.hashCode()
  }

  override fun toString(): String = "$serialName($elementDescriptor)"
}


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
@file:OptIn(ExperimentalForeignApi::class)

package app.cash.zipline

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.sizeOf

/** Copy the data of [item] to the [index] of [this] as if it were an array of [T] structs. */
internal inline operator fun <reified T : CVariable> CPointer<T>.set(index: Int, item: CValues<T>) {
  val offset = index * sizeOf<T>()
  item.place(interpretCPointer(rawValue + offset)!!)
}

/** Copy the values of [items] into a new array. */
internal inline fun <reified T : CVariable> NativePlacement.allocArrayOf(
  vararg items: CValues<T>,
): CArrayPointer<T> {
  val array = allocArray<T>(items.size)
  items.forEachIndexed { index, item ->
    array[index] = item
  }
  return array
}

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
package app.cash.zipline

/** Introspect QuickJS for its current memory usage. */
@EngineApi
data class MemoryUsage(
  /** Memory allocated. */
  val memoryAllocatedCount: Long,
  val memoryAllocatedSize: Long,
  val memoryAllocatedLimit: Long,

  /** Memory used. */
  val memoryUsedCount: Long,
  val memoryUsedSize: Long,

  /** Atoms. */
  val atomsCount: Long,
  val atomsSize: Long,

  /** Strings. */
  val stringsCount: Long,
  val stringsSize: Long,

  /** Objects. */
  val objectsCount: Long,
  val objectsSize: Long,

  /** Properties. */
  val propertiesCount: Long,
  val propertiesSize: Long,

  /** Shapes. */
  val shapeCount: Long,
  val shapeSize: Long,

  /** Bytecode functions. */
  val jsFunctionsCount: Long,
  val jsFunctionsSize: Long,
  val jsFunctionsCodeSize: Long,
  val jsFunctionsLineNumberTablesCount: Long,
  val jsFunctionsLineNumberTablesSize: Long,

  /** C functions. */
  val cFunctionsCount: Long,

  /** Arrays. */
  val arraysCount: Long,

  /** Fast arrays. */
  val fastArraysCount: Long,
  val fastArraysElementsCount: Long,

  /** Binary objects. */
  val binaryObjectsCount: Long,
  val binaryObjectsSize: Long,
)

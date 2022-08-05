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
package app.cash.zipline.loader

import app.cash.zipline.loader.internal.ByteStringAsHexSerializer
import app.cash.zipline.loader.internal.isTopologicallySorted
import app.cash.zipline.loader.internal.topologicalSort
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Preferred construction is via [ZiplineManifest.create]
 * Constructor is still accessible from .copy() on data class, alternatively can implement without the data class but
 * with manual equals/hashCode to support value equality
 */
@Serializable
data class ZiplineManifest private constructor(
  /** This is an ordered map; its modules are always topologically sorted. */
  val modules: Map<String, Module>,

  /**
   * JS module ID for the application (ie. "./alpha-app.js").
   * This will usually be the last module in the manifest once it is topologically sorted.
   */
  val mainModuleId: String,

  /** Fully qualified main function to start the application (ie. "zipline.ziplineMain"). */
  val mainFunction: String? = null,

  /**
   * A manifest may include many signatures, in order of preference. The keys of the map are the
   * signing key names. The values of the map are hex-encoded signatures.
   */
  val signatures: Map<String, String> = mapOf(),

  /** Version to represent the code as defined in this manifest, by default it will be Git commit SHA. */
  val version: String? = null,

  /**
   * The newest timestamp that this manifest is known to be fresh. Typically, a manifest is fresh at
   * the moment it is downloaded. If this field is null the caller should determine freshness
   * independently.
   */
  val freshAtEpochMs: Long? = null,

  /**
   * Optional URL to resolve module URLs against when downloading. If null, module URLs are relative
   * to the URL that this manifest was loaded from.
   */
  val baseUrl: String? = null,
) {
  init {
    require(modules.keys.toList().isTopologicallySorted { id -> modules[id]!!.dependsOnIds }) {
      "Modules are not topologically sorted and can not be loaded"
    }
  }

  @Serializable
  data class Module(
    /** This may be an absolute URL, or relative to an enclosing manifest. */
    val url: String,
    @Serializable(with = ByteStringAsHexSerializer::class)
    val sha256: ByteString,
    val dependsOnIds: List<String> = listOf(),
  )

  companion object {
    fun create(
      modules: Map<String, Module>,
      mainFunction: String? = null,
      mainModuleId: String? = null,
      version: String? = null,
      builtAtEpochMs: Long? = null,
      baseUrl: String? = null,
    ): ZiplineManifest {
      val sortedModuleIds = modules.keys
        .toList()
        .topologicalSort { id ->
          modules[id]?.dependsOnIds
            ?: throw IllegalArgumentException("Unexpected [id=$id] is not found in modules keys")
        }
      return ZiplineManifest(
        modules = sortedModuleIds.associateWith { id ->
          modules[id]
            ?: throw IllegalArgumentException("Unexpected [id=$id] is not found in modules keys")
        },
        mainModuleId = mainModuleId ?: sortedModuleIds.last(),
        mainFunction = mainFunction,
        signatures = mapOf(),
        version = version,
        freshAtEpochMs = builtAtEpochMs,
        baseUrl = baseUrl,
      )
    }
  }
}

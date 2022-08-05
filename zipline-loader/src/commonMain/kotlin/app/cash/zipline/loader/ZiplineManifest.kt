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
  /** Metadata on this manifest that isn't authenticated by a signature. */
  val unsigned: Unsigned = Unsigned(),

  /** This is an ordered map; its modules are always topologically sorted. */
  val modules: Map<String, Module> = mapOf(),

  /**
   * JS module ID for the application (ie. "./alpha-app.js").
   * This will usually be the last module in the manifest once it is topologically sorted.
   */
  val mainModuleId: String,

  /** Fully qualified main function to start the application (ie. "zipline.ziplineMain"). */
  val mainFunction: String? = null,

  /** Version to represent the code as defined in this manifest, by default it will be Git commit SHA. */
  val version: String? = null,
) {
  init {
    require(modules.keys.toList().isTopologicallySorted { id -> modules[id]!!.dependsOnIds }) {
      "Modules are not topologically sorted and can not be loaded"
    }
  }

  /**
   * Properties of this manifest not authenticated by a signature. We prefer to define properties
   * in the top-level manifest wherever possible.
   */
  @Serializable
  data class Unsigned(
    /**
     * A manifest may include many signatures, in order of preference. The keys of the map are the
     * signing key names. The values of the map are hex-encoded signatures.
     *
     * This is unsigned to solve a chicken-egg problem: we can't sign the output of the signature.
     */
    val signatures: Map<String, String> = mapOf(),

    /**
     * The newest timestamp that this manifest is known to be fresh. Typically, a manifest is fresh
     * at the moment it is downloaded. If this field is null the caller should determine freshness
     * independently.
     *
     * This is unsigned so that embedded manifests may be updated to track the time they were
     * downloaded at.
     */
    val freshAtEpochMs: Long? = null,

    /**
     * Optional URL to resolve module URLs against when downloading. If null, module URLs are
     * relative to the URL that this manifest was loaded from.
     *
     * This is unsigned so that cached manifests may be updated to track the URL that they were
     * originally fetched from.
     */
    val baseUrl: String? = null,
  )

  @Serializable
  data class Module(
    /** This may be an absolute URL, or relative to an enclosing manifest. */
    val url: String,
    @Serializable(with = ByteStringAsHexSerializer::class)
    val sha256: ByteString,
    val dependsOnIds: List<String> = listOf(),
  )

  val signatures: Map<String, String>
    get() = unsigned.signatures
  val freshAtEpochMs: Long?
    get() = unsigned.freshAtEpochMs
  val baseUrl: String?
    get() = unsigned.baseUrl

  fun copy(
    signatures: Map<String, String> = this.signatures,
    freshAtEpochMs: Long? = this.freshAtEpochMs,
    baseUrl: String? = this.baseUrl,
    modules: Map<String, Module> = this.modules,
    mainModuleId: String = this.mainModuleId,
    mainFunction: String? = this.mainFunction,
    version: String? = this.version,
  ) = copy(
    unsigned = unsigned.copy(
      freshAtEpochMs = freshAtEpochMs,
      signatures = signatures,
      baseUrl = baseUrl,
    ),
    modules = modules,
    mainModuleId = mainModuleId,
    mainFunction = mainFunction,
    version = version,
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
        unsigned = Unsigned(
          signatures = mapOf(),
          freshAtEpochMs = builtAtEpochMs,
          baseUrl = baseUrl,
        ),
        modules = sortedModuleIds.associateWith { id ->
          modules[id]
            ?: throw IllegalArgumentException("Unexpected [id=$id] is not found in modules keys")
        },
        mainModuleId = mainModuleId ?: sortedModuleIds.last(),
        mainFunction = mainFunction,
        version = version,
      )
    }
  }
}

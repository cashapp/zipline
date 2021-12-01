package app.cash.zipline.loader

import okio.ByteString.Companion.encodeUtf8

val TEST_MANIFEST_PRODUCTION = ZiplineManifest.create(
  modules = mapOf(
    "kotlinx-serialization-json" to ZiplineModule(
      url = "/kotlinx-serialization-kotlinx-serialization-json-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlinx-serialization-core"),
    ),
    "kotlinx-coroutines" to ZiplineModule(
      url = "/kotlinx.coroutines-kotlinx-coroutines-core-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
    "zipline-root" to ZiplineModule(
      url = "/zipline-root-zipline.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin", "kotlinx-serialization-json", "kotlinx-coroutines"),
    ),
    "zipline-root-testing" to ZiplineModule(
      url = "/zipline-root-testing.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("zipline-root"),
    ),
    "kotlin" to ZiplineModule(
      url = "/kotlin-kotlin-stdlib-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf(),
    ),
    "atomicfu" to ZiplineModule(
      url = "/88b0986a7186d029-atomicfu-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
    "kotlinx-serialization-core" to ZiplineModule(
      url = "/kotlinx-serialization-kotlinx-serialization-core-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
  )
)

package app.cash.zipline.loader

import okio.ByteString.Companion.encodeUtf8

val TEST_MANIFEST_PRODUCTION = ZiplineManifest(
  files = listOf(
    ZiplineModule(
      id = "kotlinx-serialization-json",
      filePath = "/kotlinx-serialization-kotlinx-serialization-json-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlinx-serialization-core"),
    ),
    ZiplineModule(
      id = "kotlinx-coroutines",
      filePath = "/kotlinx.coroutines-kotlinx-coroutines-core-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
    ZiplineModule(
      id = "zipline-root",
      filePath = "/zipline-root-zipline.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin", "kotlinx-serialization-json", "kotlinx-coroutines"),
    ),
    ZiplineModule(
      id = "zipline-root-testing",
      filePath = "/zipline-root-testing.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("zipline-root"),
    ),
    ZiplineModule(
      id = "kotlin",
      filePath = "/kotlin-kotlin-stdlib-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf(),
    ),
    ZiplineModule(
      id = "atomicfu",
      filePath = "/88b0986a7186d029-atomicfu-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
    ZiplineModule(
      id = "kotlinx-serialization-core",
      filePath = "/kotlinx-serialization-kotlinx-serialization-core-js-ir.zipline",
      sha256 = "abc123".encodeUtf8(),
      dependsOnIds = listOf("kotlin"),
    ),
  ).shuffled()
)

package app.cash.zipline.loader

import app.cash.zipline.Zipline
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test

@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val CDN_DOMAIN = "https://cdn.com/path/to/zipline/directory"
  private val testManifest = ZiplineManifest(
    files = listOf(
      ZiplineModule(
        id = "kotlinx-serialization-json",
        url = "$CDN_DOMAIN/kotlinx-serialization-kotlinx-serialization-json-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlinx-serialization-core"),
      ),
      ZiplineModule(
        id = "kotlinx-coroutines",
        url = "$CDN_DOMAIN/kotlinx.coroutines-kotlinx-coroutines-core-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlin"),
      ),
      ZiplineModule(
        id = "zipline-root",
        url = "$CDN_DOMAIN/zipline-root-zipline.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlin", "kotlinx-serialization-json", "kotlinx-coroutines"),
      ),
      ZiplineModule(
        id = "zipline-root-testing",
        url = "$CDN_DOMAIN/zipline-root-testing.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("zipline-root"),
      ),
      ZiplineModule(
        id = "kotlin",
        url = "$CDN_DOMAIN/kotlin-kotlin-stdlib-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf(),
      ),
      ZiplineModule(
        id = "atomicfu",
        url = "$CDN_DOMAIN/88b0986a7186d029-atomicfu-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlin"),
      ),
      ZiplineModule(
        id = "kotlinx-serialization-core",
        url = "$CDN_DOMAIN/kotlinx-serialization-kotlinx-serialization-core-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlin"),
      ),
    ).shuffled()
  )

  private val httpClient: ZiplineHttpClient = JvmZiplineHttpClient()
  private val loader = ZiplineLoader(
    client = httpClient
  )
  private val dispatcher = TestCoroutineDispatcher()

  @Test
  fun `happy path`() {
    val zipline = Zipline.create(dispatcher)
    dispatcher.runBlockingTest {
      coroutineScope {
        loader.load(this@coroutineScope, zipline, testManifest)
      }
    }
  }

  @Test
  fun `circular dependency fails`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      ZiplineModule(
        id = "kotlinx-serialization-json",
        url = "$CDN_DOMAIN/kotlinx-serialization-kotlinx-serialization-json-js-ir.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlinx-serialization-json"),
      )
    }
    assertEquals("Invalid circular dependency on self for [id=kotlinx-serialization-json]", exception.message)
  }
}

package app.cash.zipline.loader

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import com.google.common.hash.Hashing
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val httpClient = FakeZiplineHttpClient()
  private val loader = ZiplineLoader(
    client = httpClient
  )
  private val dispatcher = TestCoroutineDispatcher()
  private lateinit var quickJs: QuickJs

  @Before
  fun setUp() {
    quickJs = QuickJs.create()
  }

  @After
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun `happy path`() {
    val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
    """.trimMargin()
    val alphaBytecode = quickJs.compile(alphaJs, "alpha.js")
    val alphaFilePath = "/alpha.zipline"
    val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
    """.trimMargin()
    val bravoBytecode = quickJs.compile(bravoJs, "bravo.js")
    val bravoFilePath = "/bravo.zipline"

    val manifest = ZiplineManifest(
      files = listOf(
        ZiplineModule(
          id = "bravo",
          filePath = bravoFilePath,
          sha256 = bravoBytecode.asSha256(),
          dependsOnIds = listOf("alpha"),
        ),
        ZiplineModule(
          id = "alpha",
          filePath = alphaFilePath,
          sha256 = alphaBytecode.asSha256(),
          dependsOnIds = listOf(),
        ),
      )
    )
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode.toByteString(),
      bravoFilePath to bravoBytecode.toByteString()
    )
    val zipline = Zipline.create(dispatcher)
    dispatcher.runBlockingTest {
      coroutineScope {
        loader.load(this@coroutineScope, zipline, manifest)
      }
    }
    assertThat(zipline.quickJs.evaluate("globalThis.log", "assert.js")).isEqualTo(
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  private fun ByteArray.asSha256() =
    ByteBuffer.wrap(Hashing.sha256().hashBytes(this).asBytes()).toByteString()

  @Test
  fun `circular dependency fails`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      ZiplineModule(
        id = "alpha",
        filePath = "/alpha.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("alpha"),
      )
    }
    assertEquals("Invalid circular dependency on self for [id=alpha]", exception.message)
  }

  @Test
  fun `non-relative filePath fails`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      ZiplineModule(
        id = "alpha",
        filePath = "http://cash-cdn.app/alpha.zipline",
        sha256 = "abc123".encodeUtf8(),
        dependsOnIds = listOf("kotlin"),
      )
    }
    assertEquals(
      "[filePath=http://cash-cdn.app/alpha.zipline] should be a relative path to the base configured in ZiplineHttpClient, not an absolute URL",
      exception.message
    )
  }
}

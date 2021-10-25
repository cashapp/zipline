package app.cash.zipline

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import org.junit.Test

class ZiplineFileTest {
  private val bytecode = "sample bytecode".encodeUtf8()

  @Test
  fun encodeAndDecode() {
    val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode)
    val buffer = Buffer()
    ZiplineFileWriter(ziplineFile).write(buffer)
    val decodedZiplineFile = ZiplineFileReader().read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeGoldenFile() {
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val decodedZiplineFile = ZiplineFileReader().read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeWithUnknownSection() {
    // Append an extra section to our golden file and confirm that it's ignored. We want this so
    // if later we want to add new sections, we know old readers will silently ignore data they
    // don't understand. (We'll bump the zipline version when we need to break old clients.)
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    buffer.writeInt(9999) // Section 9999 is unlikely
    buffer.writeInt(5) // Section 9999 length
    buffer.writeUtf8("hello")
    val decodedZiplineFile = ZiplineFileReader().read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeUnknownVersionCrashes() {
    // We manually changed the version to an unknown one.
    val goldenFile =
      "5a49504c494e45000134654e000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val e = assertFailsWith<IOException> {
      ZiplineFileReader().read(buffer)
    }
    assertEquals("unsupported version: 20211022", e.message)
  }

  @Test
  fun decodeSectionTruncatedCrashes() {
    // We manually changed the version to an unknown one.
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f64".decodeHex()
    val buffer = Buffer().write(goldenFile)
    assertFailsWith<IOException> {
      ZiplineFileReader().read(buffer)
    }
  }

  @Test
  fun decodeNonZiplineFileCrashes() {
    val buffer = Buffer().writeUtf8("function hello() { };")
    val e = assertFailsWith<IOException> {
      ZiplineFileReader().read(buffer)
    }
    assertEquals("not a zipline file", e.message)
  }

  @Test
  fun decodeBytecodeSectionMissing() {
    val goldenFile = "5a49504c494e45000134654c".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val e = assertFailsWith<IOException> {
      ZiplineFileReader().read(buffer)
    }
    assertEquals("QuickJS bytecode section missing", e.message)
  }
}

package app.cash.zipline.loader

import app.cash.zipline.QuickJs
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
      |""".trimMargin()

fun alphaBytecode(quickJs: QuickJs) = ziplineFile(quickJs, alphaJs, "alpha.js")
const val alphaFilePath = "/alpha.zipline"

val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
      |""".trimMargin()

fun bravoBytecode(quickJs: QuickJs) = ziplineFile(quickJs, bravoJs, "bravo.js")
const val bravoFilePath = "/bravo.zipline"

const val manifestPath = "/manifest.zipline.json"
fun manifest(quickJs: QuickJs) = ZiplineManifest.create(
  modules = mapOf(
    "bravo" to ZiplineModule(
      url = bravoFilePath,
      sha256 = bravoBytecode(quickJs).sha256(),
      dependsOnIds = listOf("alpha"),
    ),
    "alpha" to ZiplineModule(
      url = alphaFilePath,
      sha256 = alphaBytecode(quickJs).sha256(),
      dependsOnIds = listOf(),
    ),
  )
)

fun ziplineFile(quickJs: QuickJs, javaScript: String, fileName: String): ByteString {
  val ziplineFile = ZiplineFile(
    CURRENT_ZIPLINE_VERSION,
    quickJs.compile(javaScript, fileName).toByteString()
  )

  val buffer = Buffer()
  ziplineFile.writeTo(buffer)
  return buffer.readByteString()
}

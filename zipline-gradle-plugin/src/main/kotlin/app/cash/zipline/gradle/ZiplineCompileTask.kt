package app.cash.zipline.gradle

import app.cash.zipline.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.QuickJs
import app.cash.zipline.ZiplineFile
import app.cash.zipline.ZiplineFileWriter
import app.cash.zipline.bytecode.applySourceMapToBytecode
import java.io.File
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory

open class ZiplineCompileTask : DefaultTask() {
  @InputDirectory
  var inputDir: File? = null

  @OutputDirectory
  var outputDir: File? = null

  init {
    super.doLast {
      if (inputDir == null) {
        logger.info("inputDirectory file null")
        return@doLast
      }

      if (outputDir == null) {
        logger.info("outputDirectory file null")
        return@doLast
      }

      if (inputDir != null && outputDir != null) {
        val files = inputDir!!.listFiles()
        files!!.forEach { jsFile ->
          if (jsFile.path.endsWith(".js")) {
            val jsSourceMapFile = files.singleOrNull { smp -> smp.path == "${jsFile.path}.map" }
            // TODO name the zipline as the SHA of the source code, only compile a new file when the SHA changes
            compileFile(
              inputJs = jsFile,
              inputJsSourceMap = jsSourceMapFile,
              outputZipline = File(outputDir!!.path, jsFile.nameWithoutExtension + ".zipline")
            )
          }
        }
      }
    }
  }

  private fun compileFile(
    inputJs: File,
    inputJsSourceMap: File?,
    outputZipline: File
  ) {
    val quickJs = QuickJs.create()
    var bytecode = quickJs.compile(inputJs.readText(), inputJs.name)
    quickJs.close()
    if (inputJsSourceMap != null) {
      // rewrite the bytecode with source line numbers
      bytecode = applySourceMapToBytecode(bytecode, inputJsSourceMap.readText())
    }
    val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())

    // Use executes block then closes the sink.
    outputZipline.sink().buffer().use {
      ZiplineFileWriter(ziplineFile).write(it)
    }
  }
}

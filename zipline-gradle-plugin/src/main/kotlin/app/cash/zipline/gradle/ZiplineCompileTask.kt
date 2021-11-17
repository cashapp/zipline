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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile


open class ZiplineCompileTask : DefaultTask() {
  @InputFile
  var inputJs: File? = null

  @InputFile
  @Optional
  var inputSourceMap: File? = null

  @OutputFile
  var outputZipline: File? = null

  init {
    super.doLast {
      if (inputJs == null) {
        logger.info("inputJs file null")
        return@doLast
      }

      if (outputZipline == null) {
        logger.info("outputZipline file null")
        return@doLast
      }

      val inputJavaScript = inputJs!!.readText()
      val quickJs = QuickJs.create()
      var bytecode = quickJs.compile(inputJavaScript, inputJs!!.name)
      inputSourceMap?.let {
        // rewrite the bytecode with source line numbers
        bytecode = applySourceMapToBytecode(bytecode, it.readText())
      }
      val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())

      // Use executes block then closes the sink.
      outputZipline!!.sink().buffer().use {
        ZiplineFileWriter(ziplineFile).write(it)
      }
    }
  }
}

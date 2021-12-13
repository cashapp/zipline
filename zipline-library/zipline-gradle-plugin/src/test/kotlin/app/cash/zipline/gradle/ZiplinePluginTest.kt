package app.cash.zipline.gradle

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class ZiplinePluginTest {
  @Test
  fun `plugin configures kotlin js compile to be modular`() {
    runGradleCompileZiplineTask(File("src/test/projects/modularJs"))
    val inputDir = File("src/test/projects/modularJs/build/compileSync/main/developmentLibrary/kotlin")
    val outputDir = File("src/test/projects/modularJs/build/zipline")
    val outputDirFiles = outputDir.listFiles()
    val inputDirFileNames = inputDir.listFiles()!!.map { it.nameWithoutExtension }
    val outputDirFileNames = outputDirFiles!!.map { it.nameWithoutExtension }
    assertThat(outputDirFileNames).containsAtLeastElementsIn(inputDirFileNames)
  }
}

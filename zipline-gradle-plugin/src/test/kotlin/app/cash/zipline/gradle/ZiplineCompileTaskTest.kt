package app.cash.zipline.gradle

import app.cash.zipline.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.QuickJs
import app.cash.zipline.ZiplineFileReader
import java.io.File
import okio.buffer
import okio.source
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ZiplineCompileTaskTest {
  @Test
  fun `write to and read from a zipline file`() {
    val rootProject = File("src/test/projects/happyPath")

    val gradleRunner = GradleRunner.create()
      .withPluginClasspath()
      .withArguments("--info", "--stacktrace", "compileHello")
      .withProjectDir(rootProject)

    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.task(":compileHello")!!.outcome)

    val ziplineFile = File("$rootProject/build/zipline/hello.zipline")
    val readZiplineFile = ziplineFile.source().buffer().use { source ->
      ZiplineFileReader().read(source)
    }
    assertEquals(CURRENT_ZIPLINE_VERSION, readZiplineFile.ziplineVersion)

    val quickJs = QuickJs.create()
    quickJs.execute(readZiplineFile.quickjsBytecode.toByteArray())
    assertEquals("Hello, guy!", quickJs.evaluate("greet('guy')", "test.js"))
  }
}

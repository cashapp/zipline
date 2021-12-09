/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.zipline.gradle

import app.cash.zipline.QuickJs
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ZiplineFile
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
import okio.buffer
import okio.source
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ZiplineCompileTaskTest {
  private var quickJs: QuickJs? = null

  @After
  fun tearDown() {
    quickJs?.close()
  }

  @Test
  fun `write to and read from zipline`() {
    val rootProject = File("src/test/projects/happyPath")
    assertZiplineCompileTask(rootProject) {
      val readZiplineFile = it.source().buffer().use { source ->
        ZiplineFile.read(source)
      }
      assertEquals(CURRENT_ZIPLINE_VERSION, readZiplineFile.ziplineVersion)
      quickJs = QuickJs.create()
      quickJs!!.execute(readZiplineFile.quickjsBytecode.toByteArray())
      val exception = assertFailsWith<Exception> {
        quickJs!!.evaluate("demo.sayHello()", "test.js")
      }
      // .kt files in the stacktrace means that the sourcemap was applied correctly.
      assertThat(exception.stackTraceToString()).startsWith(
        """
          |app.cash.zipline.QuickJsException: boom!
          |	at JavaScript.goBoom1(throwException.kt)
          |	at JavaScript.goBoom2(throwException.kt:9)
          |	at JavaScript.goBoom3(throwException.kt:6)
          |	at JavaScript.sayHello(throwException.kt:3)
          |	at JavaScript.<eval>(test.js)
          |""".trimMargin()
      )
    }
  }

  private fun runGradleCompileZiplineTask(
    rootProject: File
  ) {
    val taskName = "compileZipline"
    val gradleRunner = GradleRunner.create()
      .withPluginClasspath()
      .withArguments("--info", "--stacktrace", taskName)
      .withProjectDir(rootProject)

    val result = gradleRunner.build()
    assertThat(listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
      .contains(result.task(":$taskName")!!.outcome)
  }

  private fun assertZiplineCompileTask(rootProject: File, nonManifestFileAssertions: (ziplineFile: File) -> Unit) {
    val jsDir = File("$rootProject/jsBuild")
    val ziplineDir = File("$rootProject/build/zipline")

    runGradleCompileZiplineTask(rootProject)

    val expectedNumberFiles = jsDir.listFiles()!!.size / 2
    // Don't include Zipline manifest
    val actualNumberFiles = (ziplineDir.listFiles()?.size ?: 0) - 1
    assertEquals(expectedNumberFiles, actualNumberFiles)

    ziplineDir.listFiles()!!.forEach { ziplineFile ->
      if (!ziplineFile.path.endsWith(".zipline.json")) nonManifestFileAssertions(ziplineFile)
    }
  }
}

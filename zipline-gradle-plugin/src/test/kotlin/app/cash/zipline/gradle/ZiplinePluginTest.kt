/*
 * Copyright (C) 2022 Block, Inc.
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
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Test

class ZiplinePluginTest {
  private var quickJs: QuickJs? = null

  @After
  fun tearDown() {
    quickJs?.close()
  }

  @Test
  fun `zipline compile task automatically added`() {
    val projectDir = File("src/test/projects/basic")

    val taskName = "productionExecutableZipline"
    val gradleRunner = createRunner(projectDir, taskName)

    val result = gradleRunner.build()
    assertThat(listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
      .contains(result.task(":lib:$taskName")!!.outcome)

    val ziplineOut = File(projectDir, "lib/build/compileSync/main/productionExecutable/zipline")
    assertThat(File(ziplineOut, "manifest.zipline.json").exists()).isTrue()
    assertThat(File(ziplineOut, "basic-lib.zipline").exists()).isTrue()
  }

  private fun createRunner(projectDir: File, taskName: String): GradleRunner {
    val gradleRoot = File(projectDir, "gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(File(gradleRoot, "wrapper"), true)
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments("--info", "--stacktrace", "--continue", taskName, versionProperty)
      .forwardOutput()
  }

  private val versionProperty = "-PziplineVersion=${System.getProperty("ziplineVersion")}"
}

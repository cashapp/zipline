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

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class ZiplinePluginTest {
  @Test
  fun `production builds`() {
    val projectDir = File("src/test/projects/basic")

    val taskName = "compileProductionMainZipline"
    val gradleRunner = createRunner(projectDir, taskName)

    val result = gradleRunner.build()
    assertThat(listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
      .contains(result.task(":lib:$taskName")!!.outcome)

    val ziplineOut = File(projectDir, "lib/build/compileSync/main/productionExecutable/zipline")
    assertThat(File(ziplineOut, "manifest.zipline.json").exists()).isTrue()
    assertThat(File(ziplineOut, "basic-lib.zipline").exists()).isTrue()

    val webpackConfig = File(projectDir, "lib/webpack.config.d/generated-zipline-webpack-config.js")
    assertThat(webpackConfig.readText()).contains(
      "config.devServer.static.push(" +
        "'../../../../lib/build/compileSync/main/productionExecutable/zipline');"
    )
  }

  /**
   * This is similar to the test above, confirming that our task tracks the JS compilation mode
   * and the corresponding directories.
   */
  @Test
  fun `development builds`() {
    val projectDir = File("src/test/projects/basic")

    val taskName = "compileDevelopmentMainZipline"
    val gradleRunner = createRunner(projectDir, taskName)

    val result = gradleRunner.build()
    assertThat(listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
      .contains(result.task(":lib:$taskName")!!.outcome)

    val ziplineOut = File(projectDir, "lib/build/compileSync/main/developmentExecutable/zipline")
    assertThat(File(ziplineOut, "manifest.zipline.json").exists()).isTrue()
    assertThat(File(ziplineOut, "basic-lib.zipline").exists()).isTrue()

    val webpackConfig = File(projectDir, "lib/webpack.config.d/generated-zipline-webpack-config.js")
    assertThat(webpackConfig.readText()).contains(
      "config.devServer.static.push(" +
        "'../../../../lib/build/compileSync/main/developmentExecutable/zipline');"
    )
  }

  @Test
  fun `webpack config file is cleaned`() {
    val projectDir = File("src/test/projects/basic")
    val webpackConfig = File(projectDir, "lib/webpack.config.d/generated-zipline-webpack-config.js")
    webpackConfig.writeText("Hello, I'm about to be deleted")

    val gradleRunner = createRunner(projectDir, ":lib:clean")
    val result = gradleRunner.build()
    assertThat(listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
      .contains(result.task(":lib:clean")!!.outcome)

    assertThat(webpackConfig.exists()).isFalse()
  }

  private fun createRunner(projectDir: File, taskName: String): GradleRunner {
    val gradleRoot = File(projectDir, "gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(File(gradleRoot, "wrapper"), true)
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments("--info", "--stacktrace", "--continue", "clean", taskName, versionProperty)
      .forwardOutput()
  }

  private val versionProperty = "-PziplineVersion=${System.getProperty("ziplineVersion")}"
}

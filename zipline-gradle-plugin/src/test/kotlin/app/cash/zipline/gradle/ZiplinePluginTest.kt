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

    val taskName = ":lib:compileProductionExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/main/productionExecutable/kotlinZipline"
    )
    assertThat(ziplineOut.resolve("manifest.zipline.json").exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()

    val webpackConfig = File(projectDir, "lib/webpack.config.d/generated-zipline-webpack-config.js")
    assertThat(webpackConfig.readText()).contains(
      "config.devServer.static.push(" +
        "'../../../../lib/build/compileSync/main/productionExecutable/kotlinZipline');"
    )
  }

  /**
   * This is similar to the test above, confirming that our task tracks the JS compilation mode
   * and the corresponding directories.
   */
  @Test
  fun `development builds`() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/main/developmentExecutable/kotlinZipline"
    )
    assertThat(ziplineOut.resolve("manifest.zipline.json").exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()

    val webpackConfig = File(projectDir, "lib/webpack.config.d/generated-zipline-webpack-config.js")
    assertThat(webpackConfig.readText()).contains(
      "config.devServer.static.push(" +
        "'../../../../lib/build/compileSync/main/developmentExecutable/kotlinZipline');"
    )
  }

  @Test
  fun `webpack config file is cleaned`() {
    val projectDir = File("src/test/projects/basic")
    val webpackConfig = projectDir.resolve(
      "lib/webpack.config.d/generated-zipline-webpack-config.js"
    )
    webpackConfig.writeText("Hello, I'm about to be deleted")

    val result = createRunner(projectDir, "clean").build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(":lib:clean")!!.outcome)

    assertThat(webpackConfig.exists()).isFalse()
  }

  /**
   * This confirms these plugin features are working:
   *
   *  - IR rewriting in JS and JVM
   *  - Compiling to .zipline files and producing a manifest
   */
  @Test
  fun `end to end`() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:launchGreetService"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output).contains("end-to-end call result: 'Hello, Jesse'")
  }

  @Test
  fun `jvm only project`() {
    val projectDir = File("src/test/projects/jvmOnly")

    val taskName = ":lib:bindAndTakeJvm"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output).contains("Zipline Kotlin plugin did its job properly")
  }

  /**
   * Although Kotlin/JS looks like it supports multiple JS targets in a single project, the link
   * task doesn't include the target name in the output directory path. Linked targets are instead
   * disambiguated by their `.js` file name only.
   *
   * This test confirms that linking a single target works, though the whole thing is pretty
   * fragile.
   */
  @Test
  fun `multiple js targets`() {
    val projectDir = File("src/test/projects/multipleJsTargets")

    val taskName = ":lib:compileDevelopmentExecutableKotlinBlueZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/main/developmentExecutable/kotlinZipline"
    )
    assertThat(ziplineOut.resolve("manifest.zipline.json").exists()).isTrue()
    assertThat(ziplineOut.resolve("multipleJsTargets-lib-blue.zipline").exists()).isTrue()
  }

  private fun createRunner(projectDir: File, vararg taskNames: String): GradleRunner {
    val gradleRoot = projectDir.resolve("gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(gradleRoot.resolve("wrapper"), true)
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments("--info", "--stacktrace", "--continue", *taskNames, versionProperty)
      .forwardOutput()
  }

  companion object {
    val SUCCESS_OUTCOMES = listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PziplineVersion=${System.getProperty("ziplineVersion")}"
  }
}

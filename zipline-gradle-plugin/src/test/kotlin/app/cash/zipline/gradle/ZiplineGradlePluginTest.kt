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

import assertk.Assert
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class ZiplineGradlePluginTest {
  @Test
  fun productionBuilds() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileProductionExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve("lib/build/zipline/Production")
    assertThat(ziplineOut.resolve(manifestFileName).exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()
  }

  /**
   * This is similar to the test above, confirming that our task tracks the JS compilation mode
   * and the corresponding directories.
   */
  @Test
  fun developmentBuilds() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve("lib/build/zipline/Development")
    assertThat(ziplineOut.resolve(manifestFileName).exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()
  }

  /**
   * This confirms these plugin features are working:
   *
   *  - IR rewriting in JS and JVM
   *  - Compiling to .zipline files and producing a manifest
   */
  @Test
  fun endToEnd() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:launchGreetService"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output).contains("end-to-end call result: 'Hello, Jesse'")
  }

  /**
   * Stack traces in this mode have no line numbers and no function names. Class names like
   * 'Exception' are mangled into meaningless names like 'Ab'.
   */
  @Test
  fun stacktraceWithOptimizeForSmallArtifactSize() {
    val projectDir = File("src/test/projects/crash")

    val optimizeModeProperty = "-PoptimizeMode=optimizeForSmallArtifactSize"
    val taskName = ":lib:launchCrashService"
    val result = createRunner(projectDir, "clean", taskName, optimizeModeProperty).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output.lines()).containsMatchForEachInOrder(
      Regex("""app.cash.zipline.ZiplineException: \w+: boom!"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/CrashService.kt\)"""),
      Regex("""at [^ ]+ \(lib.js\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/internal/bridge/InboundService.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/internal/bridge/Endpoint.kt\)"""),
      Regex("""at app.cash.zipline.tests.CrashService[${'$'}]Companion[${'$'}]Adapter[${'$'}]GeneratedOutboundService.crash\(CrashService.kt:\d+\)"""),
    )
  }

  /**
   * Stack traces in this mode have line numbers and function names. Symbols and line numbers are
   * retained across Webpack minification. (Some JavaScript stack trace elements don't have line
   * numbers).
   */
  @Test
  fun stacktraceWithOptimizeForDeveloperExperience() {
    val projectDir = File("src/test/projects/crash")

    val optimizeModeProperty = "-PoptimizeMode=optimizeForDeveloperExperience"
    val taskName = ":lib:launchCrashService"
    val result = createRunner(projectDir, "clean", taskName, optimizeModeProperty).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output.lines()).containsMatchForEachInOrder(
      Regex("""app.cash.zipline.ZiplineException: Exception: boom!"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt:\d+\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/tests/CrashService.kt\)"""),
      Regex("""at [^ ]+ \(lib.js\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/internal/bridge/InboundService.kt:\d+\)"""),
      Regex("""at [^ ]+ \(app/cash/zipline/internal/bridge/Endpoint.kt:\d+\)"""),
      Regex("""at app.cash.zipline.tests.CrashService[${'$'}]Companion[${'$'}]Adapter[${'$'}]GeneratedOutboundService.crash\(CrashService.kt:\d+\)"""),
    )
  }

  /**
   * Stack traces in this mode have line numbers and function names. These are built without any
   * Webpack minification. (Some JavaScript stack trace elements don't have line numbers).
   */
  @Test
  fun stacktraceWithDevelopmentBuild() {
    val projectDir = File("src/test/projects/crash")

    val optimizeModeProperty = "-PoptimizeMode=development"
    val taskName = ":lib:launchCrashService"
    val result = createRunner(projectDir, "clean", taskName, optimizeModeProperty).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output.lines()).containsMatchForEachInOrder(
      Regex("""app.cash.zipline.ZiplineException: Exception: boom!"""),
      Regex("""at goBoom_[^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at crash_[^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt:\d+\)"""),
      Regex("""at crash_[^ ]+ \(app/cash/zipline/tests/launchCrashServiceJs.kt\)"""),
      Regex("""at call_[^ ]+ \(app/cash/zipline/tests/CrashService.kt\)"""),
      Regex("""at call_[^ ]+ \(app/cash/zipline/internal/bridge/InboundService.kt:\d+\)"""),
      Regex("""at call_[^ ]+ \(app/cash/zipline/internal/bridge/Endpoint.kt:\d+\)"""),
      Regex("""at app.cash.zipline.tests.CrashService[${'$'}]Companion[${'$'}]Adapter[${'$'}]GeneratedOutboundService.crash\(CrashService.kt:\d+\)"""),
    )
  }

  @Test
  fun jvmOnlyProject() {
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
  fun multipleJsTargets() {
    val projectDir = File("src/test/projects/multipleJsTargets")

    val taskName = ":lib:compileDevelopmentExecutableKotlinBlueZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve("lib/build/zipline/BlueDevelopment")
    val manifest = ziplineOut.resolve(manifestFileName)
    assertThat(manifest.exists()).isTrue()
    assertThat(manifest.readText())
      .containsMatch(Regex(""""version":"1.2.3""""))
    assertThat(ziplineOut.resolve("multipleJsTargets-lib-blue.zipline").exists()).isTrue()
  }

  @Test
  fun versionAndMetadata() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve("lib/build/zipline/Development")
    val manifest = ziplineOut.resolve(manifestFileName)
    val manifestText = manifest.readText()
    assertThat(manifestText)
      .contains(""""version":"1.2.3"""")
    assertThat(manifestText)
      .contains(""""metadata":{"build_timestamp":"2023-10-25T12:00:00T"}""")
  }

  @Test
  fun manifestSigning() {
    val projectDir = File("src/test/projects/signing")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve("lib/build/zipline/Development")
    val manifest = ziplineOut.resolve(manifestFileName)
    assertThat(manifest.readText())
      .containsMatch(Regex(""""signatures":\{"key1":"\w{128}","key2":"\w{128}"}"""))
  }

  @Test
  fun generateZiplineManifestKeyPairEd25519() {
    val projectDir = File("src/test/projects/basic")

    val result = createRunner(projectDir, "generateZiplineManifestKeyPairEd25519").build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(":lib:generateZiplineManifestKeyPairEd25519")!!.outcome)
    assertThat(result.output).containsMatch(
      Regex(
        """
        |      ALGORITHM: Ed25519
        |     PUBLIC KEY: [\da-f]{64}
        |    PRIVATE KEY: [\da-f]{64}
        |
        """.trimMargin(),
      ),
    )
  }

  @Test
  fun generateZiplineManifestKeyPairEcdsaP256() {
    val projectDir = File("src/test/projects/basic")

    val result = createRunner(projectDir, "generateZiplineManifestKeyPairEcdsaP256").build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(":lib:generateZiplineManifestKeyPairEcdsaP256")!!.outcome)
    // Expected lengths were determined experimentally!
    assertThat(result.output).containsMatch(
      Regex(
        """
        |      ALGORITHM: EcdsaP256
        |     PUBLIC KEY: [\da-f]{130}
        |    PRIVATE KEY: [\da-f]{134}
        |
        """.trimMargin(),
      ),
    )
  }

  @Test
  fun ziplineApiDumpDoesNothingOnApiMatch() {
    ziplineApiTaskDoesNothingOnApiMatch(":lib:ziplineApiDump")
  }

  @Test
  fun ziplineApiCheckDoesNothingOnApiMatch() {
    ziplineApiTaskDoesNothingOnApiMatch(":lib:ziplineApiCheck")
  }

  private fun ziplineApiTaskDoesNothingOnApiMatch(taskName: String) {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    val ziplineApiTomlContent = """
      |# This comment will be clobbered if this file is overwritten
      |# by the Gradle task.
      |
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |
      |  # This comment will also be clobbered on an unexpected update.
      |  "ipvircui",
      |]
      """.trimMargin()
    ziplineApiToml.writeText(ziplineApiTomlContent)

    try {
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.readText())
        .isEqualTo(ziplineApiTomlContent)
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnDroppedApi() {
    ziplineApiTaskFailsOnDroppedApi(":lib:ziplineApiCheck")
  }

  @Test
  fun ziplineApiDumpFailsOnDroppedApi() {
    ziplineApiTaskFailsOnDroppedApi(":lib:ziplineApiDump")
  }

  @Test
  fun checkTaskIncludesZiplineApiCheck() {
    ziplineApiTaskFailsOnDroppedApi(":lib:check")
  }

  private fun ziplineApiTaskFailsOnDroppedApi(taskName: String) {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that contains a function not offered.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |
      |  # fun greet(kotlin.String): kotlin.String
      |  "ipvircui",
      |
      |  # fun hello(kotlin.String): kotlin.String
      |  "Cw62Cti7",
      |]
      |
      """.trimMargin(),
    )

    try {
      val result = createRunner(projectDir, "clean", taskName).buildAndFail()
      assertThat(result.output).contains(
        """
        |  Expected function Cw62Cti7 of app.cash.zipline.tests.GreetService not found:
        |    fun hello(kotlin.String): kotlin.String
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiDumpCreatesNewTomlFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.readText()).isEqualTo(
        """
        |[app.cash.zipline.tests.GreetService]
        |
        |functions = [
        |  # fun close(): kotlin.Unit
        |  "moYx+T3e",
        |
        |  # fun greet(kotlin.String): kotlin.String
        |  "ipvircui",
        |]
        |
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnMissingTomlFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    val taskName = ":lib:ziplineApiCheck"
    val result = createRunner(projectDir, "clean", taskName).buildAndFail()
    assertThat(result.output).contains(
      """
      |Zipline API file is incomplete. Run :ziplineApiDump to update it.
      |  api/zipline-api.toml
      """.trimMargin(),
    )
  }

  @Test
  fun ziplineApiDumpUpdatesIncompleteFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that doesn't declare 'greet'.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |]
      |
      """.trimMargin(),
    )

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()

      // The task updates the file to include the 'greet' function.
      assertThat(ziplineApiToml.readText()).isEqualTo(
        """
        |[app.cash.zipline.tests.GreetService]
        |
        |functions = [
        |  # fun close(): kotlin.Unit
        |  "moYx+T3e",
        |
        |  # fun greet(kotlin.String): kotlin.String
        |  "ipvircui",
        |]
        |
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnIncompleteFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that doesn't declare 'greet'.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |]
      |
      """.trimMargin(),
    )

    try {
      val taskName = ":lib:ziplineApiCheck"
      val result = createRunner(projectDir, "clean", taskName).buildAndFail()
      assertThat(result.output).contains(
        """
        |Zipline API file is incomplete. Run :ziplineApiDump to update it.
        |  api/zipline-api.toml
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  /** Confirm ziplineApiDump tasks are available on non-JVM projects. */
  @Test
  fun ziplineApiDumpOnAndroidProject() {
    val projectDir = File("src/test/projects/android")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.exists()).isTrue()
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun apiTrackingDisabledDoesNotGenerateFileOrTasks() {
    val projectDir = File("src/test/projects/no-api-tracking")

    val checkResult = createRunner(projectDir, "clean", "check").build()
    assertThat(checkResult.tasks.map { it.path }).doesNotContain(":lib:ziplineApiCheck")

    val dumpResult = createRunner(projectDir, ":lib:ziplineApiDump").buildAndFail()
    assertThat(dumpResult.output).contains("Cannot locate tasks that match ':lib:ziplineApiDump'")
  }

  @Test
  fun pluginWithNoRuntimeDoesNotCrash() {
    val projectDir = File("src/test/projects/no-runtime")
    createRunner(projectDir, "clean", "run").build()
  }

  private fun createRunner(
    projectDir: File,
    vararg taskNames: String,
  ): GradleRunner {
    val gradleRoot = projectDir.resolve("gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(gradleRoot.resolve("wrapper"), true)
    File(projectDir, "kotlin-js-store/yarn.lock").delete()
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments(*arguments, *taskNames, versionProperty)
      .forwardOutput()
  }

  /**
   * Given a list of strings and a (potentially-smaller) list of patterns, confirm that each pattern
   * is found in the strings, and that these matches occur in order.
   */
  private fun Assert<Iterable<String>>.containsMatchForEachInOrder(vararg patterns: Regex) {
    given { strings ->
      val s = strings.iterator()

      eachPattern@ for (pattern in patterns) {
        while (s.hasNext()) {
          if (pattern.containsMatchIn(s.next())) continue@eachPattern
        }

        throw AssertionError(
          "no match for ${pattern.pattern} in\n${strings.joinToString(separator = "\n")}",
        )
      }
    }
  }

  companion object {
    val SUCCESS_OUTCOMES = listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PziplineVersion=${System.getProperty("ziplineVersion")}"

    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
    private val manifestFileName = app.cash.zipline.loader.internal.MANIFEST_FILE_NAME
  }
}

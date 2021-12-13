package app.cash.zipline.gradle

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

fun runGradleCompileZiplineTask(
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

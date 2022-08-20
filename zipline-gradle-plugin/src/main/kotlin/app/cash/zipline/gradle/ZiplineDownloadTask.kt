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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Download a Zipline application as part of your build process, such as for embedding into
 * an Android or iOS app to support offline, first-launch, and/or other usage.
 */
@Suppress("unused") // Public API for Gradle plugin users.
abstract class ZiplineDownloadTask : DefaultTask() {
  @get:Input
  abstract val applicationName: Property<String>

  @get:Input
  abstract val manifestUrl: Property<String>

  @get:OutputDirectory
  abstract val downloadDir: DirectoryProperty

  @TaskAction
  fun task() {
    val ziplineDownloader = ZiplineGradleDownloader()
    ziplineDownloader.download(downloadDir.get().asFile, applicationName.get(), manifestUrl.get())
  }
}

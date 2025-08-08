/*
 * Copyright (C) 2023 Block, Inc.
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

import app.cash.zipline.gradle.BuildConfig.ziplineVersion
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider

internal fun <T : Any> Iterable<Provider<T>>.flatten(): Provider<List<T>> {
  val empty: Provider<List<T>> = DefaultProvider { emptyList() }
  return fold(empty) { listProvider, elementProvider ->
    listProvider.zip(elementProvider, Collection<T>::plus)
  }
}

internal fun String.capitalize(): String {
  return lowercase(locale = Locale.US)
    .replaceFirstChar { it.titlecase(locale = Locale.US) }
}

internal fun Project.ziplineDependency(artifactId: String): Any {
  // Indicates when the plugin is applied inside the Zipline repo to Zipline's own modules. This
  // changes dependencies from being external Maven coordinates to internal project references.
  val isInternalBuild = properties["app.cash.zipline.internal"].toString() == "true"

  return when {
    isInternalBuild -> project(":$artifactId")
    else -> "app.cash.zipline:$artifactId:$ziplineVersion"
  }
}

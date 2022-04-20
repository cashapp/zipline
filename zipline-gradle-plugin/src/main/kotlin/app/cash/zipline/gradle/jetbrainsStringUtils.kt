/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package app.cash.zipline.gradle

/*
 * Functions copied from the Kotlin Gradle plugin in `org.jetbrains.kotlin.gradle.utils`.
 */

internal fun lowerCamelCaseName(vararg nameParts: String?): String {
  val nonEmptyParts = nameParts.mapNotNull { it?.takeIf(String::isNotEmpty) }
  return nonEmptyParts.drop(1).joinToString(
    separator = "",
    prefix = nonEmptyParts.firstOrNull().orEmpty(),
    transform = String::capitalize
  )
}

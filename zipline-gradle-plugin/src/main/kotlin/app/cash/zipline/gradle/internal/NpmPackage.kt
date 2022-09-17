package app.cash.zipline.gradle.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class NpmPackage(
  val main: String?,
)

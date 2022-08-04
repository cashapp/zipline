package app.cash.zipline.loader

import app.cash.zipline.Zipline

/**
 * Zipline engine loaded with code and related metadata on the load.
 */
data class LoadedZipline(
  val zipline: Zipline,
  val freshAtEpochMs: Long,
)

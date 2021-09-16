package app.cash.zipline.samples.emojisearch

interface HostApi {
  suspend fun httpCall(url: String): ByteArray
}


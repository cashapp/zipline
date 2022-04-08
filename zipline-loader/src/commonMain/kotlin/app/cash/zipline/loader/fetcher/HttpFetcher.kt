package app.cash.zipline.loader.fetcher

import app.cash.zipline.loader.ZiplineHttpClient
import okio.ByteString

/**
 * Fetch from the network.
 */
class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
) : Fetcher {
  override suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String?
  ): ByteString? =
    httpClient.download(url)
}

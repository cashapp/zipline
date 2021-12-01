package app.cash.zipline.loader

import okio.ByteString

class FakeZiplineHttpClient: ZiplineHttpClient {
  var filePathToByteString: Map<String, ByteString> = mapOf()

  override suspend fun download(url: String): ByteString {
    return filePathToByteString[url] ?: throw IllegalArgumentException("404: $url not found")
  }
}

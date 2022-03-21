package app.cash.zipline.loader.interceptors.handler

import app.cash.zipline.Zipline
import okio.FileSystem
import okio.Path

object HandlerInterceptorFactory {
  fun createDownloadOnly(
    downloadFileSystem: FileSystem,
    downloadDir: Path,
  ): List<HandlerInterceptor> = listOf(
    FsSaveHandlerInterceptor(
      downloadFileSystem = downloadFileSystem,
      downloadDir = downloadDir
    )
  )

  fun createProduction(
    zipline: Zipline
  ): List<HandlerInterceptor> = listOf(
    ZiplineLoadHandlerInterceptor(
      zipline = zipline
    )
  )
}

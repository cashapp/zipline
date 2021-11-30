package app.cash.zipline.loader

import app.cash.zipline.Zipline
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
class ZiplineLoader(
// TODO add caching
//  val cacheDirectory: Path,
//  val cacheMaxSizeInBytes: Int = 100 * 1024 * 1024,
  val client: ZiplineHttpClient,
) {
  suspend fun load(scope: CoroutineScope, zipline: Zipline, manifest: ZiplineManifest) {
    // TODO consider moving this to `async` on the Zipline's dispatcher, which is guaranteed single-threaded
    val ziplineMutex = Mutex()
    val concurrentDownloadsSemaphore = Semaphore(3)

    val loads = manifest.files.map {
      ModuleLoad(zipline, ziplineMutex, concurrentDownloadsSemaphore, it, mutableListOf())
    }

    val idToLoad = loads.associateBy { it.module.id }

    val loadsSorted = loads.topologicalSort { load ->
      load.module.dependsOnIds.map { upstream ->
        idToLoad[upstream] ?: throw IllegalArgumentException("${load.module.id} depends on unknown module $upstream")
      }
    }

    for (load in loadsSorted) {
      val deferred: Deferred<*> = scope.async {
        load.load()
      }

      val downstreams = loads.filter { load.module.id in it.module.dependsOnIds }

      for (downstream in downstreams) {
        downstream.upstreams += deferred
      }
    }
  }

  private inner class ModuleLoad(
    val zipline: Zipline,
    val ziplineMutex: Mutex,
    val concurrentDownloadsSemaphore: Semaphore,
    val module: ZiplineModule,
    val upstreams: MutableList<Deferred<*>>,
  ) {
    suspend fun load() {
      val download = concurrentDownloadsSemaphore.withPermit {
        client.download(module.filePath)
      }
      for (upstream in upstreams) {
        upstream.await()
      }
      ziplineMutex.withLock {
        zipline.loadJsModule(download.toByteArray(), module.id, module.filePath)
      }
    }
  }

  /** For downloading patches instead of full-sized files. */
  suspend fun localFileHashes(): List<ByteString> {
    TODO()
  }
}

//expect
interface ZiplineHttpClient {
  suspend fun download(filePath: String): ByteString
}

class FakeZiplineHttpClient: ZiplineHttpClient {
  var filePathToByteString: Map<String, ByteString> = mapOf()

  override suspend fun download(filePath: String): ByteString {
    return filePathToByteString[filePath] ?: throw IllegalArgumentException("404: $filePath not found")
  }
}

class ZiplineManifest(
  val files: List<ZiplineModule>
)

data class ZiplineModule(
  val id: String,
  val filePath: String,
  val sha256: ByteString,
  val patchFrom: String? = null,
  val patchUrl: String? = null,
  val dependsOnIds: List<String> = listOf()
) {
  init {
    require (id !in dependsOnIds) {
      "Invalid circular dependency on self for [id=$id]"
    }

    require(!filePath.startsWith("http")) {
      "[filePath=$filePath] should be a relative path to the base configured in ZiplineHttpClient, not an absolute URL"
    }
  }
}

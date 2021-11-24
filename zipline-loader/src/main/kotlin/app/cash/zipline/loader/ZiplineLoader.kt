package app.cash.zipline.loader

import app.cash.zipline.Zipline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

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
  /*
  file a.js:
  globalThis.loadedFiles = globalThis.loadedFiles || [];
  globalThis.loadedFiles += 'A';
   */

  /**
   * This returns once all of the modules in [manifest] have been loaded into [zipline].
   * This function can be canceled, but doing so leaves [zipline] in an undefined state.
   *
   * version zero:
   * download each file sequentially
   * call Zipline.loadJsModule()
   *
   * version one:
   * download each file sequentially if not stored on disk
   * store files on disk using okio.FileSystem
   * gotcha: atomicMove()
   * gotcha: download fails, this will leave garbage around forever
   * call Zipline.loadJsModule()
   *
   * version two:
   * same as version one, but it doesn't leave garbage around forever
   * probably Sqlite to store downloads in flight
   * call Zipline.loadJsModule()
   *
   * version three:
   * download in parallel
   * load as soon as downloads finish
   * honor topo sort
   * https://github.com/square/okhttp/blob/master/okhttp/src/main/kotlin/okhttp3/internal/cache/DiskLruCache.kt
   *
   * version four:
   * prune old files
   * note that a file that has been pruned might also be loaded shortly after pruning; perhaps
   * even between deleting on disk and deleting in DB
   */
  suspend fun load(scope: CoroutineScope, zipline: Zipline, manifest: ZiplineManifest) {
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



    loadsSorted.forEach {
      println("Loading ${it.module.id}...")

      // download the file from url
      //
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
        client.download(module.url)
      }
      for (upstream in upstreams) {
        upstream.await()
      }
      ziplineMutex.withLock {
//        zipline.loadJsModule(download.toByteArray(), module.id)
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
  suspend fun download(url: String): ByteString
}

class JvmZiplineHttpClient: ZiplineHttpClient {
  override suspend fun download(url: String): ByteString {
    println("Downloading $url...")
    return ByteString.EMPTY
  }

}

class ZiplineManifest(
  val files: List<ZiplineModule>
)

data class ZiplineModule(
  val id: String,
  val url: String,
  val sha256: ByteString,
  val patchFrom: String? = null,
  val patchUrl: String? = null,
  val dependsOnIds: List<String> = listOf()
) {
  init {
    require (id !in dependsOnIds) {
      "Invalid circular dependency on self for [id=$id]"
    }
  }
}

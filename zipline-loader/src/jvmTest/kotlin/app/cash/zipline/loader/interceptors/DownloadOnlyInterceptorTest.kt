// /*
//  * Copyright (C) 2022 Square, Inc.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *      http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
//
// package app.cash.zipline.loader.interceptors
//
// import app.cash.zipline.QuickJs
// import app.cash.zipline.loader.FakeZiplineHttpClient
// import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
// import app.cash.zipline.loader.ZiplineModuleLoader
// import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
// import kotlin.test.assertEquals
// import kotlinx.coroutines.runBlocking
// import kotlinx.coroutines.sync.Semaphore
// import kotlinx.coroutines.test.TestCoroutineDispatcher
// import okio.FileSystem
// import okio.Path.Companion.toPath
// import okio.fakefilesystem.FakeFileSystem
// import org.junit.After
// import org.junit.Before
// import org.junit.Test
//
// class DownloadOnlyInterceptorTest {
//   private val httpClient = FakeZiplineHttpClient()
//   private val dispatcher = TestCoroutineDispatcher()
//   private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
//
//   private var concurrentDownloadsSemaphore = Semaphore(3)
//
//   private lateinit var fileSystem: FileSystem
//   private val downloadDir = "/zipline/downloads".toPath()
//   private lateinit var quickJs: QuickJs
//
//   private lateinit var moduleLoader: ZiplineModuleLoader
//
//   @Before
//   fun setUp() {
//     quickJs = QuickJs.create()
//     fileSystem = FakeFileSystem()
//     moduleLoader = ZiplineModuleLoader.createDownloadOnly(
//       dispatcher = dispatcher,
//       httpClient = httpClient,
//       concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
//       downloadDir = downloadDir,
//       downloadFileSystem = fileSystem
//     )
//   }
//
//   @After
//   fun tearDown() {
//     quickJs.close()
//     cacheDbDriver.close()
//   }
//
//   @Test
//   fun getFileFromNetwork(): Unit = runBlocking {
//     httpClient.filePathToByteString = mapOf(
//       alphaFilePath to alphaBytecode(quickJs),
//     )
//
//     val ziplineFile = moduleLoader.load(
//
//     )
//
//       strategy.getZiplineFile(
//       id = "alpha",
//       sha256 = alphaBytecode(quickJs).sha256(),
//       url = alphaFilePath
//     )
//
//     assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
//   }
//
//
//   @Test
//   fun processFileWritesToDownloadDirectory(): Unit = runBlocking {
//     val alphaByteString = alphaBytecode(quickJs)
//     httpClient.filePathToByteString = mapOf(
//       alphaFilePath to alphaByteString,
//     )
//
//     val ziplineFile = alphaByteString.toZiplineFile()
//     strategy.processFile(
//       ziplineFile = ziplineFile,
//       id = "alpha",
//       sha256 = alphaByteString.sha256(),
//     )
//
//     assertEquals(ziplineFile, ziplineFile)
//   }
// }

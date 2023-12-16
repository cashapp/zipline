/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.samples.trivia

import app.cash.zipline.Zipline
import app.cash.zipline.loader.DefaultFreshnessCheckerNotFresh
import app.cash.zipline.loader.LoadResult
import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.ZiplineLoader
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
fun getTriviaService(zipline: Zipline): TriviaService {
  return zipline.take("triviaService")
}

suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  val manifestUrl = "http://localhost:8080/manifest.zipline.json"
  val loader = ZiplineLoader(
    dispatcher,
    NO_SIGNATURE_CHECKS,
    OkHttpClient(),
  )
  return when (val result = loader.loadOnce("trivia", DefaultFreshnessCheckerNotFresh, manifestUrl)) {
    is LoadResult.Success -> result.zipline
    is LoadResult.Failure -> error(result.exception)
  }
}

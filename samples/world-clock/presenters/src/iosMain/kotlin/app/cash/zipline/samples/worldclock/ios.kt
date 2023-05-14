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
package app.cash.zipline.samples.worldclock

import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.ZiplineLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import platform.Foundation.NSURLSession

class WorldClockIos(
  private val scope: CoroutineScope,
) {
  private val ziplineDispatcher = Dispatchers.Main
  private val urlSession = NSURLSession.sharedSession

  val events = flowOf<WorldClockEvent>()
  val models = MutableStateFlow(WorldClockModel(label = "..."))

  fun start(modelsCallback: (WorldClockModel) -> Unit) {
    startWorldClockZipline(
      scope = scope,
      ziplineDispatcher = ziplineDispatcher,
      ziplineLoader = ZiplineLoader(
        dispatcher = ziplineDispatcher,
        manifestVerifier = NO_SIGNATURE_CHECKS,
        urlSession = urlSession,
      ),
      manifestUrl = "http://localhost:8080/manifest.zipline.json",
      host = RealWorldClockHost(),
      events = events,
      models = models,
    )
    scope.launch(ziplineDispatcher) {
      models.collect { model ->
        modelsCallback(model)
      }
    }
  }
}

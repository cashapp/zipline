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
package app.cash.zipline.samples.emojisearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.zipline.QuickJs
import app.cash.zipline.tools.startCpuSampling
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import okio.Closeable

/**
 * Adds a 'start/stop' floating action button in the bottom right corner to toggle the sampling
 * profiler on and off. Samples are written to [baseDir].
 */
class ProfilerToggle(
  val baseDir: File,
  val quickJs: QuickJs,
) {
  @Composable
  fun Wrap(
    composable: @Composable () -> Unit
  ) {
    val profilingState = remember { mutableStateOf<ProfilerState>(Idle()) }

    Box(Modifier.fillMaxSize()) {
      composable()

      FloatingActionButton(
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        onClick = { profilingState.value = profilingState.value.toggle() }
      ) {
        Text(profilingState.value.action)
      }
    }
  }

  private interface ProfilerState {
    val action: String
    fun toggle(): ProfilerState
  }

  private inner class Idle : ProfilerState {
    override val action get() = "Start"

    override fun toggle(): ProfilerState {
      val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
      val out = File(baseDir, "emoji-search-$now.hprof")
      val closeable = quickJs.startCpuSampling(out)
      return Running(closeable)
    }
  }

  private inner class Running(
    private val closeable: Closeable
  ) : ProfilerState {
    override val action get() = "Stop"

    override fun toggle(): ProfilerState {
      closeable.close()
      return Idle()
    }
  }
}

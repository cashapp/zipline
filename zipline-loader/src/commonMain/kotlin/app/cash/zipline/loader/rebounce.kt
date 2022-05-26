// Copyright Square, Inc.
package app.cash.zipline.loader

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Returns a flow that mirrors [this], but repeats the latest value every [duration] if no value is
 * emitted by [this]. This is the inverse of `Flow.debounce()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.rebounce(duration: Duration): Flow<T> {
  return transformLatest {
    while (true) {
      emit(it)
      delay(duration)
    }
  }
}

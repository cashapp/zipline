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
package app.cash.zipline

import app.cash.zipline.testing.SchedulerService
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * We've had crashes where timeouts implemented in JS yield unexpected recursive calls and very deep
 * stacks. Confirm that recursive suspend functions don't crash.
 */
class ZiplineDispatchTest {
  private val uncaughtExceptionHandler = TestUncaughtExceptionHandler()
  private val executorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "ZiplineDispatchTest").also {
      it.uncaughtExceptionHandler = uncaughtExceptionHandler
    }
  }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp() {
    runBlocking(dispatcher) {
      zipline.loadTestingJs()
    }
  }

  @After fun tearDown() {
    runBlocking(dispatcher) {
      zipline.close()
    }
    executorService.shutdown()
    executorService.awaitTermination(5, TimeUnit.SECONDS)
  }

  @Test
  fun callbacksCalledInSequence() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSchedulerService()")
    val schedulerService = zipline.take<SchedulerService>("schedulerService")

    val channel = Channel<String>(capacity = 10)

    coroutineScope {
      async {
        schedulerService.schedule(100L, object : SchedulerService.Callback {
          override suspend fun invoke(): String {
            channel.send("delay = 100")
            return "success"
          }
        })
      }
      async {
        schedulerService.schedule(0L, object : SchedulerService.Callback {
          override suspend fun invoke(): String {
            channel.send("delay = 0")
            return "success"
          }
        })
      }
      async {
        schedulerService.schedule(200L, object : SchedulerService.Callback {
          override suspend fun invoke(): String {
            channel.send("delay = 200")
            return "success"
          }
        })
      }
    }

    assertThat(channel.receive()).isEqualTo("delay = 0")
    assertThat(channel.receive()).isEqualTo("delay = 100")
    assertThat(channel.receive()).isEqualTo("delay = 200")
  }

  @Test
  fun recursiveCallbacksInterleaved() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSchedulerService()")
    val schedulerService = zipline.take<SchedulerService>("schedulerService")

    val channel = Channel<String>(capacity = 10)

    coroutineScope {
      async {
        schedulerService.schedule(100L, object : SchedulerService.Callback {
          override suspend fun invoke(): String {
            channel.send("delay = 100 before")
            this@coroutineScope.async {
              schedulerService.schedule(200L, object : SchedulerService.Callback {
                override suspend fun invoke(): String {
                  channel.send("delay = 100 + 200")
                  return "success"
                }
              })
            }
            channel.send("delay = 100 after")
            return "success"
          }
        })
      }
      async {
        schedulerService.schedule(0L, object : SchedulerService.Callback {
          override suspend fun invoke(): String {
            channel.send("delay = 0 before")
            this@coroutineScope.async {
              schedulerService.schedule(200L, object : SchedulerService.Callback {
                override suspend fun invoke(): String {
                  channel.send("delay = 0 + 200")
                  return "success"
                }
              })
            }
            channel.send("delay = 0 after")
            return "success"
          }
        })
      }
    }

    assertThat(channel.receive()).isEqualTo("delay = 0 before")
    assertThat(channel.receive()).isEqualTo("delay = 0 after")
    assertThat(channel.receive()).isEqualTo("delay = 100 before")
    assertThat(channel.receive()).isEqualTo("delay = 100 after")
    assertThat(channel.receive()).isEqualTo("delay = 0 + 200")
    assertThat(channel.receive()).isEqualTo("delay = 100 + 200")
  }

  @Test
  fun recursiveSuspendingFunctionsDontStackOverflow() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSchedulerService()")
    val schedulerService = zipline.take<SchedulerService>("schedulerService")

    val callback = object : SchedulerService.Callback {
      var count = 0

      override suspend fun invoke(): String {
        count++
        return when {
          count <= 200 -> {
            val result = schedulerService.schedule(delayMillis = 0L, callback = this)
            ".$result"
          }
          else -> "!"
        }
      }
    }

    assertThat(schedulerService.schedule(0L, callback))
      .isEqualTo("${".".repeat(200)}!")
  }

  @Test
  fun recursiveDelayingFunctionsDontStackOverflow() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSchedulerService()")
    val schedulerService = zipline.take<SchedulerService>("schedulerService")

    val callback = object : SchedulerService.Callback {
      var count = 0

      override suspend fun invoke(): String {
        delay(1L)
        count++
        return when {
          count <= 200 -> {
            val result = schedulerService.schedule(delayMillis = 1L, callback = this)
            ".$result"
          }
          else -> "!"
        }
      }
    }

    assertThat(schedulerService.schedule(0L, callback))
      .isEqualTo("${".".repeat(200)}!")
  }
}

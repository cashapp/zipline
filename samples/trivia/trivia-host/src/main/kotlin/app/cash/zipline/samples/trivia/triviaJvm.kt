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

import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

private fun playGame(triviaService: TriviaService) {
  var correct = 0
  var count = 0

  val games = triviaService.games()
  for (game in games) {
    println(game.name)
    println()

    for (question in game.questions) {
      println(question.text)
      println()

      print("   ➡️  ")
      val answer = readlnOrNull() ?: break
      println()

      val result = triviaService.answer(game.id, question.id, answer.trim())

      if (result.correct) {
        correct++
        println("✅  ${result.message}")
      } else {
        println("💥  ${result.message}")
      }
      println()

      count++
    }
  }

  println("$correct / $count correct!")
}

fun main() {
  val executorService = Executors.newFixedThreadPool(1) {
    Thread(it, "Zipline")
  }
  val dispatcher = executorService.asCoroutineDispatcher()
  runBlocking(dispatcher) {
    val zipline = launchZipline(dispatcher)
    val triviaService = getTriviaService(zipline)
    playGame(triviaService)
  }
  exitProcess(0)
}

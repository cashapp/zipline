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

import app.cash.zipline.ZiplineService
import kotlinx.serialization.Serializable

interface TriviaService : ZiplineService {
  fun games(): List<TriviaGame>
  fun answer(gameId: Int, questionId: Int, answer: String): AnswerResult
}

@Serializable
class TriviaGame(
  val id: Int,
  val name: String,
  val questions: List<Question>,
)

@Serializable
class Question(
  val id: Int,
  val text: String,
)

@Serializable
class AnswerResult(
  val correct: Boolean,
  val message: String,
)

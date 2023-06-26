/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.api.validator.fir

data class FirZiplineApi(
  val services: List<FirZiplineService>,
)

/** An interface that extends from ZiplineService. */
data class FirZiplineService(
  val name: String,
  val functions: List<FirZiplineFunction>,
)

data class FirZiplineFunction(
  val id: String,
  val signature: String,
) {
  internal constructor(signature: String) : this(signature.signatureHash(), signature)
}

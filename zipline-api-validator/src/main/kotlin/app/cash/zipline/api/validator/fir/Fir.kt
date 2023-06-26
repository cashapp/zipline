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

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.resolve.firClassLike

/** Recursively collect all supertypes of this class. */
internal fun FirClass.getAllSupertypes(session: FirSession): Set<FirClass> {
  val result = mutableSetOf<FirClass>()
  collectSupertypes(session, this, result)
  return result
}

private fun collectSupertypes(
  session: FirSession,
  type: FirClass,
  sink: MutableSet<FirClass>,
) {
  if (!sink.add(type)) return // Already added.
  if (type !is FirRegularClass) return // Cannot traverse supertypes.

  val supertypes = type.symbol.resolvedSuperTypeRefs.mapNotNull {
    it.firClassLike(session) as? FirClass
  }

  for (supertype in supertypes) {
    collectSupertypes(session, supertype, sink)
  }
}

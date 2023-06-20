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
package app.cash.zipline.apicheck

import app.cash.zipline.kotlin.BridgedInterface.Companion.NON_INTERFACE_FUNCTION_NAMES
import app.cash.zipline.kotlin.FqPackageName
import app.cash.zipline.kotlin.classId
import java.io.File
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.pipeline.FirResult
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirStarProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.types.Variance

fun readZiplineServices(
  sources: Collection<File>,
  dependencies: Collection<File>,
): List<DeclaredZiplineService> {
  return KotlinFirLoader(sources, dependencies).use { loader ->
    val output = loader.load("zipline-api-dump")
    ZiplineServicesReader(output).read()
  }
}

private val ziplineFqPackage = FqPackageName("app.cash.zipline")
private val ziplineServiceClassId = ziplineFqPackage.classId("ZiplineService")

/**
 * Read the frontend intermediate representation of a program and emit its ZiplineService
 * interfaces. These are subject to strict API compatibility requirements.
 */
internal class ZiplineServicesReader(
  output: FirResult,
) {
  private val platformOutput = output.platformOutput
  private val session: FirSession = platformOutput.session

  private val ziplineServiceClass: FirClassLikeSymbol<*>? =
    session.symbolProvider.getClassLikeSymbolByClassId(ziplineServiceClassId)

  fun read(): List<DeclaredZiplineService> {
    val types = platformOutput.fir
      .flatMap { it.declarations.findRegularClassesRecursive() }
      .filter { it.isInterface && it.isZiplineService }

    return types
      .map { it.asDeclaredZiplineService() }
      .sortedBy { it.name }
  }

  private val FirRegularClass.isZiplineService: Boolean
    get() {
      val ziplineServiceClssSymbol = ziplineServiceClass as? FirClassSymbol<*> ?: return false
      return ziplineServiceClssSymbol.isSupertypeOf(symbol, session)
    }

  private fun FirRegularClass.asDeclaredZiplineService(): DeclaredZiplineService {
    return DeclaredZiplineService(
      symbol.classId.asSingleFqName().asString(),
      bridgedFunctions(this),
    )
  }

  private fun bridgedFunctions(type: FirRegularClass): List<DeclaredZiplineFunction> {
    val result = sortedSetOf<DeclaredZiplineFunction>(
      { a, b -> a.signature.compareTo(b.signature) },
    )

    for (supertype in type.getAllSupertypes(session)) {
      if (!supertype.isInterface) continue // Skip kotlin.Any.

      for (declaration in supertype.declarations) {
        when (declaration) {
          is FirFunction -> {
            if (declaration.isNonInterfaceFunction) continue
            result += declaration.asDeclaredZiplineFunction()
          }

          is FirProperty -> {
            result += declaration.asDeclaredZiplineFunction()
          }

          else -> Unit
        }
      }
    }

    return result.toList()
  }

  private val FirFunction.isNonInterfaceFunction: Boolean
    get() = symbol.name.identifier in NON_INTERFACE_FUNCTION_NAMES

  private fun FirFunction.asDeclaredZiplineFunction(): DeclaredZiplineFunction {
    val signature = buildString {
      if (isSuspend) append("suspend ")
      append("fun ${symbol.name.identifier}(")
      append(valueParameters.joinToString { it.returnTypeRef.asString() })
      append("): ${returnTypeRef.asString()}")
    }

    return DeclaredZiplineFunction(signature)
  }

  private fun FirProperty.asDeclaredZiplineFunction(): DeclaredZiplineFunction {
    val signature = when {
      isVar -> "var ${symbol.name.identifier}: ${returnTypeRef.asString()}"
      else -> "val ${symbol.name.identifier}: ${returnTypeRef.asString()}"
    }
    return DeclaredZiplineFunction(signature)
  }

  /** See [app.cash.zipline.kotlin.asString]. */
  private fun FirTypeRef.asString(): String {
    val classSymbol = toRegularClassSymbol(session) ?: error("unexpected class: $this")

    val typeRef = when (this) {
      is FirResolvedTypeRef -> delegatedTypeRef ?: this
      else -> this
    }

    return buildString {
      append(classSymbol.classId.asSingleFqName().asString())

      if (typeRef is FirUserTypeRef) {
        val typeArguments = typeRef.qualifier.lastOrNull()?.typeArgumentList?.typeArguments
        if (typeArguments?.isEmpty() == false) {
          typeArguments.joinTo(this, separator = ",", prefix = "<", postfix = ">") {
            it.asString()
          }
        }
      }
    }
  }

  private fun FirTypeProjection.asString(): String {
    return when (this) {
      is FirStarProjection -> {
        "*"
      }
      is FirTypeProjectionWithVariance -> {
        variance.label + (if (variance != Variance.INVARIANT) " " else "") + typeRef.asString()
      }
      else -> {
        error("Unexpected kind of FirTypeProjection: " + javaClass.simpleName)
      }
    }
  }

  private fun List<FirDeclaration>.findRegularClassesRecursive(): List<FirRegularClass> {
    val classes = filterIsInstance<FirRegularClass>()
    return classes + classes.flatMap { it.declarations.findRegularClassesRecursive() }
  }
}

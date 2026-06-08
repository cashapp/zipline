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

import java.io.File
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.pipeline.FirResult
import org.jetbrains.kotlin.fir.resolve.firClassLike
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirStarProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.lookupTagIfAny
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

fun readFirZiplineApi(
  javaHome: File,
  jdkRelease: Int,
  sources: Collection<File>,
  classpath: Collection<File>,
  options: FirZiplineApiReaderOptions = FirZiplineApiReaderOptions(),
): FirZiplineApi {
  return KotlinFirLoader(javaHome, jdkRelease, sources, classpath).use { loader ->
    val output = loader.load("zipline-api-dump")
    FirZiplineApiReader(output, options).read()
  }
}

data class FirZiplineApiReaderOptions(
  val includeSchemaInFunctionIds: Boolean = false,
  val includeApiConstants: Boolean = false,
)

private val ziplineServiceClassId =
  ClassId(FqName("app.cash.zipline"), Name.identifier("ZiplineService"))

private val ziplineApiConstantClassId =
  ClassId(FqName("app.cash.zipline"), Name.identifier("ZiplineApiConstant"))

private val autoCloseableClassId =
  ClassId(FqName("java.lang"), Name.identifier("AutoCloseable"))

/**
 * Read the frontend intermediate representation of a program and emit its ZiplineService
 * interfaces. These are subject to strict API compatibility requirements.
 */
internal class FirZiplineApiReader(
  output: FirResult,
  private val options: FirZiplineApiReaderOptions = FirZiplineApiReaderOptions(),
) {
  private val platformOutput = output.outputs.single()
  private val session: FirSession = platformOutput.session

  private val ziplineServiceClass: FirClassLikeSymbol<*>? =
    session.symbolProvider.getClassLikeSymbolByClassId(ziplineServiceClassId)

  fun read(): FirZiplineApi {
    val types = platformOutput.fir
      .flatMap { it.regularClasses() }
      .filter { it.isInterface && it.isZiplineService }

    val services = types
      .map { it.asDeclaredZiplineService() }
      .sortedBy { it.name }

    return FirZiplineApi(services)
  }

  private val FirRegularClass.isZiplineService: Boolean
    get() {
      val ziplineServiceClssSymbol = ziplineServiceClass as? FirClassSymbol<*> ?: return false
      return ziplineServiceClssSymbol.isSupertypeOf(symbol, session)
    }

  private fun FirRegularClass.asDeclaredZiplineService(): FirZiplineService {
    return FirZiplineService(
      symbol.classId.asSingleFqName().asString(),
      bridgedFunctions(this),
      if (options.includeApiConstants) companionConstants() else listOf(),
    )
  }

  private fun bridgedFunctions(type: FirRegularClass): List<FirZiplineFunction> {
    val result = sortedSetOf<FirZiplineFunction>(
      { a, b -> a.signature.compareTo(b.signature) },
    )

    for (supertype in type.getAllSupertypes(session)) {
      if (!supertype.isInterface) continue // Skip kotlin.Any.
      if (supertype.symbol.classId == autoCloseableClassId) continue // Skip AutoCloseable.

      supertype.processAllDeclarations(session) { symbol ->
        when (symbol) {
          is FirFunctionSymbol -> {
            if (symbol.isNonInterfaceFunction) return@processAllDeclarations
            result += symbol.asDeclaredZiplineFunction()
          }

          is FirPropertySymbol -> {
            result += symbol.asDeclaredZiplineFunction()
          }

          else -> Unit
        }
      }
    }

    return result.toList()
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun FirRegularClass.companionConstants(): List<FirZiplineConstant> {
    val serviceName = symbol.classId.asSingleFqName().asString()
    return declarations
      .filterIsInstance<FirRegularClass>()
      .filter { it.isCompanion }
      .flatMap { companion ->
        companion.declarations
          .filterIsInstance<FirProperty>()
          .filter { it.hasAnnotation(ziplineApiConstantClassId, session) }
          .map { it.asDeclaredZiplineConstant(serviceName) }
      }
      .sortedBy { it.signature }
  }

  private fun FirProperty.asDeclaredZiplineConstant(serviceName: String): FirZiplineConstant {
    if (!isConst || isVar) {
      error("@ZiplineApiConstant may only annotate const val properties: $serviceName.${name.asString()}")
    }

    val value = initializer?.asConstantValueString(serviceName, name.asString())
      ?: error("@ZiplineApiConstant property must have an initializer: $serviceName.${name.asString()}")
    val signature = "const val ${name.asString()}: ${symbol.resolvedReturnTypeRef.asString()} = $value"
    return FirZiplineConstant(signature)
  }

  private fun FirExpression.asConstantValueString(
    serviceName: String,
    propertyName: String,
  ): String {
    val literal = evaluateAs<FirLiteralExpression>(session)
      ?: error("@ZiplineApiConstant property must have a compile-time constant value: $serviceName.$propertyName")
    return literal.value.toKotlinLiteral()
  }

  private fun Any?.toKotlinLiteral(): String {
    return when (this) {
      null -> "null"
      is String -> quoteAsKotlinString()
      is Char -> quoteAsKotlinChar()
      else -> toString()
    }
  }

  private fun String.quoteAsKotlinString(): String {
    return buildString {
      append('"')
      for (char in this@quoteAsKotlinString) {
        append(char.escapeForKotlinLiteral())
      }
      append('"')
    }
  }

  private fun Char.quoteAsKotlinChar(): String {
    return "'" + escapeForKotlinLiteral() + "'"
  }

  private fun Char.escapeForKotlinLiteral(): String {
    return when (this) {
      '\\' -> "\\\\"
      '"' -> "\\\""
      '\'' -> "\\'"
      '\n' -> "\\n"
      '\r' -> "\\r"
      '\t' -> "\\t"
      else -> toString()
    }
  }

  private val FirFunctionSymbol<*>.isNonInterfaceFunction: Boolean
    get() = name.identifier in NON_INTERFACE_FUNCTION_NAMES

  private fun FirFunctionSymbol<*>.asDeclaredZiplineFunction(): FirZiplineFunction {
    val parameterTypeRefs = valueParameterSymbols.map { it.resolvedReturnTypeRef }
    val signature = buildString {
      if (isSuspend) append("suspend ")
      append("fun ${name.identifier}(")
      parameterTypeRefs.joinTo(this) { it.asString() }
      append("): ${resolvedReturnTypeRef.asString()}")
    }
    val id = if (options.includeSchemaInFunctionIds) {
      val expandedSignature = buildString {
        if (isSuspend) append("suspend ")
        append("fun ${name.identifier}(")
        parameterTypeRefs.joinTo(this) { it.asExpandedString() }
        append("): ${resolvedReturnTypeRef.asExpandedString()}")
      }
      expandedSignature.signatureHash()
    } else {
      signature.signatureHash()
    }

    return FirZiplineFunction(id = id, signature = signature)
  }

  private fun FirPropertySymbol.asDeclaredZiplineFunction(): FirZiplineFunction {
    val valOrVar = if (isVar) "var" else "val"
    val signature = "$valOrVar ${name.identifier}: ${resolvedReturnTypeRef.asString()}"
    val id = if (options.includeSchemaInFunctionIds) {
      val signatureWithSchema = "$valOrVar ${name.identifier}: ${resolvedReturnTypeRef.asExpandedString()}"
      signatureWithSchema.signatureHash()
    } else {
      signature.signatureHash()
    }
    return FirZiplineFunction(id = id, signature = signature)
  }

  /** See [app.cash.zipline.kotlin.asString]. */
  private fun FirTypeRef.asString(): String {
    // Abbreviated type gets us the name of typealiases rather than what they expand to.
    val classLikeSymbol = coneType.abbreviatedTypeOrSelf.lookupTagIfAny
      ?.toClassLikeSymbol(session) ?: error("unexpected class: $this")

    return buildString {
      append(classLikeSymbol.classId.asSingleFqName().asString())

      val typeArguments = typeArguments()
      if (typeArguments.isNotEmpty()) {
        typeArguments.joinTo(this, separator = ",", prefix = "<", postfix = ">") {
          it.asString()
        }
      }

      if (coneType.isMarkedNullable) {
        append("?")
      }
    }
  }

  private fun FirTypeProjection.asExpandedString(
    visiting: MutableSet<String>,
  ): String {
    return when (this) {
      is FirStarProjection -> {
        "*"
      }

      is FirTypeProjectionWithVariance -> {
        variance.label + (if (variance != Variance.INVARIANT) " " else "") +
          typeRef.asExpandedString(visiting)
      }

      else -> {
        error("Unexpected kind of FirTypeProjection: " + javaClass.simpleName)
      }
    }
  }

  /**
   * Returns a string representation of this type reference for API tracking. Source DTOs are
   * expanded inline; built-in, classpath, and Zipline service types are left as named leaves.
   */
  private fun FirTypeRef.asExpandedString(
    visiting: MutableSet<String> = mutableSetOf(),
    excludedPropertyNames: Set<String> = emptySet(),
  ): String {
    val lookupTag = coneType.abbreviatedTypeOrSelf.lookupTagIfAny
      ?: error("unexpected class: $this")
    val classLikeSymbol = lookupTag.toClassLikeSymbol(session)
    val className = classLikeSymbol?.classId?.asSingleFqName()?.asString()
      ?: lookupTag.name.asString()

    return buildString {
      append(className)

      val typeArguments = typeArguments()
      if (typeArguments.isNotEmpty()) {
        typeArguments.joinTo(this, separator = ",", prefix = "<", postfix = ">") {
          it.asExpandedString(visiting)
        }
      }

      if (classLikeSymbol != null) {
        val regularClass = firRegularClassOrNull()
        if (regularClass != null && regularClass.origin.fromSource && !regularClass.isZiplineService) {
          if (visiting.add(className)) {
            append(regularClass.schema(visiting, excludedPropertyNames))
            visiting -= className
          } else {
            append("{...}")
          }
        }
      }

      if (coneType.isMarkedNullable) {
        append("?")
      }
    }
  }

  /**
   * Builds the stable schema fragment for a source class.
   *
   * Example:
   * ```
   * {kind=class;prop=foo:java.lang.String;prop=bar:java.lang.String}
   * ```
   */
  private fun FirRegularClass.schema(
    visiting: MutableSet<String>,
    excludedPropertyNames: Set<String>,
  ): String {
    return buildString {
      append("{kind=")
      append(classKind.name.lowercase())

      val properties = firProperties()

      val overriddenPropertyNames = properties
        .filter { it.isOverride }
        .mapTo(mutableSetOf()) { it.name.asString() }
      val supertypeExcludedPropertyNames = excludedPropertyNames + overriddenPropertyNames
      val supertypes = symbol.resolvedSuperTypeRefs
        .filter {
          val regularClass = it.firRegularClassOrNull() ?: return@filter false
          !regularClass.origin.isBuiltIns && !regularClass.isZiplineService
        }
        .sortedBy { it.asString() }
      for (supertype in supertypes) {
        append(";super=")
        append(supertype.asExpandedString(visiting, supertypeExcludedPropertyNames))
      }

      for (property in properties) {
        if (property.name.asString() in excludedPropertyNames) continue
        append(";prop=")
        append(property.name.asString())
        append(":")
        append(property.symbol.resolvedReturnTypeRef.asExpandedString(visiting))
      }

      for (enumEntry in firEnumEntries()) {
        append(";enum=")
        append(enumEntry.name.asString())
      }

      append("}")
    }
  }

  private fun FirTypeRef.firRegularClassOrNull(): FirRegularClass? {
    return firClassLike(session) as? FirRegularClass
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun FirRegularClass.firProperties(): List<FirProperty> {
    return if (classKind == ClassKind.ENUM_CLASS) {
      listOf()
    } else {
      declarations
        .filterIsInstance<FirProperty>()
        .filter { it.origin.fromSource }
        .filter { it.receiverParameter == null }
        .sortedBy { it.name.asString() }
    }
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun FirRegularClass.firEnumEntries(): List<FirEnumEntry> {
    return declarations
      .filterIsInstance<FirEnumEntry>()
      .sortedBy { it.name.asString() }
  }

  private fun FirTypeRef.typeArguments(): List<FirTypeProjection> {
    val typeRef = when (this) {
      is FirResolvedTypeRef -> delegatedTypeRef ?: this
      else -> this
    }

    return when (typeRef) {
      is FirUserTypeRef -> typeRef.qualifier.lastOrNull()?.typeArgumentList?.typeArguments
        ?: listOf()

      else -> listOf()
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

  /** Collect all regular class declarations in this. */
  private fun FirFile.regularClasses(): List<FirRegularClass> {
    val result = mutableListOf<FirRegularClass>()
    accept(
      visitor = object : FirDefaultVisitor<Unit, MutableList<FirRegularClass>>() {
        override fun visitRegularClass(
          regularClass: FirRegularClass,
          data: MutableList<FirRegularClass>,
        ) {
          super.visitRegularClass(regularClass, data)
          data.add(regularClass)
        }

        override fun visitElement(
          element: FirElement,
          data: MutableList<FirRegularClass>,
        ) {
          element.acceptChildren(this, data)
        }
      },
      data = result,
    )
    return result
  }
}

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

import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineService
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import org.junit.Test

internal class FirZiplineApiReaderTest {
  private val javaHome = File(System.getProperty("java.home"))
  private val jdkRelease = Runtime.version().feature()
  private val sources = System.getProperty("zipline.internal.sources")
    .split(File.pathSeparator)
    .map(::File)
    .filter(File::exists) // Entries that don't exist produce warnings.
  private val classpath = System.getProperty("zipline.internal.classpath")
    .split(File.pathSeparator)
    .map(::File)
    .filter(File::exists) // Entries that don't exist produce warnings.

  @Test
  fun happyPath() {
    val ziplineApi = readFirZiplineApi(javaHome, jdkRelease, sources, classpath)
    assertThat(ziplineApi).isEqualTo(
      FirZiplineApi(
        listOf(
          FirZiplineService(
            name = CollectionDtoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun save(${CollectionRequest::class.qualifiedName}): kotlin.Unit"),
            ),
          ),
          FirZiplineService(
            name = EchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
          FirZiplineService(
            name = ExtendedEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("fun echoAll(kotlin.collections.List<kotlin.String>): kotlin.collections.List<kotlin.String>"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
          FirZiplineService(
            name = ExternalTypeService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun record(${CallResult::class.qualifiedName}): kotlin.Unit"),
            ),
          ),
          FirZiplineService(
            name = ImportsJdkTypes::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun jvmIoException(java.io.IOException): kotlin.String"),
              FirZiplineFunction("fun okioIoException(okio.IOException): kotlin.String"),
            ),
          ),
          FirZiplineService(
            name = PersonService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun print(${Person::class.qualifiedName}): kotlin.Unit"),
            ),
          ),
          FirZiplineService(
            name = ProfileService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun show(${Profile::class.qualifiedName}): kotlin.Unit"),
            ),
          ),
          FirZiplineService(
            name = RecursiveDtoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun link(${RecursiveNode::class.qualifiedName}): kotlin.Unit"),
            ),
          ),
          FirZiplineService(
            name = UnnecessaryEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun includesDirectAndInheritedSchemaInFunctionIdsWhenConfigured() {
    val ziplineApi = readFirZiplineApi(
      javaHome,
      jdkRelease,
      sources,
      classpath,
      FirZiplineApiReaderOptions(includeSchemaInFunctionIds = true),
    )

    assertThat(
      ziplineApi.function(
        serviceName = PersonService::class.qualifiedName!!,
        signature = "fun print(${Person::class.qualifiedName}): kotlin.Unit",
      ),
    ).isEqualTo(
      functionWithIdSignature(
        signature = "fun print(${Person::class.qualifiedName}): kotlin.Unit",
        idSignature = "fun print(${Person::class.qualifiedName}{kind=class;prop=address:${Address::class.qualifiedName}{kind=class;prop=street:kotlin.String};prop=name:kotlin.String}): kotlin.Unit",
      ),
    )
    assertThat(
      ziplineApi.function(
        serviceName = ProfileService::class.qualifiedName!!,
        signature = "fun show(${Profile::class.qualifiedName}): kotlin.Unit",
      ),
    ).isEqualTo(
      functionWithIdSignature(
        signature = "fun show(${Profile::class.qualifiedName}): kotlin.Unit",
        idSignature = "fun show(${Profile::class.qualifiedName}{kind=class;super=${AuditedEntityDto::class.qualifiedName}{kind=class;super=${EntityDto::class.qualifiedName}{kind=class};prop=auditInfo:${AuditInfo::class.qualifiedName}{kind=class;prop=createdBy:kotlin.String}};prop=displayName:kotlin.String;prop=id:kotlin.String}): kotlin.Unit",
      ),
    )
    assertThat(
      ziplineApi.function(
        serviceName = ExternalTypeService::class.qualifiedName!!,
        signature = "fun record(${CallResult::class.qualifiedName}): kotlin.Unit",
      ),
    ).isEqualTo(
      FirZiplineFunction("fun record(${CallResult::class.qualifiedName}): kotlin.Unit"),
    )
  }

  @Test
  fun includesNestedSchemaInFunctionIdsWhenConfigured() {
    val ziplineApi = readFirZiplineApi(
      javaHome,
      jdkRelease,
      sources,
      classpath,
      FirZiplineApiReaderOptions(includeSchemaInFunctionIds = true),
    )

    assertThat(
      ziplineApi.function(
        serviceName = CollectionDtoService::class.qualifiedName!!,
        signature = "fun save(${CollectionRequest::class.qualifiedName}): kotlin.Unit",
      ),
    ).isEqualTo(
      functionWithIdSignature(
        signature = "fun save(${CollectionRequest::class.qualifiedName}): kotlin.Unit",
        idSignature = "fun save(${CollectionRequest::class.qualifiedName}{kind=class;prop=external:${CallResult::class.qualifiedName};prop=nullableName:kotlin.String?;prop=people:kotlin.collections.List<${Person::class.qualifiedName}{kind=class;prop=address:${Address::class.qualifiedName}{kind=class;prop=street:kotlin.String};prop=name:kotlin.String}?>;prop=status:${Status::class.qualifiedName}{kind=enum_class;enum=Disabled;enum=Enabled}}): kotlin.Unit",
      ),
    )
  }

  @Test
  fun includesRecursiveSchemaInFunctionIdsWhenConfigured() {
    val ziplineApi = readFirZiplineApi(
      javaHome,
      jdkRelease,
      sources,
      classpath,
      FirZiplineApiReaderOptions(includeSchemaInFunctionIds = true),
    )

    assertThat(
      ziplineApi.function(
        serviceName = RecursiveDtoService::class.qualifiedName!!,
        signature = "fun link(${RecursiveNode::class.qualifiedName}): kotlin.Unit",
      ),
    ).isEqualTo(
      functionWithIdSignature(
        signature = "fun link(${RecursiveNode::class.qualifiedName}): kotlin.Unit",
        idSignature = "fun link(${RecursiveNode::class.qualifiedName}{kind=class;prop=name:kotlin.String;prop=next:${RecursiveNode::class.qualifiedName}{...}?}): kotlin.Unit",
      ),
    )
  }

  /** This uses source-defined DTOs with nested source, enum, nullable, and external leaf types. */
  interface CollectionDtoService : ZiplineService {
    fun save(request: CollectionRequest)
  }

  data class CollectionRequest(
    val external: CallResult,
    val nullableName: String?,
    val people: List<Person?>,
    val status: Status,
  )

  enum class Status {
    Disabled,
    Enabled,
  }

  /** This should be included in the output. */
  interface EchoService : ZiplineService {
    fun echo(request: String): String
    val greeting: String
    var terse: Boolean
  }

  /** This should be included in the output. */
  interface ExtendedEchoService : EchoService {
    fun echoAll(requests: List<String>): List<String>
  }

  /** This uses an external classpath type. */
  interface ExternalTypeService : ZiplineService {
    fun record(result: CallResult)
  }

  /** This uses externally-defined types. */
  interface ImportsJdkTypes : ZiplineService {
    /**
     * In this test we can also use a JDK class directly. This isn't the case for production code,
     * where `ZiplineService` declarations should be defined in `commonMain`.
     */
    fun jvmIoException(e: java.io.IOException): String

    /**
     * Okio's IOException is a 3rd-party class that's typealiased to a JDK class. In the generated
     * TOML file this should use the `okio.IOExeption` name, and not the name it's aliased to.
     */
    fun okioIoException(e: okio.IOException): String
  }

  /** This uses source-defined DTOs. */
  interface PersonService : ZiplineService {
    fun print(person: Person)
  }

  data class Person(
    val name: String,
    val address: Address,
  )

  data class Address(val street: String)

  /** This uses source-defined DTOs with inherited and overridden properties. */
  interface ProfileService : ZiplineService {
    fun show(profile: Profile)
  }

  data class Profile(
    override val id: String,
    val displayName: String,
  ) : AuditedEntityDto(id, AuditInfo("created"))

  open class AuditedEntityDto(
    open override val id: String,
    val auditInfo: AuditInfo,
  ) : EntityDto(id)

  open class EntityDto(open val id: String)

  data class AuditInfo(val createdBy: String)

  /** This uses source-defined recursive DTOs. */
  interface RecursiveDtoService : ZiplineService {
    fun link(node: RecursiveNode)
  }

  data class RecursiveNode(
    val name: String,
    val next: RecursiveNode?,
  )

  /** This should be included in the output, but without additional methods. */
  interface UnnecessaryEchoService : EchoService {
    override fun echo(request: String): String
    override fun equals(other: Any?): Boolean
  }

  /** This shouldn't be included in the output. */
  @Suppress("unused")
  class SampleEchoService : EchoService {
    override val greeting: String get() = error("unexpected call")
    override var terse: Boolean
      get() = error("unexpected call")
      set(value) = error("unexpected call")

    override fun echo(request: String) = error("unexpected call")
  }

  private fun functionWithIdSignature(
    signature: String,
    idSignature: String,
  ): FirZiplineFunction {
    return FirZiplineFunction(
      id = idSignature.signatureHash(),
      signature = signature,
    )
  }

  private fun FirZiplineApi.function(
    serviceName: String,
    signature: String,
  ): FirZiplineFunction {
    return services.single { it.name == serviceName }
      .functions.single { it.signature == signature }
  }
}

/*
 * Copyright (C) 2021 Square, Inc.
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

package app.cash.zipline.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.gradle.deployment.internal.DeploymentRegistry
import org.http4k.core.ContentType
import org.http4k.routing.ResourceLoader.Companion.Directory
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.SunHttp
import org.http4k.server.asServer

abstract class ZiplineServeTask @Inject constructor(
  objectFactory: ObjectFactory
) : DefaultTask() {

  @get:Input
  lateinit var inputDir: Provider<DirectoryProperty>

  @Optional
  @get:Input
  val port: Property<Int> = objectFactory.property(Int::class.java)

  @TaskAction
  fun task() {
    val deploymentId = "serveZipline"
    val deploymentRegistry = services.get(DeploymentRegistry::class.java)
    val deploymentHandle = deploymentRegistry.get(deploymentId, ZiplineServerDeploymentHandle::class.java)
    if (deploymentHandle == null) {
      val server = routes(
        "/" bind static(Directory(inputDir.get().asFile.get().absolutePath), Pair("zipline", ContentType.TEXT_PLAIN))
      ).asServer(SunHttp(port.orElse(8080).get()))

      deploymentRegistry.start(
        deploymentId,
        DeploymentRegistry.ChangeBehavior.BLOCK,
        ZiplineServerDeploymentHandle::class.java,
        server
      )
    }
  }
}

internal open class ZiplineServerDeploymentHandle @Inject constructor(
  private val server: Http4kServer
) : DeploymentHandle {

  var running: Boolean = false

  override fun isRunning(): Boolean = running

  override fun start(deployment: Deployment) {
    running = true
    server.start()
  }

  override fun stop() {
    running = false
    server.stop()
  }
}

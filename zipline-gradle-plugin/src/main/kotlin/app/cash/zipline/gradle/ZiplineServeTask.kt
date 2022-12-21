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

import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.schedule
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
import org.http4k.routing.websockets
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage

abstract class ZiplineServeTask : DefaultTask() {

  @get:InputDirectory
  abstract val inputDir: DirectoryProperty

  @get:Optional
  @get:Input
  abstract val port: Property<Int>

  @TaskAction
  fun task() {
    val deploymentId = "serveZipline"
    val deploymentRegistry = services.get(DeploymentRegistry::class.java)
    val deploymentHandle = deploymentRegistry.get(deploymentId, ZiplineServerDeploymentHandle::class.java)
    if (deploymentHandle == null) {
      // First time this task is run, start the server
      val ws = websockets(
        "/ws" bind { ws: Websocket ->
          websockets.add(ws)
          ws.send(WsMessage(RELOAD_MESSAGE))
          ws.onClose {
            websockets.remove(ws)
          }
        }
      )
      val http = routes(
        "/" bind static(
          Directory(inputDir.get().asFile.absolutePath),
          Pair("zipline", ContentType.TEXT_PLAIN)
        )
      )
      val server = PolyHandler(http, ws).asServer(Jetty(port.orNull ?: 8080))
      deploymentRegistry.start(
        deploymentId,
        DeploymentRegistry.ChangeBehavior.BLOCK,
        ZiplineServerDeploymentHandle::class.java,
        server
      )

      // Keep the connection open by sending a message periodically
      Timer("WebsocketHeartbeat", true).schedule(0, 10000) {
        websockets.forEach { it.send(WsMessage("heartbeat")) }
      }
    } else {
      // Subsequent task runs, just send a websocket message
      websockets.forEach { it.send(WsMessage(RELOAD_MESSAGE)) }
    }
  }

  companion object {
    const val RELOAD_MESSAGE = "reload"
    val websockets: MutableList<Websocket> = mutableListOf()
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

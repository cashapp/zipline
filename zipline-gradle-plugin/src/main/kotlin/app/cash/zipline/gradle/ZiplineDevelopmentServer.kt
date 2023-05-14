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
package app.cash.zipline.gradle

import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlin.concurrent.schedule
import org.gradle.api.file.Directory
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.http4k.core.ContentType
import org.http4k.routing.ResourceLoader
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

/**
 * Serves .zipline and manifest files from a directory to a nearby ZiplineLoader. That loader may
 * subscribe to change notifications with a web socket, which will cause this loader to send a
 * 'reload' method whenever the manifest should be checked for an update.
 */
internal open class ZiplineDevelopmentServer @Inject constructor(
  private val inputDirectory: Directory,
  private val port: Int,
) : DeploymentHandle {
  private val websockets = CopyOnWriteArrayList<Websocket>()
  private var timer: Timer? = null
  private var server: Http4kServer? = null

  override fun isRunning() = server != null

  override fun start(deployment: Deployment) {
    val ws = websockets(
      "/ws" bind { ws: Websocket ->
        websockets.add(ws)
        ws.onClose {
          websockets.remove(ws)
        }
      },
    )

    val http = routes(
      "/" bind static(
        ResourceLoader.Directory(inputDirectory.asFile.absolutePath),
        Pair("zipline", ContentType.TEXT_PLAIN),
      ),
    )

    // Keep the connection open by sending a message periodically.
    timer = Timer("WebsocketHeartbeat", true).apply {
      schedule(0, 10000) {
        sendMessageToAllWebSockets(HEARTBEAT_MESSAGE)
      }
    }

    server = PolyHandler(http, ws).asServer(Jetty(port)).apply {
      start()
    }
  }

  @Suppress("unused") // Invoked reflectively by ZiplineServeTask.
  fun sendReloadToAllWebSockets() {
    sendMessageToAllWebSockets(RELOAD_MESSAGE)
  }

  private fun sendMessageToAllWebSockets(message: String) {
    websockets.forEach {
      it.send(WsMessage(message))
    }
  }

  override fun stop() {
    try {
      timer?.cancel()
      server?.stop()
    } finally {
      timer = null
      server = null
    }
  }

  companion object {
    const val HEARTBEAT_MESSAGE = "heartbeat"
    const val RELOAD_MESSAGE = "reload"
  }
}

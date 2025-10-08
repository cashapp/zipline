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

import java.io.File
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlin.concurrent.schedule
import org.eclipse.jetty.ee10.servlet.ResourceServlet
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.websocket.api.Callback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import org.gradle.api.file.Directory
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle

/**
 * Serves .zipline and manifest files from a directory to a nearby ZiplineLoader. That loader may
 * subscribe to change notifications with a web socket, which will cause this loader to send a
 * 'reload' method whenever the manifest should be checked for an update.
 */
internal open class ZiplineDevelopmentServer internal constructor(
  private val inputDirectory: File,
  private val port: Int,
) : DeploymentHandle {
  @Inject constructor(
    inputDirectory: Directory,
    port: Int,
  ) : this(inputDirectory.asFile, port)

  private val webSockets = CopyOnWriteArrayList<ZiplineWebSocket>()
  private var timer: Timer? = null
  private var server: Server? = null

  override fun isRunning() = server != null

  override fun start(deployment: Deployment) {
    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
      .apply {
        JettyWebSocketServletContainerInitializer.configure(this, null)

        // Offer a web socket for reload events.
        addServlet(
          ServletHolder(
            "ws",
            object : JettyWebSocketServlet() {
            public override fun configure(factory: JettyWebSocketServletFactory) {
              factory.addMapping("/ws") { _, _ ->
                ZiplineWebSocket().also { webSockets.add(it) }
              }
            }
          },
          ),
          "/ws",
        )

        // Serve .zipline bytecode and manifest JSON files at the file system root.
        addServlet(
          ServletHolder("default", ResourceServlet()).apply {
            setInitParameter("resourceBase", inputDirectory.absolutePath)
            setInitParameter("dirAllowed", "true")
            // Note that 'no-cache' is different from 'no-store'. It permits conditional requests.
            setInitParameter("cacheControl", "no-cache")
            setInitParameter("etags", "true")
          },
          "/*",
        )
      }

    // Keep the connection open by sending a message periodically.
    timer = Timer("WebsocketHeartbeat", true).apply {
      schedule(0, 10000) {
        sendMessageToAllWebSockets(HEARTBEAT_MESSAGE)
      }
    }

    server = Server(port).apply {
      handler = context
      start()
    }
  }

  @Suppress("unused") // Invoked reflectively by ZiplineServeTask.
  fun sendReloadToAllWebSockets() {
    sendMessageToAllWebSockets(RELOAD_MESSAGE)
  }

  private fun sendMessageToAllWebSockets(message: String) {
    for (webSocket in webSockets) {
      val session = webSocket.session ?: continue
      session.sendText(message, Callback.NOOP)
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

  @WebSocket
  internal inner class ZiplineWebSocket {
    var session: Session? = null

    @OnWebSocketClose
    fun onWebSocketClose(statusCode: Int, reason: String?) {
      session = null
      webSockets.remove(this)
    }

    @OnWebSocketOpen
    fun onWebSocketOpen(session: Session?) {
      this.session = session
    }
  }

  companion object {
    const val HEARTBEAT_MESSAGE = "heartbeat"
    const val RELOAD_MESSAGE = "reload"
  }
}

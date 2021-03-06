/*
 * Copyright (C) 2017 The Proteus Project
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

package com.proteus.peach.server

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.contrib.pattern.ClusterReceptionistExtension
import com.proteus.peach.server.PeachServer.Log
import com.proteus.peach.server.cache.ExternalServerCache
import com.proteus.peach.server.cache.MockupExternalServerCache
import com.proteus.peach.server.comm.AkkaPeachServerReceptor
import com.proteus.peach.server.config.PeachServerConfig
import com.proteus.peach.server.service.AbstractService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration

/**
 * Peach Server companion object.
 */
object PeachServer {
  /**
   * Logger instance.
   */
  val Log: Logger = LoggerFactory.getLogger("PeachServer")
}

/**
 * Server cache launcher.
 *
 * @param serverCache Instance of the server cache.
 * @param config      Configuration instance.
 */
class PeachServer(serverCache: ExternalServerCache = new MockupExternalServerCache(),
  config: PeachServerConfig = PeachServerConfig.DefaultConfig) extends AbstractService {

  /**
   * System actor.
   */
  private[server] val system = ActorSystem(config.serverName, PeachServerConfig.createAkkaConfig(config))

  /**
   * Init method.
   */
  def doInit(): Unit = {
    Log.info("Initializing the service...")
    this.serverCache.init()
    Log.info("Service has been initialized.")
  }

  /**
   * Shutdown method implementation.
   */
  override def shutdown(): Unit = {
    Log.info("Stopping service...")
    this.serverCache.stop()
    system.shutdown()
    Log.info("Service has been stopped.")
  }

  /**
   * Method that is called when the run method is called on the service.
   */
  override protected def doRun(): Unit = {
    Log.info("Running service..")
    val comm = system.actorOf(AkkaPeachServerReceptor.props(serverCache), "comm")
    ClusterReceptionistExtension(system).registerService(comm)
    Thread.sleep(Duration(1, TimeUnit.SECONDS).toMillis)
  }
}

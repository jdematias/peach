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

package com.proteus.peach.redis.manager

import java.util.{Map => JMap}
import java.util.concurrent.ConcurrentHashMap
import java.util.{List => JList}

import com.proteus.peach.common.util.JavaCollectionsConversions.scalaToJavaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.JedisConnectionException

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Redis session manager. For each session, a pool of available connections is maintained, each
 * thread accessing Redis must request and return the Jedis connection to the pool when it
 * finishes using it.
 */
object RedisSessionManager {


  /**
   * Pool maximum size.
   */
  private val PoolSize: Int = 64

  /**
   * Wait time to retrieve elements from the pool.
   */
  private val PoolWaitTime: Int = 10000

  /**
   * Class logger.
   */
  private val Log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  /**
   * Map of available pools indexed by session identifier.
   */
  private val Sessions: JMap[String, JedisPool] = new ConcurrentHashMap[String, JedisPool]()

  /**
   * Map of session configurations per identifier.
   */
  private val Configurations: JMap[String, BasicRedisSession] = new ConcurrentHashMap[String, BasicRedisSession]()



  /**
   * Initialize a session in redis. This method must be called before using the connection.
   *
   * @param redisSession The redis session.
   * @return Whether a session could be established with the redis host.
   */
  def init(redisSession: BasicRedisSession): Boolean = {
    if (this.Sessions.containsKey(redisSession.id)) {
      throw new UnsupportedOperationException(
        s"A pool for session ${redisSession.id} already exists")
    }
    val pool = this.connect(redisSession)
    if (pool.isDefined) {
      this.Configurations.put(redisSession.id, redisSession)
      this.Sessions.put(redisSession.id, pool.get)
    }
    pool.isDefined
  }

  /**
   * Establish a connection with redis.
   *
   * @param redisSession The redis session.
   * @return An option with a JedisPool.
   */
  private def connect(redisSession: BasicRedisSession): Option[JedisPool] = {
    val poolConfig = new JedisPoolConfig()
    poolConfig.setMaxWaitMillis(RedisSessionManager.PoolWaitTime)
    Log.debug("Maximum number of connections: ${poolConfig.getMaxTotal}")
    poolConfig.setMaxTotal(RedisSessionManager.PoolSize)
    poolConfig.setTestOnBorrow(true)
    poolConfig.setTestWhileIdle(true)
    val connectionPool = new JedisPool(
      poolConfig, redisSession.addresses.get(0), redisSession.port)
    Try {
      connectionPool.getResource
    } match {
      case Success(client) => {
        if (client.isConnected) {
          client.close()
          Some(connectionPool)
        } else {
          None
        }
      }
      case Failure(error)=> {
        Log.error(s"Cannot connect to redis ${redisSession}", error)
        None
      }
    }
  }


  /**
   * Method to reconnect.
   *
   * @param sessionId The session identifier.
   * @return An option with the Jedis client.
   */
  def reconnect(sessionId: String): Option[Jedis] = {
    if (this.Sessions.containsKey(sessionId)) {
      // close the pool
      val oldPool = this.Sessions.get(sessionId)
      if (!oldPool.isClosed) {
        oldPool.close()
      }
      val newPool = this.connect(this.Configurations.get(sessionId))

      this.Sessions.put(sessionId, newPool.get)
      Some(this.Sessions.get(sessionId).getResource)
    }
    None

  }

  /**
   * Shutdown all managed sessions.
   */
  def shutdown(): Unit = {
    this.Sessions.keySet().forEach((sessionId: String) => this.close(sessionId))
  }

  /**
   * Close the connections associated with a given session.
   *
   * @param sessionId The session identifier.
   */
  def close(sessionId: String): Unit = {
    if (this.Sessions.containsKey(sessionId)) {
      val pool: JedisPool = this.Sessions.get(sessionId)
      this.Sessions.remove(sessionId)
      pool.close()
    }
  }


  /**
   * Get a Jedis client that can be used to send data to redis. Notice that the client thread
   * must call jedis.close() in order for the client to be returned to the connection pool.
   *
   * @param sessionId The session identifier.
   * @return An option with the Jedis client.
   */
  def getSession(sessionId: String): Option[Jedis] = {
    if (this.Sessions.containsKey(sessionId)) {
      Option(this.Sessions.get(sessionId).getResource)
    } else {
      None
    }
  }

  /**
   * Get the redis session used to identify a session.
   *
   * @param sessionId The session identifier.
   * @return A basic redis session information.
   */
  def getConfiguration(sessionId: String): Option[BasicRedisSession] = {
    if (this.Configurations.containsKey(sessionId)) {
      Option(this.Configurations.get(sessionId))
    } else {
      None
    }
  }
}

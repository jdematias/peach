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

package com.proteus.peach.redis.server

import com.proteus.peach.redis.manager.AbstractRedisManagerIT
import com.proteus.peach.server.cache.ExternalCacheValidator
import com.proteus.peach.server.cache.ExternalServerCache
import org.junit.AfterClass
import org.junit.BeforeClass

object RedisExternalServerCacheIT {
  /**
   * Cache server.
   */
  lazy val CacheServer: ExternalServerCache = new RedisExternalServerCache(AbstractRedisManagerIT.Session)

  /**
   * Init cache server.
   */
  @BeforeClass
  def beforeAll(): Unit = {
    CacheServer.init()
  }

  /**
   * Stop cache server.
   */
  @AfterClass
  def afterAll(): Unit = {
    CacheServer.stop()
  }
}

class RedisExternalServerCacheIT extends AbstractRedisManagerIT with ExternalCacheValidator {
  /**
   * Cache server instance.
   */
  override val cacheServer: ExternalServerCache = RedisExternalServerCacheIT.CacheServer
}

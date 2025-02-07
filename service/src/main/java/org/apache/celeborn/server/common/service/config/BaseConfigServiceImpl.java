/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.celeborn.server.common.service.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.celeborn.common.CelebornConf;
import org.apache.celeborn.common.util.ThreadUtils;

public abstract class BaseConfigServiceImpl implements ConfigService {
  private static final Logger LOG = LoggerFactory.getLogger(BaseConfigServiceImpl.class);

  protected final CelebornConf celebornConf;
  protected final AtomicReference<SystemConfig> systemConfigAtomicReference =
      new AtomicReference<>();
  protected final AtomicReference<Map<String, TenantConfig>> tenantConfigAtomicReference =
      new AtomicReference<>(new HashMap<>());

  private final ScheduledExecutorService configRefreshService =
      ThreadUtils.newDaemonSingleThreadScheduledExecutor("celeborn-config-refresher");

  public BaseConfigServiceImpl(CelebornConf celebornConf) throws IOException {
    this.celebornConf = celebornConf;
    this.systemConfigAtomicReference.set(new SystemConfig(celebornConf));
    this.refreshAllCache();
    boolean dynamicConfigEnabled = celebornConf.dynamicConfigEnabled();
    if (dynamicConfigEnabled) {
      LOG.info("Celeborn dynamic config is enabled.");
      long dynamicConfigRefreshInterval = celebornConf.dynamicConfigRefreshInterval();
      this.configRefreshService.scheduleWithFixedDelay(
          () -> {
            try {
              refreshAllCache();
            } catch (Throwable e) {
              LOG.error("Refresh config encounter exception: {}", e.getMessage(), e);
            }
          },
          dynamicConfigRefreshInterval,
          dynamicConfigRefreshInterval,
          TimeUnit.MILLISECONDS);
    } else {
      LOG.info("Celeborn dynamic config is disabled, config can not be refreshed after updated.");
    }
  }

  @Override
  public CelebornConf getCelebornConf() {
    return celebornConf;
  }

  @Override
  public SystemConfig getSystemConfigFromCache() {
    return systemConfigAtomicReference.get();
  }

  @Override
  public TenantConfig getRawTenantConfigFromCache(String tenantId) {
    return tenantConfigAtomicReference.get().get(tenantId);
  }

  @Override
  public void shutdown() {
    ThreadUtils.shutdown(configRefreshService);
  }
}

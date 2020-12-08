/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.crypto.keys.KeyWrapper;
import org.eblocker.crypto.keys.SystemKey;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.JedisDataSource;
import redis.clients.jedis.JedisPool;

import java.io.IOException;

/**
 * Module for dependency injection of common base classes.
 */
public class BaseModule extends ConfigurableModule {
    public BaseModule() throws IOException {
        super();
    }

    @Override
    protected void configure() {
        super.configure();
        bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class).in(Scopes.SINGLETON);
        bind(JedisPool.class).toProvider(JedisPoolProvider.class).in(Scopes.SINGLETON);
        bind(DataSource.class).to(JedisDataSource.class);
    }

    @Provides
    @Named("systemKey")
    @Singleton
    public KeyWrapper provideSystemKeyWrapper(@Named("keyService.systemKey.path") String systemKeyPath) {
        return new SystemKey(systemKeyPath);
    }

}

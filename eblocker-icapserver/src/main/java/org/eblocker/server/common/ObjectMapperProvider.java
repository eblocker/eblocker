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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;
import org.eblocker.crypto.json.JsonEncryptionModule;
import org.eblocker.crypto.keys.KeyWrapper;
import org.eblocker.server.common.data.CloakedUserAgentModule;
import org.eblocker.server.common.data.IpAddressModule;

public class ObjectMapperProvider implements Provider<ObjectMapper> {

    private KeyWrapper systemKey;

    @Inject
    public ObjectMapperProvider(@Named("systemKey") KeyWrapper systemKey) {
        this.systemKey = systemKey;
    }

    @Override
    public ObjectMapper get() {
        try {
            CryptoService cryptoService = CryptoServiceFactory.getInstance().setKey(systemKey.get()).build();

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new IpAddressModule());
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.registerModule(new JsonEncryptionModule(objectMapper, cryptoService));
            objectMapper.registerModule(new CloakedUserAgentModule());

            return objectMapper;
        } catch (CryptoException e) {
            throw new IllegalStateException("failed to initialize crypto service for object mapper", e);
        }
    }
}

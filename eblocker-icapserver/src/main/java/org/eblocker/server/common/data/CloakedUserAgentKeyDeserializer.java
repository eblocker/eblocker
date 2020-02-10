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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CloakedUserAgentKeyDeserializer  extends KeyDeserializer {
    private static final Logger log = LoggerFactory.getLogger(CloakedUserAgentKeyDeserializer.class);
    
    @Override
    public CloakedUserAgentKey deserializeKey(String s, DeserializationContext deserializationContext) throws IOException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(s);
            String deviceId = node.get("deviceId").asText();
            Integer userId = Integer.valueOf(node.get("userId").asText());
            Boolean isCustom = node.get("isCustom").booleanValue();
            return new CloakedUserAgentKey(deviceId, userId, isCustom);
        } catch (IOException e) {
            log.error("Error while deserializing. ", e);
        }
        return null;
    }
}

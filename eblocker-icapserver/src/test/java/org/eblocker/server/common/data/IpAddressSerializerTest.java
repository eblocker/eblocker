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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IpAddressSerializerTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(IpAddress.class, new IpAddressSerializer());
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(module);
    }

    @Test
    public void testIpv4Serialization() throws JsonProcessingException {
        IpAddress ip4 = Ip4Address.parse("192.168.3.3");
        String serialized = objectMapper.writeValueAsString(ip4);
        Assert.assertEquals("\"" + ip4.toString() + "\"", serialized);
    }

    @Test
    public void testIpv6Serialization() throws JsonProcessingException {
        IpAddress ip6 = Ip6Address.parse("2a00:1450:4005:80b::200e");
        String serialized = objectMapper.writeValueAsString(ip6);
        Assert.assertEquals("\"" + ip6.toString() + "\"", serialized);
    }

}

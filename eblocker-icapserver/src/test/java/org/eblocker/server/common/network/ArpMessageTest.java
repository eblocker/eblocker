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
package org.eblocker.server.common.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArpMessageTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void parseRequest() throws ArpMessageParsingException {
        ArpMessage message = ArpMessage.parse("1/abcdef012345/192.168.0.1/ffeeddccbbaa/192.168.0.101");
        assertEquals(ArpMessageType.ARP_REQUEST, message.type);
        assertEquals("abcdef012345", message.sourceHardwareAddress);
        assertEquals("192.168.0.1", message.sourceIPAddress);
        assertEquals("ffeeddccbbaa", message.targetHardwareAddress);
        assertEquals("192.168.0.101", message.targetIPAddress);
    }

    @Test
    public void parseResponse() throws ArpMessageParsingException {
        ArpMessage message = ArpMessage.parse("2/abcdef012345/192.168.0.1/ffeeddccbbaa/192.168.0.101");
        assertEquals(ArpMessageType.ARP_RESPONSE, message.type);
    }

    @Test(expected = ArpMessageParsingException.class)
    public void invalidCode() throws ArpMessageParsingException {
        ArpMessage.parse("3/abcdef012345/192.168.0.1/ffeeddccbbaa/192.168.0.101");
    }

    @Test(expected = ArpMessageParsingException.class)
    public void notEnoughData() throws ArpMessageParsingException {
        ArpMessage.parse("3/abcdef012345/192.168.0.1/ffeeddccbbaa");
    }

    @Test
    public void format() throws ArpMessageParsingException {
        String msg = "1/abcdef012345/192.168.0.1/ffeeddccbbaa/192.168.0.101";
        assertEquals(msg, ArpMessage.parse(msg).format());
    }

    @Test
    public void gratuitousRequests() throws ArpMessageParsingException {
        assertTrue(ArpMessage.parse("1/2837374293bf/192.168.3.153/000000000000/192.168.3.153").isGratuitousRequest());
        assertTrue(ArpMessage.parse("1/9675026310a2/192.168.3.157/ffffffffffff/192.168.3.157").isGratuitousRequest());

        assertFalse(ArpMessage.parse("2/2837374293bf/192.168.3.153/000000000000/192.168.3.153").isGratuitousRequest());
        assertFalse(ArpMessage.parse("1/2837374293bf/0.0.0.0/000000000000/192.168.3.1").isGratuitousRequest());
        assertFalse(ArpMessage.parse("1/2837374293bf/0.0.0.0/000000000000/0.0.0.0").isGratuitousRequest());
    }

    @Test
    public void arpProbes() throws ArpMessageParsingException {
        assertTrue(ArpMessage.parse("1/2837374293bf/0.0.0.0/000000000000/192.168.3.153").isArpProbe());

        assertFalse(ArpMessage.parse("1/9675026310a2/0.0.0.0/ffffffffffff/192.168.3.157").isArpProbe());
        assertFalse(ArpMessage.parse("2/2837374293bf/192.168.3.153/000000000000/192.168.3.153").isArpProbe());
        assertFalse(ArpMessage.parse("1/9675026310a2/192.168.3.157/fffffffffffe/192.168.3.157").isArpProbe());
        assertFalse(ArpMessage.parse("1/2837374293bf/0.0.0.0/000000000000/0.0.0.0").isArpProbe());
    }
}

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
package org.eblocker.server.common.data.messagecenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.*;

public class MessageVisibilityTest {

    private static final Logger LOG = LoggerFactory.getLogger(MessageVisibilityTest.class);

    private final static int MESSAGE_ID = 4711;

    private final static String DEVICE_ID_1 = "device-1";
    private final static String DEVICE_ID_2 = "device-2";
    private final static String DEVICE_ID_3 = "device-3";
    private final static String DEVICE_ID_4 = "device-4";

    @Test
    public void test() {
        MessageVisibility messageVisibility = new MessageVisibility(MESSAGE_ID);

        // Initially visible for all devices
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_1));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_2));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_3));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_4));

        // Hide for device 2 and 3
        messageVisibility.hideForDevice(DEVICE_ID_2);
        messageVisibility.hideForDevice(DEVICE_ID_3);

        assertTrue(messageVisibility.isForDevice(DEVICE_ID_1));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_2));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_3));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_4));

        // Show again for device 3
        messageVisibility.showForDevice(DEVICE_ID_3);

        assertTrue(messageVisibility.isForDevice(DEVICE_ID_1));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_2));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_3));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_4));

        // Initially, do-not-show-again is false
        assertFalse(messageVisibility.isDoNotShowAgain());

        // Show never again
        messageVisibility.setDoNotShowAgain(true);

        assertTrue(messageVisibility.isDoNotShowAgain());
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_1));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_2));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_3));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_4));
    }

    @Test
    public void testJson() throws IOException {
        MessageVisibility messageVisibility = new MessageVisibility(MESSAGE_ID);
        messageVisibility.hideForDevice(DEVICE_ID_2);
        messageVisibility.hideForDevice(DEVICE_ID_3);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(messageVisibility);

        LOG.info("JSON {}", json);

        messageVisibility = objectMapper.readValue(json, MessageVisibility.class);

        assertEquals(MESSAGE_ID, messageVisibility.getMessageId());
        assertFalse(messageVisibility.isDoNotShowAgain());
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_1));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_2));
        assertFalse(messageVisibility.isForDevice(DEVICE_ID_3));
        assertTrue(messageVisibility.isForDevice(DEVICE_ID_4));
    }
}
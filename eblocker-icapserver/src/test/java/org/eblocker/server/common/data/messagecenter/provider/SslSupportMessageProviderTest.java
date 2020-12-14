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
package org.eblocker.server.common.data.messagecenter.provider;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class SslSupportMessageProviderTest {

    private static final String DEVICE_ID_1 = "device-1";
    private static final String DEVICE_ID_2 = "device-2";
    private static final String DEVICE_ID_3 = "device-3";
    private static final String DEVICE_ID_4 = "device-4";

    private static final Device DEVICE_1 = new Device();
    private static final Device DEVICE_2 = new Device();
    private static final Device DEVICE_3 = new Device();
    private static final Device DEVICE_4 = new Device();

    private static final List<Device> DEVICES = Arrays.asList(DEVICE_1, DEVICE_2, DEVICE_3, DEVICE_4);

    static {
        DEVICE_1.setId(DEVICE_ID_1);
        DEVICE_1.setSslEnabled(false);
        DEVICE_2.setId(DEVICE_ID_2);
        DEVICE_2.setSslEnabled(false);
        DEVICE_3.setId(DEVICE_ID_3);
        DEVICE_3.setSslEnabled(false);
        DEVICE_4.setId(DEVICE_ID_4);
        DEVICE_4.setSslEnabled(false);
    }

    @Mock
    private DeviceService deviceService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() {
        when(deviceService.getDevices(false)).thenReturn(DEVICES);

        SslSupportMessageProvider sslSupportMessageProvider = new SslSupportMessageProvider(deviceService);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // No device is has root CA installed, so far.
        //
        sslSupportMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId());

        // Should be visible for all devices
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_1));
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_2));
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_3));
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_4));

        //
        // Install root CA on devices 1 and 3
        //
        DEVICE_1.setSslEnabled(true);
        DEVICE_3.setSslEnabled(true);

        sslSupportMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId());

        // Should be visible for devices 2 and 4
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_1));
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_2));
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_3));
        assertTrue(messageContainer.getVisibility().isForDevice(DEVICE_ID_4));

        //
        // Install root CA on devices 2 and 4
        // Remove root certificate from device 1
        //
        DEVICE_1.setSslEnabled(false);
        DEVICE_2.setSslEnabled(true);
        DEVICE_4.setSslEnabled(true);

        sslSupportMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId());

        // Should not be visible (again) for device 1, because once turned off, stays off.
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_1));
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_2));
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_3));
        assertFalse(messageContainer.getVisibility().isForDevice(DEVICE_ID_4));

    }

}

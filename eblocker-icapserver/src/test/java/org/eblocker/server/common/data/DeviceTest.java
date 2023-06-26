/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

public class DeviceTest {
    @Test
    public void testGetHardwareAddress() {
        Device device = new Device();

        Assert.assertNull(device.getHardwareAddress());
        Assert.assertNull(device.getHardwareAddress(false));
        Assert.assertNull(device.getHardwareAddressPrefix());

        device.setId("device:abcdef012345");
        Assert.assertEquals("ab:cd:ef:01:23:45", device.getHardwareAddress());
        Assert.assertEquals("abcdef012345", device.getHardwareAddress(false));
        Assert.assertEquals("abcdef", device.getHardwareAddressPrefix());

        device.setId("Not a valid device ID");
        Assert.assertNull(device.getHardwareAddress());
        Assert.assertNull(device.getHardwareAddress(false));
        Assert.assertNull(device.getHardwareAddressPrefix());

        device.setId("device:1234");
        Assert.assertNull(device.getHardwareAddress());
        Assert.assertNull(device.getHardwareAddress(false));
        Assert.assertNull(device.getHardwareAddressPrefix());
    }
}
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
package org.eblocker.server.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicePropertiesTest {

    @Test
    void test1() {
        //given
        //when
        DeviceProperties deviceProperties = new DeviceProperties("classpath:device-sample-1.properties");

        //then
        assertTrue(deviceProperties.isSerialNumberAvailable());
        assertEquals("bein", deviceProperties.getArchitecture());
        assertEquals("Cherry Pi X7", deviceProperties.getBoard());
        assertEquals("Pyramid 2.1", deviceProperties.getCase());
        assertFalse(deviceProperties.isWifiAvailable());

        assertTrue(deviceProperties.isSerialNumberMatching("SN0123456789"));
        assertTrue(deviceProperties.isSerialNumberMatching("sn0123456789"));
        assertFalse(deviceProperties.isSerialNumberMatching("xx0123456789"));
        assertFalse(deviceProperties.isSerialNumberMatching("SN012345678"));
        assertFalse(deviceProperties.isSerialNumberMatching("SN01234567890"));
    }

    @Test
    void test2() {
        //given
        //when
        DeviceProperties deviceProperties = new DeviceProperties("classpath:device-sample-2.properties");

        //then
        assertFalse(deviceProperties.isSerialNumberAvailable());
        assertEquals("bein", deviceProperties.getArchitecture());
        assertEquals("Cherry Pi X7", deviceProperties.getBoard());
        assertEquals("Classic", deviceProperties.getCase());
        assertTrue(deviceProperties.isWifiAvailable());
    }

    @Test
    void noFile() {
        //given
        //when
        DeviceProperties deviceProperties = new DeviceProperties("classpath:not-existing-file");

        //then
        assertFalse(deviceProperties.isSerialNumberAvailable());
        assertEquals(DeviceProperties.DEVICE_PROP_ARCH_DEFAULT, deviceProperties.getArchitecture());
        assertEquals(DeviceProperties.DEVICE_PROP_BOARD_DEFAULT, deviceProperties.getBoard());
        assertEquals(DeviceProperties.DEVICE_PROP_CASE_DEFAULT, deviceProperties.getCase());
        assertEquals(DeviceProperties.DEVICE_PROP_SERIALNUMBER_PATTERN_DEFAULT, deviceProperties.getSerialNumberPattern());
        assertEquals(DeviceProperties.DEVICE_PROP_SERIALNUMBER_EXAMPLE_DEFAULT, deviceProperties.getSerialnumberExample());
        assertFalse(deviceProperties.isWifiAvailable());
        assertFalse(deviceProperties.isRgbLedAvailable());
    }

}

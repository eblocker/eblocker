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

import org.eblocker.server.common.data.Device.DisplayIconPosition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class IconSettingsTest {

    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {

    }

    @Test
    public void testGetDisplayIconMode() {
        // Test for each combination of flags
        IconSettings iconSettings = new IconSettings(false, false, false, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.OFF, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(false, false, true, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.OFF, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(false, true, false, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.OFF, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(false, true, true, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.OFF, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(true, false, false, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.ON_ALL_DEVICES, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(true, false, true, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.ON, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(true, true, false, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.FIVE_SECONDS, iconSettings.getDisplayIconMode());

        iconSettings = new IconSettings(true, true, true, DisplayIconPosition.LEFT);
        Assert.assertEquals(DisplayIconMode.FIVE_SECONDS_BROWSER_ONLY, iconSettings.getDisplayIconMode());
    }

    @Test
    public void testCreateFromDevice() {
        DisplayIconPosition iconPositionLeft = DisplayIconPosition.LEFT;
        DisplayIconPosition iconPositionRight = DisplayIconPosition.RIGHT;
        // Test each IconMode is correctly translated into the respective flags

        // IconMode.ON
        Device device = Mockito.mock(Device.class);
        Mockito.when(device.getIconMode()).thenReturn(DisplayIconMode.ON);
        Mockito.when(device.getIconPosition()).thenReturn(iconPositionRight);
        IconSettings iconSettings = new IconSettings(device);

        Assert.assertTrue(iconSettings.isEnabled());
        Assert.assertTrue(iconSettings.isBrowserOnly());
        Assert.assertFalse(iconSettings.isFiveSeconds());

        Assert.assertEquals(iconPositionRight, device.getIconPosition());

        // IconMode.ON_ALL_DEVICES
        device = Mockito.mock(Device.class);
        Mockito.when(device.getIconMode()).thenReturn(DisplayIconMode.ON_ALL_DEVICES);
        Mockito.when(device.getIconPosition()).thenReturn(iconPositionLeft);
        iconSettings = new IconSettings(device);

        Assert.assertTrue(iconSettings.isEnabled());
        Assert.assertFalse(iconSettings.isBrowserOnly());
        Assert.assertFalse(iconSettings.isFiveSeconds());

        Assert.assertEquals(iconPositionLeft, device.getIconPosition());

        // IconMode.FIVE_SECONDS
        device = Mockito.mock(Device.class);
        Mockito.when(device.getIconMode()).thenReturn(DisplayIconMode.FIVE_SECONDS);
        Mockito.when(device.getIconPosition()).thenReturn(iconPositionRight);
        iconSettings = new IconSettings(device);

        Assert.assertTrue(iconSettings.isEnabled());
        Assert.assertFalse(iconSettings.isBrowserOnly());
        Assert.assertTrue(iconSettings.isFiveSeconds());

        Assert.assertEquals(iconPositionRight, device.getIconPosition());

        // IconMode.FIVE_SECONDS_BROWSER_ONLY
        device = Mockito.mock(Device.class);
        Mockito.when(device.getIconMode()).thenReturn(DisplayIconMode.FIVE_SECONDS_BROWSER_ONLY);
        Mockito.when(device.getIconPosition()).thenReturn(iconPositionRight);
        iconSettings = new IconSettings(device);

        Assert.assertTrue(iconSettings.isEnabled());
        Assert.assertTrue(iconSettings.isBrowserOnly());
        Assert.assertTrue(iconSettings.isFiveSeconds());

        Assert.assertEquals(iconPositionRight, device.getIconPosition());

        // IconMode.OFF
        device = Mockito.mock(Device.class);
        Mockito.when(device.getIconMode()).thenReturn(DisplayIconMode.OFF);
        Mockito.when(device.getIconPosition()).thenReturn(iconPositionRight);
        iconSettings = new IconSettings(device);

        Assert.assertFalse(iconSettings.isEnabled());
        Assert.assertFalse(iconSettings.isBrowserOnly());
        Assert.assertFalse(iconSettings.isFiveSeconds());

        Assert.assertEquals(iconPositionRight, device.getIconPosition());
    }
}

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
package org.eblocker.server.common.util;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterMode;
import org.junit.Assert;
import org.junit.Test;

public class FilterModeUtilsTest {

    @Test
    public void getEffectiveFilterMode() {
        Assert.assertEquals(FilterMode.NONE, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.NONE, false)));
        Assert.assertEquals(FilterMode.NONE, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.NONE, true)));
        Assert.assertEquals(FilterMode.NONE, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.NONE, false)));
        Assert.assertEquals(FilterMode.NONE, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.NONE, true)));

        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.PLUG_AND_PLAY, false)));
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.PLUG_AND_PLAY, true)));
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.PLUG_AND_PLAY, false)));
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.PLUG_AND_PLAY, true)));

        Assert.assertEquals(FilterMode.ADVANCED, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.ADVANCED, false)));
        Assert.assertEquals(FilterMode.ADVANCED, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.ADVANCED, true)));
        Assert.assertEquals(FilterMode.ADVANCED, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.ADVANCED, false)));
        Assert.assertEquals(FilterMode.ADVANCED, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.ADVANCED, true)));

        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.AUTOMATIC, false)));
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(false, device(FilterMode.AUTOMATIC, true)));
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.AUTOMATIC, false)));
        Assert.assertEquals(FilterMode.ADVANCED, FilterModeUtils.getEffectiveFilterMode(true, device(FilterMode.ADVANCED, true)));
    }

    private Device device(FilterMode filterMode, boolean sslEnabled) {
        Device device = new Device();
        device.setFilterMode(filterMode);
        device.setSslEnabled(sslEnabled);
        return device;
    }
}

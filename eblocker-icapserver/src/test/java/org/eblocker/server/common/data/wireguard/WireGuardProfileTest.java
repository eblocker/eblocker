/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data.wireguard;

import org.eblocker.server.common.data.vpn.KeepAliveMode;
import org.eblocker.server.common.data.vpn.VpnLoginCredentials;
import org.eblocker.server.common.data.vpn.VpnProfile;
import org.junit.Assert;
import org.junit.Test;

public class WireGuardProfileTest {

    @Test
    public void implementsExistingVpnProfileContractWithoutLegacyCredentials() {
        WireGuardProfile profile = new WireGuardProfile(23, "Mullvad WireGuard");

        Assert.assertTrue(profile instanceof VpnProfile);
        Assert.assertEquals(Integer.valueOf(23), profile.getId());
        Assert.assertEquals("Mullvad WireGuard", profile.getName());
        Assert.assertTrue(profile.isNameServersEnabled());
        Assert.assertFalse(profile.isEnabled());
        Assert.assertFalse(profile.isTemporary());
        Assert.assertFalse(profile.isDeleted());
        Assert.assertEquals(KeepAliveMode.DISABLED, profile.getKeepAliveMode());
        Assert.assertNull(profile.getKeepAlivePingTarget());
        Assert.assertNull(profile.getLoginCredentials());
    }

    @Test
    public void storesEditableProfileMetadata() {
        WireGuardProfile profile = new WireGuardProfile(24, "initial");
        VpnLoginCredentials credentials = new VpnLoginCredentials();
        credentials.setUsername("unused");
        credentials.setPassword("unused");

        profile.setName("changed");
        profile.setDescription("description");
        profile.setEnabled(true);
        profile.setNameServersEnabled(false);
        profile.setTemporary(true);
        profile.setDeleted(true);
        profile.setKeepAliveMode(KeepAliveMode.CUSTOM);
        profile.setKeepAlivePingTarget("1.1.1.1");
        profile.setLoginCredentials(credentials);
        profile.setConfigurationFileVersion(1);

        Assert.assertEquals("changed", profile.getName());
        Assert.assertEquals("description", profile.getDescription());
        Assert.assertTrue(profile.isEnabled());
        Assert.assertFalse(profile.isNameServersEnabled());
        Assert.assertTrue(profile.isTemporary());
        Assert.assertTrue(profile.isDeleted());
        Assert.assertEquals(KeepAliveMode.CUSTOM, profile.getKeepAliveMode());
        Assert.assertEquals("1.1.1.1", profile.getKeepAlivePingTarget());
        Assert.assertSame(credentials, profile.getLoginCredentials());
        Assert.assertEquals(Integer.valueOf(1), profile.getConfigurationFileVersion());
    }

    @Test
    public void equalityUsesProfileId() {
        Assert.assertEquals(new WireGuardProfile(5, "a"), new WireGuardProfile(5, "b"));
        Assert.assertNotEquals(new WireGuardProfile(5, "a"), new WireGuardProfile(6, "a"));
    }
}

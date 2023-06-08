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
package org.eblocker.server.upnp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkAddressFactoryCustomizationTest {

    private NetworkAddressFactoryCustomization factoryCustomization;

    @Before
    public void setUp() {
        factoryCustomization = new NetworkAddressFactoryCustomization("eth0");
    }

    @Test
    public void isUsableAddress() throws UnknownHostException {
        Assert.assertTrue(factoryCustomization.isUsableAddress("eth0", InetAddress.getByName("10.10.10.10")));
        Assert.assertTrue(factoryCustomization.isUsableAddress("eth0", InetAddress.getByName("172.16.10.10")));
        Assert.assertTrue(factoryCustomization.isUsableAddress("eth0", InetAddress.getByName("192.168.10.10")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth0", InetAddress.getByName("8.8.8.8")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth0", InetAddress.getByName("169.254.93.109")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth1", InetAddress.getByName("10.10.10.10")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth1", InetAddress.getByName("172.16.10.10")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth1", InetAddress.getByName("192.168.10.10")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth1", InetAddress.getByName("8.8.8.8")));
        Assert.assertFalse(factoryCustomization.isUsableAddress("eth1", InetAddress.getByName("169.254.93.109")));
    }

    @Test
    public void isUsableNetworkInterface() {
        Assert.assertTrue(factoryCustomization.isUsableNetworkInterface("eth0"));
        Assert.assertFalse(factoryCustomization.isUsableNetworkInterface("eth1"));
        Assert.assertFalse(factoryCustomization.isUsableNetworkInterface("lo"));
        Assert.assertFalse(factoryCustomization.isUsableNetworkInterface("enp0s3"));
    }
}

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
package org.eblocker.server.common.network.unix;

import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.DhcpServerConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IscDhcpServerConfigurationTest {

    @Test
    public void enable() throws IOException {

        HashSet<Device> devices = new HashSet<>();
        devices.add(TestDeviceFactory.createDevice("e425e78b8602",
                "192.168.0.42", false));
        devices.add(TestDeviceFactory.createDevice("001122334455",
                "192.168.0.43", false));

        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.100", "192.168.0.200"));
        config.setNameServerPrimary("192.168.3.20");
        config.setNameServerSecondary("192.168.3.21");
        config.setDevices(devices);
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-extended.conf");
    }

    @Test
    public void minimumConfiguration() throws IOException {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.100", "192.168.0.200"));
        config.setDevices(new HashSet<Device>());
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-extended-minimum.conf");
    }

    @Test(expected = EblockerException.class)
    public void missingRange() throws IOException {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setDevices(new HashSet<Device>());
        IscDhcpServerConfiguration.format(config);
    }

    private void checkResult(String result, String referenceFile) throws IOException {
        String expected = IOUtils.toString(ClassLoader.getSystemResource(referenceFile));
        assertEquals(expected, result);
    }

    @Test
    public void fixedIPAddress() throws IOException {
        Device deviceStatic1 = new Device();
        deviceStatic1.setIpAddressFixed(true);
        deviceStatic1.setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.0.11")));
        deviceStatic1.setId("device:111111111111");

        Device deviceStatic2 = new Device();
        deviceStatic2.setIpAddressFixed(true);
        deviceStatic2.setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.5"), IpAddress.parse("192.168.0.6")));
        deviceStatic2.setId("device:111111111112");

        Device deviceStatic3 = new Device();
        deviceStatic3.setIpAddressFixed(true);
        deviceStatic3.setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.7"), IpAddress.parse("10.10.10.11")));
        deviceStatic3.setId("device:111111111113");

        Device deviceDynamic = new Device();
        deviceDynamic.setIpAddressFixed(false);
        deviceDynamic.setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.0.12")));
        deviceDynamic.setId("device:222222222222");

        Device deviceStaticOutsideSubnet = new Device();
        deviceStaticOutsideSubnet.setIpAddressFixed(true);
        deviceStaticOutsideSubnet.setIpAddresses(Collections.singletonList(IpAddress.parse("10.10.10.10")));
        deviceStaticOutsideSubnet.setId("device:333333333333");

        Set<Device> devices = new HashSet<>();
        devices.add(deviceDynamic);
        devices.add(deviceStatic1);
        devices.add(deviceStatic2);
        devices.add(deviceStatic3);
        devices.add(deviceStaticOutsideSubnet);

        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.100", "192.168.0.200"));
        config.setDevices(devices);

        String generatedConfig = IscDhcpServerConfiguration.format(config);

        checkResult(generatedConfig, "test-data/dhcpd-fixed-ip-addresses.conf");
    }

    @Test
    public void fixedIpAddressesAndDisabledDevices() throws IOException {
        HashSet<Device> devices = new HashSet<>();
        devices.add(TestDeviceFactory.createDevice("001122334455", "192.168.0.55", true, true));
        devices.add(TestDeviceFactory.createDevice("001122334456", "192.168.0.56", true, false));
        devices.add(TestDeviceFactory.createDevice("001122334457", "192.168.0.57", false, true));
        devices.add(TestDeviceFactory.createDevice("001122334458", "192.168.0.58", false, false));

        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.100", "192.168.0.200"));
        config.setNameServerPrimary("192.168.3.20");
        config.setNameServerSecondary("192.168.3.21");
        config.setDevices(devices);
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-fixed-and-disabled.conf");
    }

    @Test
    public void segmentedIpRange() throws IOException {
        HashSet<Device> devices = new HashSet<>();
        // One free address
        devices.add(TestDeviceFactory.createDevice("001122334455", "192.168.0.55", true, true));
        // No free address
        devices.add(TestDeviceFactory.createDevice("001122334456", "192.168.0.56", true, true));
        // One free address
        devices.add(TestDeviceFactory.createDevice("001122334458", "192.168.0.58", true, true));
        // Two free addresses
        devices.add(TestDeviceFactory.createDevice("001122334461", "192.168.0.61", true, true));
        // Three free addresses
        // 192.168.0.65 used by eBlocker
        // Five free addresses

        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.65");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.54", "192.168.0.70"));
        config.setNameServerPrimary("192.168.3.20");
        config.setNameServerSecondary("192.168.3.21");
        config.setDevices(devices);
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-segmented-ip-range.conf");
    }

    @Test
    public void testUserDefinedLeaseTime() throws IOException {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.54", "192.168.0.70"));
        config.setLeaseTime(1800);// 30 Minutes
        config.setDevices(new HashSet<Device>());
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-custom-lease-time-30-minutes.conf");
    }

    @Test
    public void testUserDefinedLeaseTimeTooShort() throws IOException {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.54", "192.168.0.70"));
        config.setLeaseTime(180);// 3 Minutes, will be corrected to 10
        config.setDevices(new HashSet<Device>());
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-custom-lease-time-3-minutes.conf");
    }

    @Test
    public void testUserDefinedLeaseTimeLongerThanMaxLease() throws IOException {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress("192.168.0.2");
        config.setNetmask("255.255.255.0");
        config.setGateway("192.168.0.1");
        config.setRange(new DhcpRange("192.168.0.54", "192.168.0.70"));
        config.setLeaseTime(14400);// 4 hours
        config.setDevices(new HashSet<Device>());
        String iscConfig = IscDhcpServerConfiguration.format(config);

        checkResult(iscConfig, "test-data/dhcpd-custom-lease-time-240-minutes.conf");
    }
}

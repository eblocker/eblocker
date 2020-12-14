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
package org.eblocker.server.common.squid.acl;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.service.DeviceService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SquidAclsTest {

    private Path aclPath;
    private List<Device> devices;
    private DeviceService deviceService;
    private SquidAclModule module;

    @Before
    public void setUp() throws IOException {
        aclPath = Files.createTempFile("squidaclmodule", "acl");
        Files.deleteIfExists(aclPath);

        devices = new ArrayList<>();
        for (int i = 0; i < 16; ++i) {
            devices.add(createMockDevice(String.format("device:%012x", i),
                (i & 1) != 0,
                (i & 2) != 0,
                (i & 4) != 0,
                (i & 8) != 0,
                "10.10.10." + (10 + i)));
        }

        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDevices(false)).thenReturn(devices);

        module = new SquidAclModule();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(aclPath);
    }

    @Test
    public void initialAclsCleared() throws IOException {
        devices.clear();
        SquidAcl acl = module.disabledClientsAcl(aclPath.toString(), deviceService);
        Assert.assertTrue(acl.update());
        assertContent();
    }

    @Test
    public void disabledClientsAcl() throws IOException {
        SquidAcl acl = module.disabledClientsAcl(aclPath.toString(), deviceService);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.10", "10.10.10.12", "10.10.10.14", "10.10.10.16", "10.10.10.18", "10.10.10.20", "10.10.10.22", "10.10.10.24");

        Assert.assertFalse(acl.update());
        assertContent("10.10.10.10", "10.10.10.12", "10.10.10.14", "10.10.10.16", "10.10.10.18", "10.10.10.20", "10.10.10.22", "10.10.10.24");

        devices.get(0).setEnabled(true);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.12", "10.10.10.14", "10.10.10.16", "10.10.10.18", "10.10.10.20", "10.10.10.22", "10.10.10.24");
    }

    @Test
    public void sslClientsAcl() throws IOException {
        SquidAcl acl = module.sslClientsAcl(aclPath.toString(), deviceService);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.13", "10.10.10.17", "10.10.10.21", "10.10.10.25");

        Assert.assertFalse(acl.update());
        assertContent("10.10.10.13", "10.10.10.17", "10.10.10.21", "10.10.10.25");

        devices.get(1).setSslEnabled(true);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.11", "10.10.10.13", "10.10.10.17", "10.10.10.21", "10.10.10.25");
    }

    @Test
    public void torClientsAcl() throws IOException {
        SquidAcl acl = module.torClientsAcl(aclPath.toString(), deviceService);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.23", "10.10.10.25");
        Assert.assertFalse(acl.update());
        assertContent("10.10.10.23", "10.10.10.25");

        devices.get(0).setEnabled(true);
        devices.get(0).setUseAnonymizationService(true);
        devices.get(0).setRouteThroughTor(true);
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.10", "10.10.10.23", "10.10.10.25");
    }

    @Test
    public void filteredClientsAcl() throws IOException {
        testConfigurableAcl(module.filteredClientsAcl(aclPath.toString(), deviceService));
    }

    private void testConfigurableAcl(ConfigurableDeviceFilterAcl acl) throws IOException {
        acl.setDevices(devices.subList(0, 4));
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.10", "10.10.10.11", "10.10.10.12", "10.10.10.13");

        acl.setDevices(devices.subList(0, 4));
        Assert.assertFalse(acl.update());
        assertContent("10.10.10.10", "10.10.10.11", "10.10.10.12", "10.10.10.13");

        acl.setDevices(devices.subList(0, 1));
        Assert.assertTrue(acl.update());
        assertContent("10.10.10.10");
    }

    private Device createMockDevice(String id, boolean enabled, boolean sslEnabled, boolean useAnonymizationService, boolean routeThroughTor, String... ipAddresses) {
        Device device = new Device();
        device.setId(id);
        device.setEnabled(enabled);
        device.setSslEnabled(sslEnabled);
        device.setUseAnonymizationService(useAnonymizationService);
        device.setRouteThroughTor(routeThroughTor);
        device.setIpAddresses(Stream.of(ipAddresses).map(IpAddress::parse).collect(Collectors.toList()));
        return device;
    }

    private void assertContent(String... ipAddresses) throws IOException {
        List<String> lines = Files.readAllLines(aclPath);
        Assert.assertEquals(ipAddresses.length, lines.size());
        for (String ipAddress : ipAddresses) {
            Assert.assertTrue(lines.contains(ipAddress));
        }
    }
}

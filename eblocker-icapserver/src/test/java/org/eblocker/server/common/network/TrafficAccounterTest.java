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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TrafficAccount;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TrafficAccounterTest {
    private final String DUMP_IP_TABLES_RULES_SCRIPT = "dump";
    private DataSource dataSource;
    private DeviceService deviceService;
    private ScriptRunner scriptRunner;
    private TrafficAccounter accounter;
    private IpTablesMock ipTablesMock;

    @Before
    public void setup() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        accounter = new TrafficAccounter(DUMP_IP_TABLES_RULES_SCRIPT, dataSource, deviceService, scriptRunner);
        ipTablesMock = new IpTablesMock();
    }

    @Test
    public void testAccounting() throws IOException, InterruptedException {
        // init service
        accounter.init();

        // setup mocks
        Mockito.when(scriptRunner.runScript(Mockito.eq(DUMP_IP_TABLES_RULES_SCRIPT), Mockito.anyString())).then(ipTablesMock::writeDump);
        Device a = createDevice("device:1866da46397c", "10.10.10.99");
        Device b = createDevice("device:080027d9c1d0", "10.10.10.101");
        Mockito.when(deviceService.getDeviceByIp(a.getIpAddresses().get(0))).thenReturn(a);
        Mockito.when(deviceService.getDeviceByIp(b.getIpAddresses().get(0))).thenReturn(b);

        // setup some traffic and run accounting
        ipTablesMock.setDevice(a, 1, 2, 4, 8);
        ipTablesMock.setDevice(b, 16, 32, 64, 128);
        accounter.update();

        // check accounting has been correct
        assertAccount(accounter.getTrafficAccount(a), 1, 2, 4, 8);
        assertAccount(accounter.getTrafficAccount(b), 16, 32, 64, 128);

        // add some traffic and run accounting again
        ipTablesMock.setDevice(a, 2, 4, 8, 16);
        ipTablesMock.setDevice(b, 32, 64, 128, 256);
        accounter.update();

        // checking traffic has been added
        assertAccount(accounter.getTrafficAccount(a), 2, 4, 8, 16);
        assertAccount(accounter.getTrafficAccount(b), 32, 64, 128, 256);
    }

    @Test
    public void testDeviceReset() throws IOException, InterruptedException {
        // init service
        accounter.init();

        // setup mocks
        Mockito.when(scriptRunner.runScript(Mockito.eq(DUMP_IP_TABLES_RULES_SCRIPT), Mockito.anyString())).then(ipTablesMock::writeDump);
        Device a = createDevice("device:1866da46397c", "10.10.10.99");
        Mockito.when(deviceService.getDeviceByIp(a.getIpAddresses().get(0))).thenReturn(a);

        // setup some traffic and run accounting
        ipTablesMock.setDevice(a, 1, 2, 4, 8);
        accounter.update();

        // check accounting has been correct
        assertAccount(accounter.getTrafficAccount(a), 1, 2, 4, 8);

        // run update again and check accounting is unchanged as no new activity has been captured
        accounter.update();
        assertAccount(accounter.getTrafficAccount(a), 1, 2, 4, 8);

        // disable b and run again, accounting has to be unchanged
        ipTablesMock.removeDevice(a);
        accounter.update();
        assertAccount(accounter.getTrafficAccount(a), 1, 2, 4, 8);

        // re-enable device and run update again, this time traffic has to be counted from zero again
        ipTablesMock.setDevice(a, 1, 2, 4, 8);
        accounter.update();
        assertAccount(accounter.getTrafficAccount(a), 2, 4, 8, 16);
    }

    private void assertAccount(TrafficAccount account, long packetsIn, long bytesIn, long packetsOut, long bytesOut) {
        Assert.assertEquals(packetsIn, account.getPacketsIn());
        Assert.assertEquals(bytesIn, account.getBytesIn());
        Assert.assertEquals(packetsOut, account.getPacketsOut());
        Assert.assertEquals(bytesOut, account.getBytesOut());
    }

    private Device createDevice(String id, String ip) {
        Device device = new Device();
        device.setId(id);
        if (ip != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ip)));
        }
        return device;
    }

    private class IpTablesMock {
        private Map<Device, long[]> devices = new LinkedHashMap<>();

        public void setDevice(Device device, long packetsIn, long bytesIn, long packetsOut, long bytesOut) {
            devices.put(device, new long[]{ packetsIn, bytesIn, packetsOut, bytesOut });
        }

        public void removeDevice(Device device) {
            devices.remove(device);
        }

        public int writeDump(InvocationOnMock im) throws FileNotFoundException {
            try (PrintWriter writer = new PrintWriter((String) im.getArgument(1))) {
                devices.forEach((k, v) -> {
                    writer.format("[%d:%d] -A ACCOUNT-IN -s %s/32 -i eth0 -j RETURN\n", v[0], v[1], k.getIpAddresses().get(0));
                    writer.format("[%d:%d] -A ACCOUNT-OUT -d %s/32 -o eth0 -j RETURN\n", v[2], v[3], k.getIpAddresses().get(0));
                });
                writer.flush();
            }
            return 0;
        }

        ;
    }

}

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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DhcpClientLeaseReaderTest {

    @Test
    public void testParsing() {
        DhcpClientLeaseReader reader = new DhcpClientLeaseReader("classpath:test-data/dhclient.leases");
        DhcpClientLease lease = reader.readLease();

        Assertions.assertEquals("eth0", lease.getInterfaceName());
        Assertions.assertEquals("10.10.10.100", lease.getFixedAddress());
        Assertions.assertNotNull(lease.getOptions());
        Assertions.assertEquals(6, lease.getOptions().size());

        Assertions.assertEquals("255.255.255.0", lease.getOptions().get("subnet-mask"));
        Assertions.assertEquals("10.10.10.10", lease.getOptions().get("routers"));
        Assertions.assertEquals("4294967295", lease.getOptions().get("dhcp-lease-time"));
        Assertions.assertEquals("5", lease.getOptions().get("dhcp-message-type"));
        Assertions.assertEquals("10.10.10.10,192.168.3.20", lease.getOptions().get("domain-name-servers"));
    }

    @Test
    public void testNoLeases() {
        DhcpClientLeaseReader reader = new DhcpClientLeaseReader("classpath:test-data/dhclient-noLeases.leases");
        Assertions.assertNull(reader.readLease());
    }

    @Test
    public void testLeasesFileDoesNotExist() {
        DhcpClientLeaseReader reader = new DhcpClientLeaseReader("classpath:test-data/dhclient-notFound.leases");
        Assertions.assertNull(reader.readLease());
    }
}

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

import org.junit.Assert;
import org.junit.Test;

public class DhcpClientLeaseReaderTest {

    @Test
    public void testParsing() {
        DhcpClientLeaseReader reader = new DhcpClientLeaseReader("classpath:test-data/dhclient.leases");
        DhcpClientLease lease = reader.readLease();

        Assert.assertEquals("eth0", lease.getInterfaceName());
        Assert.assertEquals("10.10.10.100", lease.getFixedAddress());
        Assert.assertNotNull(lease.getOptions());
        Assert.assertEquals(6, lease.getOptions().size());

        Assert.assertEquals("255.255.255.0", lease.getOptions().get("subnet-mask"));
        Assert.assertEquals("10.10.10.10", lease.getOptions().get("routers"));
        Assert.assertEquals("4294967295", lease.getOptions().get("dhcp-lease-time"));
        Assert.assertEquals("5", lease.getOptions().get("dhcp-message-type"));
        Assert.assertEquals("10.10.10.10,192.168.3.20", lease.getOptions().get("domain-name-servers"));
    }

}
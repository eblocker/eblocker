/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.openvpn;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class Ip6GatewaySelectorTest {
    @Test
    public void noGateway() {
        Assert.assertTrue(Ip6GatewaySelector.selectPublicGateway(List.of(), List.of()).isEmpty());
    }

    @Test
    public void selectGateway() {
        List<String> gateways = List.of("2abc:cafe:3:4711::5", "2abc:cafe:3:4711::4", "2abc:cafe:3:4711::3", "2abc:cafe:3:4711::2", "2abc:cafe:3:4711::1");
        List<String> networks = List.of( "::/3", "2000::/4", "3000::/4", "fc00::/7", "2000::/3");
        Assert.assertEquals("2abc:cafe:3:4711::1", Ip6GatewaySelector.selectPublicGateway(gateways, networks).get());
    }

    @Test
    public void selectOnlyGateway() {
        List<String> gateways = List.of("2abc:cafe:3:4711::1");
        List<String> networks = List.of();
        Assert.assertEquals("2abc:cafe:3:4711::1", Ip6GatewaySelector.selectPublicGateway(gateways, networks).get());
    }

    @Test
    public void invalidInput() {
        List<String> gateways = List.of("a", "b");
        List<String> networks = List.of("2000::", "3000::", "4000::");
        Assert.assertTrue(Ip6GatewaySelector.selectPublicGateway(gateways, networks).isEmpty());
    }

    @Test(expected = NumberFormatException.class)
    public void invalidNetwork() {
        List<String> gateways = List.of("a", "b");
        List<String> networks = List.of("2000::/a", "3000::/b");
        Ip6GatewaySelector.selectPublicGateway(gateways, networks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidAddress() {
        List<String> gateways = List.of("a", "b");
        List<String> networks = List.of("1::2::3/4", "1::2::3/5");
        Ip6GatewaySelector.selectPublicGateway(gateways, networks);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void notEnoughGateways() {
        List<String> gateways = List.of("2abc:cafe:3:4711::2", "2abc:cafe:3:4711::1");
        List<String> networks = List.of( "::/3", "2000::/4", "3000::/4", "fc00::/7", "2000::/3");
        Ip6GatewaySelector.selectPublicGateway(gateways, networks);
    }
}
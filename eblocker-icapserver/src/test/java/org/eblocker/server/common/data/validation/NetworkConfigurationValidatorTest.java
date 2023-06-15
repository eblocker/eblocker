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
package org.eblocker.server.common.data.validation;

import com.strategicgains.syntaxe.ValidationEngine;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class NetworkConfigurationValidatorTest {
    NetworkConfiguration configuration;
    private static final String errorPrefix = "error.network.";

    @Before
    public void setUp() {
        configuration = new NetworkConfiguration();
    }

    @Test
    public void testRequiredFields() {
        configuration.setAutomatic(true);

        assertTrue(configurationIsValid());

        configuration.setAutomatic(false);
        assertTrue(configurationHasError("ipAddress.required"));

        configuration.setIpAddress("1921.68.0.2");
        assertTrue(configurationHasError("ipAddress.invalid"));

        configuration.setIpAddress("192.168.0.2");
        assertTrue(configurationHasError("networkMask.required"));

        configuration.setNetworkMask("255.0.255.255");
        assertTrue(configurationHasError("networkMask.invalid"));

        configuration.setNetworkMask("255.255.255.0");
        assertTrue(configurationHasError("gateway.required"));

        configuration.setGateway("fritz.box");
        assertTrue(configurationHasError("gateway.invalid"));

        configuration.setGateway("192.168.3.1");
        assertTrue(configurationHasError("gateway.wrongNetwork"));
        assertTrue(configurationHasError("ipAddress.wrongNetwork"));

        configuration.setGateway("192.168.0.1");
        assertTrue(configurationIsValid());

    }

    @Test
    public void testOptionalFields() {
        configuration.setAutomatic(false);
        configuration.setIpAddress("192.168.0.2");
        configuration.setNetworkMask("255.255.255.0");
        configuration.setGateway("192.168.0.1");
        assertTrue(configurationIsValid());

        configuration.setNameServerPrimary("192.168.0.256");
        assertTrue(configurationHasError("nameServerPrimary.invalid"));

        configuration.setNameServerPrimary("192.168.0.20");
        assertTrue(configurationIsValid());

        configuration.setNameServerSecondary("ns1.google.com");
        assertTrue(configurationHasError("nameServerSecondary.invalid"));

        configuration.setNameServerSecondary("192.168.0.21");
        assertTrue(configurationIsValid());

        configuration.setDhcp(true);
        configuration.setDhcpRangeFirst("1.-1.1.-1");
        assertTrue(configurationHasError("dhcpRangeFirst.invalid"));

        configuration.setDhcpRangeFirst("192.168.30.100");
        assertTrue(configurationHasError("ipAddress.wrongNetwork"));
        assertTrue(configurationHasError("dhcpRangeFirst.wrongNetwork"));

        configuration.setDhcpRangeFirst("192.168.0.100");
        assertTrue(configurationIsValid());

        configuration.setDhcpRangeLast("ab.168.0.100");
        assertTrue(configurationHasError("dhcpRangeLast.invalid"));

        configuration.setDhcpRangeLast("192.168.3.200");
        assertTrue(configurationHasError("ipAddress.wrongNetwork"));
        assertTrue(configurationHasError("dhcpRangeLast.wrongNetwork"));

        configuration.setDhcpRangeLast("192.168.0.200");
        assertTrue(configurationIsValid());

        configuration.setDhcpRangeFirst("192.168.0.202");
        assertTrue(configurationHasError("dhcp.invalidRange"));

        configuration.setDhcpRangeFirst("192.168.0.50");
        assertTrue(configurationIsValid());

        configuration.setDhcpRangeFirst("192.168.1.50");
        assertTrue(configurationHasError("dhcp.wrongNetwork"));
    }

    @Test
    public void testDHCPRange() {
        configuration.setDhcp(true);
        configuration.setGateway("192.168.0.1");
        configuration.setIpAddress("192.168.0.10");
        configuration.setNetworkMask("255.255.255.0");
        configuration.setDhcpRangeFirst("192.168.0.100");
        configuration.setDhcpRangeLast("192.168.0.200");
        assertTrue(NetworkUtils.isBeforeAddress(configuration.getDhcpRangeFirst(), configuration.getDhcpRangeLast()));
        assertTrue(configurationIsValid());

        configuration.setDhcpRangeFirst("192.168.0.240");
        assertTrue(!NetworkUtils.isBeforeAddress(configuration.getDhcpRangeFirst(), configuration.getDhcpRangeLast()));
        assertTrue(configurationHasError("dhcp.invalidRange"));

        configuration.setDhcpRangeFirst("192.168.0.100");
        assertTrue(configurationIsValid());

        configuration.setDhcpRangeLast("192.168.4.200");
        assertTrue(configurationHasError("dhcp.wrongNetwork"));

        configuration.setDhcpRangeLast("192.168.0.50");
        assertTrue(configurationHasError("dhcp.invalidRange"));

        configuration.setDhcpRangeLast("192.168.0.200");
        assertTrue(configurationIsValid());

        configuration.setNetworkMask("255.255.0.0");
        configuration.setDhcpRangeFirst("192.168.5.197");
        configuration.setDhcpRangeLast("192.168.3.200");
        assertTrue(configurationHasError("dhcp.invalidRange"));
    }

    @Test
    public void testIp6NameServers() {
        configuration.setAutomatic(false);
        configuration.setIpAddress("192.168.0.2");
        configuration.setNetworkMask("255.255.255.0");
        configuration.setGateway("192.168.0.1");

        configuration.setNameServerPrimary("2001:1234::1111");
        configuration.setNameServerPrimary("2001:1234::2222");

        assertTrue(configurationIsValid());
    }

    private boolean configurationHasError(String string) {
        List<String> errors = ValidationEngine.validate(configuration);
        for (String error : errors) {
            if (error.equals(errorPrefix + string)) {
                return true;
            }
        }
        return false;
    }

    private boolean configurationIsValid() {
        List<String> errors = ValidationEngine.validate(configuration);
        return errors.size() == 0;
    }

}

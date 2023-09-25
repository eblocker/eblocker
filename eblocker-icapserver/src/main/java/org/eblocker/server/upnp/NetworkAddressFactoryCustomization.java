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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.EblockerModule;
import org.eblocker.server.common.network.NetworkUtils;
import org.eblocker.server.common.util.Ip4Utils;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Class for holding NetworkAddressFactory customizations. See {@link EblockerModule#provideUpnpService(NetworkAddressFactoryCustomization)} for rationale.
 */
@Singleton
public class NetworkAddressFactoryCustomization {

    private final String networkInterfaceName;
    private final List<int[]> privateNetworkIpNetmasks;

    @Inject
    public NetworkAddressFactoryCustomization(@Named("network.interface.name") String networkInterfaceName) {
        this.networkInterfaceName = networkInterfaceName;
        this.privateNetworkIpNetmasks = Arrays.asList(
                Ip4Utils.convertIpRangeToIpNetmask(NetworkUtils.privateClassA),
                Ip4Utils.convertIpRangeToIpNetmask(NetworkUtils.privateClassB),
                Ip4Utils.convertIpRangeToIpNetmask(NetworkUtils.privateClassC));
    }

    public boolean isUsableAddress(String networkInterfaceName, InetAddress address) {
        if (!this.networkInterfaceName.equals(networkInterfaceName)) {
            return false;
        }

        int ip = Ip4Utils.convertBytesToIp(address.getAddress());
        return privateNetworkIpNetmasks.stream().anyMatch(ipNetmask -> Ip4Utils.isInSubnet(ip, ipNetmask[0], ipNetmask[1]));
    }

    public boolean isUsableNetworkInterface(String networkInterfaceName) {
        return this.networkInterfaceName.equals(networkInterfaceName);
    }

}

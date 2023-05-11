/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.icmpv6;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;

import java.util.Collections;
import java.util.List;

@Singleton
public class RouterAdvertisementFactory {
    private final int routerLifetime;
    private final int dnsLifetime;
    private final NetworkInterfaceWrapper networkInterface;
    private final RouterAdvertisement.RouterPreference routerPreference;

    @Inject
    public RouterAdvertisementFactory(@Named("network.ip6.ra.router.preference") RouterAdvertisement.RouterPreference routerPreference,
                                      @Named("network.ip6.ra.router.lifetime") int routerLifetime,
                                      @Named("network.ip6.ra.dns.lifetime") int dnsLifetime,
                                      NetworkInterfaceWrapper networkInterface) {
        this.routerLifetime = routerLifetime;
        this.dnsLifetime = dnsLifetime;
        this.networkInterface = networkInterface;
        this.routerPreference = routerPreference;
    }

    public RouterAdvertisement create(byte[] sourceHardwareAddress,
                                      Ip6Address sourceAddress,
                                      byte[] targetHardwareAddress,
                                      Ip6Address targetAddress) {
        List<Option> options = List.of(
                new SourceLinkLayerAddressOption(networkInterface.getHardwareAddress()),
                new RecursiveDnsServerOption(dnsLifetime, Collections.singletonList(networkInterface.getIp6LinkLocalAddress())),
                new MtuOption(networkInterface.getMtu())
        );
        return new RouterAdvertisement(sourceHardwareAddress, sourceAddress, targetHardwareAddress, targetAddress,
                (short) 255, false, false, false,
                routerPreference, routerLifetime, 0, 0, options);
    }
}

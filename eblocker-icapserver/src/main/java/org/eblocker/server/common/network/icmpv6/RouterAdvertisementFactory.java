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
import org.eblocker.server.common.network.RouterAdvertisementCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Singleton
public class RouterAdvertisementFactory {
    public static final byte[] MULTICAST_ALL_NODES_HW_ADDRESS = new byte[]{ 51, 51, 0, 0, 0, 1 };

    private static final Logger log = LoggerFactory.getLogger(RouterAdvertisementFactory.class);
    private final int MINIMUM_MTU_IP6 = 1280;
    private final int routerLifetime;
    private final int dnsLifetime;
    private final NetworkInterfaceWrapper networkInterface;
    private final RouterAdvertisement.RouterPreference routerPreference;
    private final RouterAdvertisementCache routerAdvertisementCache;
    private int mtu;

    @Inject
    public RouterAdvertisementFactory(@Named("network.ip6.ra.router.preference") RouterAdvertisement.RouterPreference routerPreference,
                                      @Named("network.ip6.ra.router.lifetime") int routerLifetime,
                                      @Named("network.ip6.ra.dns.lifetime") int dnsLifetime,
                                      RouterAdvertisementCache routerAdvertisementCache,
                                      NetworkInterfaceWrapper networkInterface) {
        this.routerLifetime = routerLifetime;
        this.dnsLifetime = dnsLifetime;
        this.networkInterface = networkInterface;
        this.mtu = networkInterface.getMtu();
        this.routerPreference = routerPreference;
        this.routerAdvertisementCache = routerAdvertisementCache;
        this.routerAdvertisementCache.addListener(this::onCacheUpdate);
    }

    private void onCacheUpdate(List<RouterAdvertisementCache.Entry> entries) {
        Optional<Long> minMtu = entries.stream()
                .flatMap(entry -> entry.getAdvertisement().getOptions().stream())
                .filter(opt -> opt instanceof MtuOption)
                .map(opt -> (MtuOption)opt)
                .map(MtuOption::getMtu)
                .min(Comparator.naturalOrder());
        if (minMtu.isPresent()) {
            setMtu(minMtu.get().intValue());
        }
    }

    private void setMtu(int mtu) {
        if (mtu >= MINIMUM_MTU_IP6 && mtu <= networkInterface.getMtu()) {
            this.mtu = mtu;
        } else {
            log.warn("Ignoring MTU {}. Expected a value between {} and {}.", mtu, MINIMUM_MTU_IP6, networkInterface.getMtu());
        }
    }

    public RouterAdvertisement create(byte[] sourceHardwareAddress,
                                      Ip6Address sourceAddress,
                                      byte[] targetHardwareAddress,
                                      Ip6Address targetAddress) {
        List<Option> options = List.of(
                new SourceLinkLayerAddressOption(networkInterface.getHardwareAddress()),
                new RecursiveDnsServerOption(dnsLifetime, Collections.singletonList(networkInterface.getIp6LinkLocalAddress())),
                new MtuOption(mtu)
        );
        return new RouterAdvertisement(sourceHardwareAddress, sourceAddress, targetHardwareAddress, targetAddress,
                (short) 255, false, false, false,
                routerPreference, routerLifetime, 0, 0, options);
    }
}

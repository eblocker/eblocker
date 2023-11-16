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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.util.Ip6Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects that an IPv6 network prefix has changed and notifies listeners.
 */
@Singleton
@SubSystemService(SubSystem.HTTP_SERVER)
public class Ip6PrefixMonitor {
    private static final Logger log = LoggerFactory.getLogger(Ip6PrefixMonitor.class);

    private Set<String> currentPrefixes;
    private final NetworkInterfaceWrapper networkInterface;

    private final List<PrefixChangeListener> prefixChangeListeners = new ArrayList<>();

    @Inject
    Ip6PrefixMonitor(NetworkInterfaceWrapper networkInterface) {
        this.networkInterface = networkInterface;
    }

    @SubSystemInit
    public void init() {
        currentPrefixes = getPrefixes();
        log.debug("Loaded initial IPv6 prefixes: {}", currentPrefixes);
        networkInterface.addIpAddressChangeListener(this::updatePrefixes);
    }

    private void updatePrefixes(boolean ip4Updated, boolean ip6Updated) {
        if (!ip6Updated) {
            return;
        }
        Set<String> prefixes = getPrefixes();
        if (!currentPrefixes.equals(prefixes)) {
            log.debug("IPv6 prefixes have changed from {} to {}. Notifying listeners.", currentPrefixes, prefixes);
            currentPrefixes = prefixes;
            notifyPrefixChangeListeners();
        }
    }

    private Set<String> getPrefixes() {
        return networkInterface.getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .map(ip -> (Ip6Address) ip)
                .filter(ip -> !Ip6Utils.isLinkLocal(ip))
                .map(this::getCidrNetwork)
                .collect(Collectors.toSet());
    }

    private String getCidrNetwork(Ip6Address ip) {
        int prefixLen = networkInterface.getNetworkPrefixLength(ip);
        return Ip6Utils.getNetworkAddress(ip, prefixLen) + "/" + prefixLen;
    }

    public Set<String> getCurrentPrefixes() {
        return currentPrefixes;
    }

    public void addPrefixChangeListener(PrefixChangeListener listener) {
        prefixChangeListeners.add(listener);
    }

    private void notifyPrefixChangeListeners() {
        prefixChangeListeners.forEach(PrefixChangeListener::onPrefixChange);
    }

    public interface PrefixChangeListener {
        void onPrefixChange();
    }
}

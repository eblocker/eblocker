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
package org.eblocker.server.common.data.dns;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DnsServerConfig {
    private String defaultResolver;
    private Map<String, ResolverConfig> resolverConfigs;
    private Map<String, String> resolverConfigNameByIp;
    private List<LocalDnsRecord> localDnsRecords;
    private Set<String> filteredPeers;
    private Set<String> filteredPeersDefaultAllow;
    private String accessDeniedIp;
    private String vpnSubnetIp;
    private String vpnSubnetNetmask;

    public String getDefaultResolver() {
        return defaultResolver;
    }

    public void setDefaultResolver(String defaultResolver) {
        this.defaultResolver = defaultResolver;
    }

    public Map<String, ResolverConfig> getResolverConfigs() {
        return resolverConfigs;
    }

    public void setResolverConfigs(Map<String, ResolverConfig> resolverConfigs) {
        this.resolverConfigs = resolverConfigs;
    }

    public Map<String, String> getResolverConfigNameByIp() {
        return resolverConfigNameByIp;
    }

    public void setResolverConfigNameByIp(Map<String, String> resolverConfigNameByIp) {
        this.resolverConfigNameByIp = resolverConfigNameByIp;
    }

    public List<LocalDnsRecord> getLocalDnsRecords() {
        return localDnsRecords;
    }

    public void setLocalDnsRecords(List<LocalDnsRecord> localDnsRecords) {
        this.localDnsRecords = localDnsRecords;
    }

    public Set<String> getFilteredPeers() {
        return filteredPeers;
    }

    public void setFilteredPeers(Set<String> filteredPeers) {
        this.filteredPeers = filteredPeers;
    }

    public Set<String> getFilteredPeersDefaultAllow() {
        return filteredPeersDefaultAllow;
    }

    public void setFilteredPeerDefaultAllow(Set<String> filteredPeersDefaultAllow) {
        this.filteredPeersDefaultAllow = filteredPeersDefaultAllow;
    }

    public String getVpnSubnetIp() {
        return vpnSubnetIp;
    }

    public void setVpnSubnetIp(String vpnSubnetIp) {
        this.vpnSubnetIp = vpnSubnetIp;
    }

    public String getVpnSubnetNetmask() {
        return vpnSubnetNetmask;
    }

    public void setVpnSubnetNetmask(String vpnSubnetNetmask) {
        this.vpnSubnetNetmask = vpnSubnetNetmask;
    }

    /**
     * @deprecated Property is not used anymore and kept only for backward compatibility.
     */
    @Deprecated
    public String getAccessDeniedIp() {
        return accessDeniedIp;
    }

    /**
     * @deprecated Property is not used anymore and kept only for backward compatibility.
     */
    @Deprecated
    public void setAccessDeniedIp(String accessDeniedIp) {
        this.accessDeniedIp = accessDeniedIp;
    }
}

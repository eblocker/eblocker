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

import java.util.ArrayList;
import java.util.List;

public class DnsResolvers {
    private String defaultResolver;
    private String customResolverMode;
    private List<String> dhcpNameServers = new ArrayList<>();
    private List<String> customNameServers = new ArrayList<>();

    public String getDefaultResolver() {
        return defaultResolver;
    }

    public void setDefaultResolver(String defaultResolver) {
        this.defaultResolver = defaultResolver;
    }

    public List<String> getDhcpNameServers() {
        return dhcpNameServers;
    }

    public void setDhcpNameServers(List<String> dhcpNameServers) {
        this.dhcpNameServers = dhcpNameServers;
    }

    public String getCustomResolverMode() {
        return customResolverMode;
    }

    public void setCustomResolverMode(String customResolverMode) {
        this.customResolverMode = customResolverMode;
    }

    public List<String> getCustomNameServers() {
        return customNameServers;
    }

    public void setCustomNameServers(List<String> customNameServers) {
        this.customNameServers = customNameServers;
    }
}

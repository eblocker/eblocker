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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class NetworkIp6Configuration {
    private boolean routerAdvertisementsEnabled;
    private List<Ip6Address> localAddresses = List.of();
    private List<Ip6Address> globalAddresses = List.of();

    @JsonProperty
    public boolean isRouterAdvertisementsEnabled() {
        return routerAdvertisementsEnabled;
    }

    public void setRouterAdvertisementsEnabled(boolean routerAdvertisementsEnabled) {
        this.routerAdvertisementsEnabled = routerAdvertisementsEnabled;
    }

    @JsonProperty
    public List<Ip6Address> getLocalAddresses() {
        return localAddresses;
    }

    public void setLocalAddresses(List<Ip6Address> localAddresses) {
        this.localAddresses = localAddresses;
    }

    @JsonProperty
    public List<Ip6Address> getGlobalAddresses() {
        return globalAddresses;
    }

    public void setGlobalAddresses(List<Ip6Address> globalAddresses) {
        this.globalAddresses = globalAddresses;
    }
}

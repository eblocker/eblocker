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
package org.eblocker.server.http.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.UserProfileModule;

import java.util.List;

public class DeviceDTO {

    private final String id;

    private final List<String> ipAddresses;

    private final Integer assignedUser;

    private final Integer operatingUser;

    private final UserProfileModule effectiveUserProfile;

    private final FilterMode filterMode;

    private boolean filterPlugAndPlayAdsEnabled = true;

    private boolean filterPlugAndPlayTrackersEnabled = true;

    private boolean sslEnabled;

    @JsonCreator
    public DeviceDTO(
        @JsonProperty("id") String id,
        @JsonProperty("ipAddress") List<String> ipAddresses,
        @JsonProperty("assignedUser") Integer assignedUser,
        @JsonProperty("operatingUser") Integer operatingUser,
        @JsonProperty("effectiveUserProfile") UserProfileModule effectiveUserProfile,
        @JsonProperty("filterMode") FilterMode filterMode,
        @JsonProperty("filterPlugAndPlayAdsEnabled") Boolean filterPlugAndPlayAdsEnabled,
        @JsonProperty("filterPlugAndPlayTrackersEnabled") Boolean filterPlugAndPlayTrackersEnabled,
        @JsonProperty("sslEnabled") Boolean sslEnabled
    ) {
        this.id = id;
        this.ipAddresses = ipAddresses;
        this.assignedUser = assignedUser;
        this.operatingUser = operatingUser;
        this.effectiveUserProfile = effectiveUserProfile;
        this.filterMode = filterMode;
        this.filterPlugAndPlayAdsEnabled = filterPlugAndPlayAdsEnabled;
        this.filterPlugAndPlayTrackersEnabled = filterPlugAndPlayTrackersEnabled;
        this.sslEnabled = sslEnabled;

    }

    public String getId() {
        return id;
    }

    public List<String> getIpAddresses() {
        return ipAddresses;
    }

    public Integer getAssignedUser() {
        return assignedUser;
    }

    public Integer getOperatingUser() {
        return operatingUser;
    }

    public UserProfileModule getEffectiveUserProfile() {
        return effectiveUserProfile;
    }

    public FilterMode getFilterMode() {
        return this.filterMode;
    }

    public boolean getFilterPlugAndPlayAdsEnabled() {
        return this.filterPlugAndPlayAdsEnabled;
    }

    public boolean getFilterPlugAndPlayTrackersEnabled() {
        return this.filterPlugAndPlayTrackersEnabled;
    }

    public boolean isSslEnabled() {
        return this.sslEnabled;
    }
}

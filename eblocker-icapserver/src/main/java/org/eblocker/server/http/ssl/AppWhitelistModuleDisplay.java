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
package org.eblocker.server.http.ssl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.common.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppWhitelistModuleDisplay extends AppWhitelistModuleBase {

    private List<String> whitelistedDomainsIps;

    @JsonCreator
    public AppWhitelistModuleDisplay(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("description") Map<String, String> description,
            @JsonProperty("whitelistedDomainsIps") List<String> whitelistedDomainsIps,
            @JsonProperty("labels") Map<String, String> labels,
            @JsonProperty("enabledPerDefault") Boolean enabledPerDefault,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("builtin") Boolean builtin,
            @JsonProperty("modified") Boolean modified,
            @JsonProperty("version") String version,
            @JsonProperty("updatedVersionAvailable") Boolean updatedVersionAvailable,
            @JsonProperty("hidden") Boolean hidden
    ) {
        super(id, name, description, labels, enabledPerDefault, enabled, builtin, modified, version,
                updatedVersionAvailable, hidden);
        this.whitelistedDomainsIps = whitelistedDomainsIps == null ? Collections.emptyList() : whitelistedDomainsIps;
    }

    public AppWhitelistModuleDisplay(AppWhitelistModule module) {
        super(module);
        // Combine whitelisted domains and ips
        this.whitelistedDomainsIps = new ArrayList<>();
        this.whitelistedDomainsIps.addAll(module.getWhitelistedDomains());
        this.whitelistedDomainsIps.addAll(module.getWhitelistedIPs());
    }

    //Getters and setters------------------------------------

    public void setWhitelistedDomainsIps(List<String> whitelistedDomainsIps) {
        ArrayList<String> tmpWhitelistedDomainsIps = new ArrayList<>();
        for (String whitelistedDomainIp : whitelistedDomainsIps) {
            // Remove heading/trailing whitespaces
            String urlip = whitelistedDomainIp.trim();
            // Find out what it is
            if (IpUtils.isIPAddress(urlip)) {
                // IP address, not range, can be added
                tmpWhitelistedDomainsIps.add(urlip);
            } else if (IpUtils.isIpRange(urlip)) {
                // Make sure the range is not too big
                tmpWhitelistedDomainsIps.add(IpUtils.shrinkIpRange(urlip, IP_RANGE_RANGE_THRESHOLD));
            } else {
                // Is it a domain?
                String domain = UrlUtils.findDomainInString(urlip);
                if (domain != null) {
                    tmpWhitelistedDomainsIps.add(domain);
                }
            }
        }
        this.whitelistedDomainsIps = tmpWhitelistedDomainsIps;
    }

    public List<String> getWhitelistedDomainsIps() {
        return whitelistedDomainsIps;
    }

    //equality------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppWhitelistModuleDisplay module = (AppWhitelistModuleDisplay) o;
        return Objects.equals(name, module.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    //-----------------------

}

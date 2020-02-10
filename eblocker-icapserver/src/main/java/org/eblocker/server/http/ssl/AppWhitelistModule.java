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

import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.common.util.UrlUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppWhitelistModule extends AppWhitelistModuleBase {

    private List<String> whitelistedDomains;
    private List<String>  blacklistedDomains;
    private List<String> whitelistedIPs;

    @JsonCreator
    public AppWhitelistModule(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("description") Map<String, String> description,
            @JsonProperty("whitelistedDomains") List<String> whitelistedDomains,
            @JsonProperty("blacklistedDomains") List<String> blacklistedDomains,
            @JsonProperty("whitelistedIPs") List<String> whitelistedIPs,
            @JsonProperty("labels") Map<String, String> labels,
            @JsonProperty("enabledPerDefault") Boolean enabledPerDefault,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("builtin") Boolean builtin,
            @JsonProperty("modified") Boolean modified,
            @JsonProperty("version") String version,
            @JsonProperty("updatedVersionAvailable") Boolean updatedVersionAvailable,
            @JsonProperty("hidden") Boolean hidden
    ) {
        super(id, name, description, labels, enabledPerDefault, enabled, builtin, modified, version, updatedVersionAvailable, hidden);
        this.whitelistedDomains = whitelistedDomains == null ? Collections.emptyList() : whitelistedDomains;
        this.blacklistedDomains = blacklistedDomains == null ? Collections.emptyList() : blacklistedDomains;
        this.whitelistedIPs = whitelistedIPs == null ? Collections.emptyList() : whitelistedIPs;
    }

    public AppWhitelistModule(AppWhitelistModuleDisplay module) {
        super(module);
        // Sort domains and ips into their separate lists
        whitelistedDomains = new ArrayList<>();
        whitelistedIPs = new ArrayList<>();
        for (String domainIp : module.getWhitelistedDomainsIps()) {
            String tmpDomainIp = domainIp.trim();
            if (IpUtils.isIPAddress(tmpDomainIp)){
                this.whitelistedIPs.add(tmpDomainIp);
            } else if (IpUtils.isIpRange(tmpDomainIp)){
                this.whitelistedIPs.add(IpUtils.shrinkIpRange(tmpDomainIp, IP_RANGE_RANGE_THRESHOLD));
            }else{
                // Is it a domain?
                String domain = UrlUtils.findDomainInString(tmpDomainIp);
                if (domain!=null){
                    this.whitelistedDomains.add(domain);
                }
            }
        }

    }

    //Getters and setters------------------------------------

    public void setWhitelistedDomains(List<String> whitelistedDomains) {
        ArrayList<String> tmpWhitelistedDomains = new ArrayList<>();
        for (String whitelistedDomain : whitelistedDomains) {
            // Remove heading/trailing whitespaces
            String url = whitelistedDomain.trim();
            String domain = UrlUtils.findDomainInString(url);
            if (domain != null) {
                tmpWhitelistedDomains.add(domain);
            }
        }
        this.whitelistedDomains = tmpWhitelistedDomains;
    }

    public List<String> getWhitelistedDomains(){
        return whitelistedDomains;
    }

    public void setBlacklistedDomains(List<String> blacklistedDomains) {
        this.blacklistedDomains = blacklistedDomains;
    }

    public List<String> getBlacklistedDomains() {
        return blacklistedDomains;
    }

    public void setWhitelistedIPs(List<String> whitelistedIPs) {
        if (whitelistedIPs == null) {
            whitelistedIPs = new ArrayList<>();
        }
        // check each entry in the list to be either an ip-adress or -range (/8
        // or smaller)
        ArrayList<String> tmpWhitelistedIPs = new ArrayList<>();
        for (String whitelistedIP : whitelistedIPs) {
            // remove heading/trailing whitespaces
            String ip = whitelistedIP.trim();
            if (IpUtils.isIPAddress(ip)) {
                // ip address, not range, can be added
                tmpWhitelistedIPs.add(ip);
            } else if (IpUtils.isIpRange(ip)) {
                // make sure the range is not too big
                tmpWhitelistedIPs.add(IpUtils.shrinkIpRange(ip, IP_RANGE_RANGE_THRESHOLD));
            }
        }
        this.whitelistedIPs = tmpWhitelistedIPs;
    }

    public List<String> getWhitelistedIPs() {
        return whitelistedIPs;
    }

}

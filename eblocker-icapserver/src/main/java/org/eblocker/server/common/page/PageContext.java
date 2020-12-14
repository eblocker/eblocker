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
package org.eblocker.server.common.page;

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.WhiteListConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PageContext {
    private final String id;
    private final String url;
    private final IpAddress ipAddress;

    private PageContext parentContext;
    private Set<String> blockedAdsSet;
    private Set<String> blockedTrackingsSet;
    private WhiteListConfig whiteListConfig;

    public PageContext(PageContext parentContext, String url, IpAddress ipAddress) {
        this.id = UUID.randomUUID().toString();
        this.parentContext = parentContext;
        this.url = url;
        this.ipAddress = ipAddress;
        this.blockedAdsSet = new HashSet<>();
        this.blockedTrackingsSet = new HashSet<>();

        reset();
    }

    public void reset() {
        blockedAdsSet.clear();
        blockedTrackingsSet.clear();
        whiteListConfig = WhiteListConfig.noWhiteListing();
    }

    public String getId() {
        return id;
    }

    public String getShortId() {
        return id.substring(0, 8);
    }

    public PageContext getParentContext() {
        return parentContext;
    }

    public void setParentContext(PageContext parentContext) {
        this.parentContext = parentContext;
    }

    public String getUrl() {
        return url;
    }

    public IpAddress getIpAddress() {
        return this.ipAddress;
    }

    /**
     * Add a new blocked ad url.
     *
     * @param url blocked ad url
     */
    public void incrementBlockedAds(String url) {
        blockedAdsSet.add(url);
    }

    /**
     * Add a new blocked tracking url.
     *
     * @param url blocked tracking url
     */
    public void incrementBlockedTrackings(String url) {
        blockedTrackingsSet.add(url);
    }

    public int getBlockedAds() {
        return blockedAdsSet.size();
    }

    public Set<String> getBlockedAdsSet() {
        return blockedAdsSet;
    }

    public Set<String> getBlockedTrackingsSet() {
        return blockedTrackingsSet;
    }

    public int getBlockedTrackings() {
        return blockedTrackingsSet.size();
    }

    public WhiteListConfig getWhiteListConfig() {
        if (parentContext != null) {
            return parentContext.getWhiteListConfig();
        }
        return whiteListConfig;
    }

    public void setWhiteListConfig(WhiteListConfig whiteListConfig) {
        this.whiteListConfig = whiteListConfig;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PageContext other = (PageContext) obj;
        return this.id.equals(other.id);
    }

    @Override
    public String toString() {
        return "PageContext[" + id + "/" + url + "]";
    }
}

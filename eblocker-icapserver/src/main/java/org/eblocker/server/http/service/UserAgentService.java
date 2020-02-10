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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.*;
import org.eblocker.server.common.session.UserAgentInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.CloakedUserAgentConfig;
import org.eblocker.server.common.data.CloakedUserAgentKey;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;

import java.util.Collection;
import java.util.regex.Pattern;

@Singleton
public class UserAgentService {

    private final DataSource datasource;

    private final CloakedUserAgentConfig cloakedUserAgentConfig;

    private final Cache<String, UserAgentInfo> userAgentInfoCache;

    private final Pattern patternBrowser;
    private final Pattern patternMsie;

    @Inject
    public UserAgentService(
        DataSource datasource,
        @Named("useragent.cache.size") int size,
        @Named("useragent.regex.browser") String regexBrowser,
        @Named("useragent.regex.msie") String regexMsie
  ) {

        this.datasource = datasource;
        CloakedUserAgentConfig config = datasource.get(CloakedUserAgentConfig.class);
        this.cloakedUserAgentConfig =  config == null ? new CloakedUserAgentConfig() : config;
        userAgentInfoCache = CacheBuilder.newBuilder().maximumSize(size).build();
        patternBrowser = Pattern.compile(regexBrowser);
        patternMsie = Pattern.compile(regexMsie);
    }

    public void setCloakedUserAgent(Integer userId, String deviceId, String cloakedUserAgent, Boolean isCustom) {
        CloakedUserAgentKey key = new CloakedUserAgentKey(deviceId, userId, isCustom);

        cloakedUserAgentConfig.remove(key); // to also update the key within the map (isCustom may change)

        if (cloakedUserAgent != null) {
            cloakedUserAgentConfig.put(key, cloakedUserAgent);
        }
        datasource.save(cloakedUserAgentConfig);
    }

    public String getCloakedUserAgent(Integer userId, String deviceId) {
        CloakedUserAgentKey key = new CloakedUserAgentKey(deviceId, userId, null);
        return cloakedUserAgentConfig.get(key);
    }

    public boolean isCustom(Integer userId, String deviceId) {
        CloakedUserAgentKey key = new CloakedUserAgentKey(deviceId, userId, null);
        for (CloakedUserAgentKey each : cloakedUserAgentConfig.keySet()) {
            if (each.equals(key)) {
                return each.getCustom();
            }
        }
        return false;
    }

    protected void turnOffCloakingForDevice(Integer userId, String deviceId) {
        setCloakedUserAgent(userId, deviceId, null, null);
    }

    public void turnOffCloakingForAllDevices(Collection<Device> devices) {
        for (Device dev : devices) {
            turnOffCloakingForDevice(dev.getAssignedUser(), dev.getId());
        }
    }

    public UserAgentInfo getUserAgentInfo(String userAgent) {
        UserAgentInfo info = userAgentInfoCache.getIfPresent(userAgent);
        if (info != null) {
            return info;
        }
        if (!patternBrowser.matcher(userAgent).matches()) {
            info = UserAgentInfo.OTHER;
        } else if (patternMsie.matcher(userAgent).matches()) {
            info = UserAgentInfo.MSIE;
        } else {
            info = UserAgentInfo.OTHER_BROWSER;
        }
        userAgentInfoCache.put(userAgent, info);
        return info;
    }

}


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
package org.eblocker.server.common.data;

import java.io.Serializable;

public class CloakedUserAgentKey implements Serializable {

    private String deviceId;
    private Integer userId;
    private Boolean isCustom;

    public CloakedUserAgentKey(String deviceId, Integer userId, Boolean isCustom) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.isCustom = isCustom;
    }
    
    @Override
    public String toString() {
        return "{" +
            "\"deviceId\":\"" + this.deviceId + "\"," +
            "\"userId\":\"" + this.userId + "\"," +
            "\"isCustom\":" + this.isCustom +
            "}";
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Boolean getCustom() {
        return this.isCustom;
    }

    public void setCustom(Boolean custom) {
        this.isCustom = custom;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CloakedUserAgentKey)) {
            return false;
        }
        CloakedUserAgentKey other = (CloakedUserAgentKey)o;

        return other.getDeviceId().equals(this.deviceId)
            && other.getUserId().equals(this.userId);
    }

    @Override
    public int hashCode() {
        return this.userId *
            this.deviceId.hashCode();
    }
}

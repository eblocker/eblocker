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
package org.eblocker.server.common.data.openvpn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** VpnProfile implementation for OpenVPN Client instances
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenVpnProfile implements VpnProfile {

    private Integer id;
    private String name;
    private String description;
    private boolean enabled;
    private boolean nameServersEnabled = true;
    private boolean temporary;
    private boolean deleted;
    private KeepAliveMode keepAliveMode = KeepAliveMode.DISABLED;
    private String keepAlivePingTarget;
    private VpnLoginCredentials loginCredentials;
    private Integer configurationFileVersion;

    public OpenVpnProfile() {
    }

    public OpenVpnProfile(Integer id, String name) {
        this.id = id;
        this.name = name;
        this.loginCredentials = new VpnLoginCredentials();
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String desc) {
        this.description = desc;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isNameServersEnabled() {
        return nameServersEnabled;
    }

    @Override
    public void setNameServersEnabled(boolean nameServersEnabled) {
        this.nameServersEnabled = nameServersEnabled;
    }

    @Override
    public boolean isTemporary() {
        return temporary;
    }

    @Override
    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public KeepAliveMode getKeepAliveMode() {
        return keepAliveMode;
    }

    @Override
    public void setKeepAliveMode(KeepAliveMode keepAliveMode) {
        this.keepAliveMode = keepAliveMode;
    }

    @Override
    public String getKeepAlivePingTarget() {
        return keepAlivePingTarget;
    }

    @Override
    public void setKeepAlivePingTarget(String target) {
        this.keepAlivePingTarget = target;
    }

    @Override
    public VpnLoginCredentials getLoginCredentials() {
        return loginCredentials;
    }

    @Override
    public void setLoginCredentials(VpnLoginCredentials loginCredentials){
        this.loginCredentials = loginCredentials;
    }

    @Override
    public int hashCode(){
        return Integer.hashCode(id);
    }

    @Override
    public boolean equals(Object that){
        if (that instanceof OpenVpnProfile) {
            return this.id == ((OpenVpnProfile)that).id;
        }
        return false;
    }

    public Integer getConfigurationFileVersion() {
        return configurationFileVersion;
    }

    public void setConfigurationFileVersion(Integer configurationFileVersion) {
        this.configurationFileVersion = configurationFileVersion;
    }
}

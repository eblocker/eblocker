/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.backup;

import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;

import java.util.List;

/**
 * Stores data for one OpenVPN client profile.
 */
public class OpenVpnClientBackup {
    private OpenVpnProfile profile;
    private OpenVpnConfiguration configuration;
    private List<EncryptedContainer> externalFiles;
    private List<String> deviceIds;

    public OpenVpnProfile getProfile() {
        return profile;
    }

    public void setProfile(OpenVpnProfile profile) {
        this.profile = profile;
    }

    public OpenVpnConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpenVpnConfiguration configuration) {
        this.configuration = configuration;
    }

    public List<EncryptedContainer> getExternalFiles() {
        return externalFiles;
    }

    public void setExternalFiles(List<EncryptedContainer> externalFiles) {
        this.externalFiles = externalFiles;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }
}

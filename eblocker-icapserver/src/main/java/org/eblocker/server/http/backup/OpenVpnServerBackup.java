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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.crypto.json.JsonEncrypt;
import org.eblocker.server.common.data.openvpn.OpenVpnServerCaKeys;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;

import javax.annotation.Nullable;

public class OpenVpnServerBackup {
    private VpnServerStatus serverStatus;
    private OpenVpnServerCaKeys caKeys;
    private String sharedSecret;
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public VpnServerStatus getServerStatus() {
        return serverStatus;
    }

    public void setServerStatus(VpnServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @Nullable
    public OpenVpnServerCaKeys getCaKeys() {
        return caKeys;
    }

    public void setCaKeys(@Nullable OpenVpnServerCaKeys caKeys) {
        this.caKeys = caKeys;
    }

    @JsonProperty
    @JsonEncrypt
    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }
}

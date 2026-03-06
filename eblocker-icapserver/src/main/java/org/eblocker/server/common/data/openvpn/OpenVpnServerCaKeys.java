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
package org.eblocker.server.common.data.openvpn;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.crypto.json.JsonEncrypt;

import java.util.List;

/**
 * Used only for exporting and importing backups.
 */
public class OpenVpnServerCaKeys {
    private String caCert;
    private String caKey;
    private String crl;
    private String serverCert;
    private String serverKey;
    private List<OpenVpnServerDeviceKeys> deviceKeys;

    public String getCaCert() {
        return caCert;
    }

    public void setCaCert(String caCert) {
        this.caCert = caCert;
    }

    @JsonProperty
    @JsonEncrypt
    public String getCaKey() {
        return caKey;
    }

    public void setCaKey(String caKey) {
        this.caKey = caKey;
    }

    public String getCrl() {
        return crl;
    }

    public void setCrl(String crl) {
        this.crl = crl;
    }

    public String getServerCert() {
        return serverCert;
    }

    public void setServerCert(String serverCert) {
        this.serverCert = serverCert;
    }

    @JsonProperty
    @JsonEncrypt
    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }

    public List<OpenVpnServerDeviceKeys> getDeviceKeys() {
        return deviceKeys;
    }

    public void setDeviceKeys(List<OpenVpnServerDeviceKeys> deviceKeys) {
        this.deviceKeys = deviceKeys;
    }
}

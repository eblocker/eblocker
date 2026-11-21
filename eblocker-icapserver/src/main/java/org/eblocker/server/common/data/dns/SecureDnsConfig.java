/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data.dns;

import java.util.ArrayList;
import java.util.List;

public class SecureDnsConfig {
    private String transport;
    private String provider;
    private boolean validateDnssec;
    private List<String> plainServers = new ArrayList<>();
    private String plainMode = "default";
    private List<String> dotServers = new ArrayList<>();
    private List<String> dohServers = new ArrayList<>();
    private String dotMode = "default";
    private String dohMode = "default";

    public List<String> getPlainServers() { return plainServers; }
    public void setPlainServers(List<String> servers) { this.plainServers = servers; }
    public String getPlainMode() { return plainMode; }
    public void setPlainMode(String mode) { this.plainMode = mode; }
    public List<String> getDotServers() { return dotServers; }
    public void setDotServers(List<String> servers) { this.dotServers = servers; }
    public List<String> getDohServers() { return dohServers; }
    public void setDohServers(List<String> servers) { this.dohServers = servers; }
    public String getDotMode() { return dotMode; }
    public void setDotMode(String mode) { this.dotMode = mode; }
    public String getDohMode() { return dohMode; }
    public void setDohMode(String mode) { this.dohMode = mode; }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isValidateDnssec() {
        return validateDnssec;
    }

    public void setValidateDnssec(boolean validateDnssec) {
        this.validateDnssec = validateDnssec;
    }
}

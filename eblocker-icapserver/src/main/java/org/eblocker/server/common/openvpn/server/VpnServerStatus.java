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
package org.eblocker.server.common.openvpn.server;

import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;

public class VpnServerStatus {
    private boolean isRunning;
    private boolean isFirstStart;
    private String host;
    private Integer mappedPort;
    private ExternalAddressType externalAddressType;
    private PortForwardingMode portForwardingMode;

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
    }

    public boolean isFirstStart() {
        return isFirstStart;
    }

    public void setFirstStart(boolean firstStart) {
        this.isFirstStart = firstStart;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getMappedPort() {
        return mappedPort;
    }

    public void setMappedPort(Integer mappedPort) {
        this.mappedPort = mappedPort;
    }

    public ExternalAddressType getExternalAddressType() {
        return externalAddressType;
    }

    public void setExternalAddressType(ExternalAddressType externalAddressType) {
        this.externalAddressType = externalAddressType;
    }

    public PortForwardingMode getPortForwardingMode() {
        return portForwardingMode;
    }

    public void setPortForwardingMode(PortForwardingMode portForwardingMode) {
        this.portForwardingMode = portForwardingMode;
    }
}

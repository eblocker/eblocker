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
package org.eblocker.server.common.data.vpn;

/**
 * Generic VPN provider profile contract shared by WireGuard provider routing and UI/API code.
 */
public interface VpnProfile {
    Integer getId();
    String getName();
    void setName(String name);
    String getDescription();
    void setDescription(String desc);
    boolean isEnabled();
    void setEnabled(boolean enabled);
    boolean isNameServersEnabled();
    void setNameServersEnabled(boolean nameServersEnabled);
    boolean isTemporary();
    void setTemporary(boolean temporary);
    boolean isDeleted();
    void setDeleted(boolean deleted);
    KeepAliveMode getKeepAliveMode();
    void setKeepAliveMode(KeepAliveMode keepAliveMode);
    String getKeepAlivePingTarget();
    void setKeepAlivePingTarget(String target);
    VpnLoginCredentials getLoginCredentials();
    void setLoginCredentials(VpnLoginCredentials loginCredentials);
}

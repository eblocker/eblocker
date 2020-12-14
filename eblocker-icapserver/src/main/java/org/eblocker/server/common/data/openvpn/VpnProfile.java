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

/**
 * This interface can model VPNProfiles. It has quite a huge API to be quite general for future additional implementations of not only OpenVPN,
 * so you dont have to make use of all the available methods, just return a good default value or nullpointer when you dont need a certain method.
 */
public interface VpnProfile {

    /**
     * Get the unique ID of this VpnProfile (which will also be used as the marker for the IP packets in the iptables mangle table)
     *
     * @return
     */
    Integer getId();

    /**
     * Get the name of this VPN Profile
     *
     * @return
     */
    String getName();

    /**
     * Set the name
     *
     * @param name
     */
    void setName(String name);

    /**
     * Get the description for this VPN Profile
     *
     * @return
     */
    String getDescription();

    /**
     * Set the description of this VPN client profile (or the server)
     *
     * @param desc
     */
    void setDescription(String desc);

    /**
     * Get the client config file
     *
     * @return client config file if available, otherwise null
     * <p>
     * File getConfigFile();
     */

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

    /**
     * Get the login credentials for this VPN account (if needed)
     *
     * @return login credentials container, null if they are not needed
     */
    VpnLoginCredentials getLoginCredentials();

    /**
     * Set the login credentials for this VPN client instance
     *
     * @param loginCredentials
     */
    void setLoginCredentials(VpnLoginCredentials loginCredentials);
}

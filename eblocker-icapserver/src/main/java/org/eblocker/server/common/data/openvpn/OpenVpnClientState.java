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

import java.util.List;
import java.util.Set;

public class OpenVpnClientState {
    public enum State {INACTIVE, ACTIVE, PENDING_RESTART}

    private Integer id;
    private State state;
    private String virtualInterfaceName;
    private String linkLocalInterfaceAlias;
    private String linkLocalIpAddress;
    private Integer route;
    private String routeNetGateway;
    private String routeVpnGateway;
    private String trustedIp;
    private Set<String> devices;
    private List<String> nameServers;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getVirtualInterfaceName() {
        return virtualInterfaceName;
    }

    public void setVirtualInterfaceName(String virtualInterfaceName) {
        this.virtualInterfaceName = virtualInterfaceName;
    }

    public String getLinkLocalInterfaceAlias() {
        return linkLocalInterfaceAlias;
    }

    public void setLinkLocalInterfaceAlias(String linkLocalInterfaceAlias) {
        this.linkLocalInterfaceAlias = linkLocalInterfaceAlias;
    }

    public String getLinkLocalIpAddress() {
        return linkLocalIpAddress;
    }

    public void setLinkLocalIpAddress(String linkLocalIpAddress) {
        this.linkLocalIpAddress = linkLocalIpAddress;
    }

    public Integer getRoute() {
        return route;
    }

    public void setRoute(Integer route) {
        this.route = route;
    }

    public String getRouteNetGateway() {
        return routeNetGateway;
    }

    public void setRouteNetGateway(String routeNetGateway) {
        this.routeNetGateway = routeNetGateway;
    }

    public String getRouteVpnGateway() {
        return routeVpnGateway;
    }

    public void setRouteVpnGateway(String routeVpnGateway) {
        this.routeVpnGateway = routeVpnGateway;
    }

    public String getTrustedIp() {
        return trustedIp;
    }

    public void setTrustedIp(String trustedIp) {
        this.trustedIp = trustedIp;
    }

    public Set<String> getDevices() {
        return devices;
    }

    public void setDevices(Set<String> devices) {
        this.devices = devices;
    }

    public List<String> getNameServers() {
        return nameServers;
    }

    public void setNameServers(List<String> nameServers) {
        this.nameServers = nameServers;
    }
}

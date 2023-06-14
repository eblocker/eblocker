/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.SocketException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Proxy for {@link java.net.NetworkInterface}.
 *
 * This class is not final so it can be mocked in unit-tests.
 */
public class NetworkInterface {
    private java.net.NetworkInterface networkInterface;

    public NetworkInterface(java.net.NetworkInterface networkInterface) {
        this.networkInterface = networkInterface;
    }

    /**
     * See {@link java.net.NetworkInterface#inetAddresses()}
     */
    public Stream<InetAddress> inetAddresses() {
        return networkInterface.inetAddresses();
    }

    /**
     * See {@link java.net.NetworkInterface#subInterfaces()}
     */
    public Stream<NetworkInterface> subInterfaces() {
        return networkInterface.subInterfaces().map(subInterface -> new NetworkInterface(subInterface));
    }

    /**
     * See {@link java.net.NetworkInterface#getInterfaceAddresses()}
     */
    public List<InterfaceAddress> getInterfaceAddresses() {
        return networkInterface.getInterfaceAddresses();
    }

    /**
     * See {@link java.net.NetworkInterface#getHardwareAddress()}
     */
    public byte[] getHardwareAddress() throws SocketException {
        return networkInterface.getHardwareAddress();
    }

    /**
     * See {@link java.net.NetworkInterface#getName()}
     */
    public String getName() {
        return networkInterface.getName();
    }

    /**
     * See {@link java.net.NetworkInterface#isUp()}
     */
    public boolean isUp() throws SocketException {
        return networkInterface.isUp();
    }

    /**
     * See {@link java.net.NetworkInterface#getMTU()}
     */
    public int getMTU() throws SocketException {
        return networkInterface.getMTU();
    }
}

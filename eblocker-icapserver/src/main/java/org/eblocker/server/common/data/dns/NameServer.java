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
package org.eblocker.server.common.data.dns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.data.IpAddress;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameServer {

    private static final Pattern FULL_SPEC_PATTERN = Pattern.compile("(tcp|udp):(?:(\\d+\\.\\d+\\.\\d+\\.\\d+)|\\[([a-f0-9:]+)\\]):(\\d+)");

    public enum Protocol {UDP, TCP}

    private final Protocol protocol;
    private final IpAddress address;
    private final int port;

    @JsonCreator
    public NameServer(@JsonProperty("protocol") Protocol protocol,
                      @JsonProperty("address") IpAddress address,
                      @JsonProperty("port") int port) {
        this.protocol = protocol;
        this.address = address;
        this.port = port;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public IpAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        boolean includeProtocolAndPort = Protocol.TCP == protocol || port != 53;
        StringBuilder sb = new StringBuilder();
        if (includeProtocolAndPort) {
            sb.append(protocol.name().toLowerCase());
            sb.append(":");
            if (address.isIpv6()) {
                sb.append("[");
            }
        }
        sb.append(address);
        if (includeProtocolAndPort) {
            if (address.isIpv6()) {
                sb.append("]");
            }
            sb.append(":");
            sb.append(port);
        }
        return sb.toString();
    }

    public static NameServer parse(String input) {
        Matcher matcher = FULL_SPEC_PATTERN.matcher(input);
        if (matcher.matches()) {
            NameServer.Protocol protocol = "tcp".equals(matcher.group(1)) ? NameServer.Protocol.TCP : NameServer.Protocol.UDP;
            IpAddress address = matcher.group(2) != null ? IpAddress.parse(matcher.group(2)) : IpAddress.parse(matcher.group(3));
            return new NameServer(protocol, address, Integer.parseInt(matcher.group(4)));
        }
        return new NameServer(Protocol.UDP, IpAddress.parse(input), 53);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        NameServer that = (NameServer) o;

        if (port != that.port)
            return false;
        if (protocol != that.protocol)
            return false;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        int result = protocol.hashCode();
        result = 31 * result + address.hashCode();
        result = 31 * result + port;
        return result;
    }
}

/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.unix.firewall;

import java.util.Objects;

public class ActionDestinationNat extends Action {
    private String destinationIp;
    private Integer destinationPort;

    public ActionDestinationNat(String destinationIp, int destinationPort) {
        super("DNAT");
        this.destinationIp = destinationIp;
        this.destinationPort = destinationPort;
    }

    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" --to-destination ");
        if (destinationIp.contains(":")) {
            // IPv6 address must be written in brackets, so the port is not interpreted as part of the IP address:
            result.append("[");
            result.append(destinationIp);
            result.append("]");
        } else {
            result.append(destinationIp);
        }
        result.append(":");
        result.append(destinationPort);
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ActionDestinationNat that = (ActionDestinationNat) o;
        return Objects.equals(destinationIp, that.destinationIp) &&
                Objects.equals(destinationPort, that.destinationPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), destinationIp, destinationPort);
    }
}

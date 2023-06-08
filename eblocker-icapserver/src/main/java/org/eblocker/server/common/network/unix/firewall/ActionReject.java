/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

/**
 * Returns a packet that signals rejection of a connection attempt
 */
public class ActionReject extends Action {
    private RejectType type;

    public ActionReject(RejectType type) {
        super("REJECT");
        this.type = type;
    }

    @Override
    protected boolean protocolAllowed(Protocol protocol) {
        if (type == RejectType.TCP_RESET) {
            // require TCP
            return protocol == Protocol.TCP;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        if (type.label != null) {
            result.append(" --reject-with ");
            result.append(type.label);
        }
        return result.toString();
    }

    public enum RejectType {
        DEFAULT(null),
        TCP_RESET("tcp-reset"),
        ICMP6_PORT_UNREACHABLE("icmp6-port-unreachable");

        private final String label;

        RejectType(String label) {
            this.label = label;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ActionReject that = (ActionReject) o;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }
}

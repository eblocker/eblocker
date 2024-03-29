/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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
 * Redirects a packet to another port on the machine itself
 */
public class ActionRedirect extends Action {
    private Integer destinationPort;

    public ActionRedirect(int destinationPort) {
        super("REDIRECT");
        this.destinationPort = destinationPort;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" --to-ports ");
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
        ActionRedirect that = (ActionRedirect) o;
        return Objects.equals(destinationPort, that.destinationPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), destinationPort);
    }
}

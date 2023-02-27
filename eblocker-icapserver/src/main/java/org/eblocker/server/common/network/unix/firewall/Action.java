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

public class Action {
    private String chain;

    protected Action(String chain) {
        this.chain = chain;
    }

    public static Action returnFromChain() {
        return new Action("RETURN");
    }

    public static Action masquerade() {
        return new Action("MASQUERADE");
    }

    public static Action drop() {
        return new Action("DROP");
    }

    public static Action accept() {
        return new Action("ACCEPT");
    }

    public static Action reject() {
        return new Action("REJECT");
    }

    public static Action jumpToChain(String chain) {
        return new Action(chain);
    }

    public static Action redirectTo(String targetIp, int targetPort) {
        return new ActionDestinationNat(targetIp, targetPort);
    }

    public static Action redirectTo(int targetPort) {
        return new ActionRedirect(targetPort);
    }

    public static Action mark(int value) {
        return new ActionMark(value);
    }

    public String toString() {
        StringBuilder result = new StringBuilder("-j ");
        result.append(chain);
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Action action = (Action) o;
        return Objects.equals(chain, action.chain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chain);
    }
}

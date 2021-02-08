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

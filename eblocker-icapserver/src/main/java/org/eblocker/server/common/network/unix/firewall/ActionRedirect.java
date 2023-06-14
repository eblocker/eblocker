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

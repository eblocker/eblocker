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
        result.append(destinationIp);
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

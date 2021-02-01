package org.eblocker.server.common.network.unix.firewall;

import java.util.Objects;

public class ActionMark extends Action {
    private int value;

    public ActionMark(int value) {
        super("MARK");
        this.value = value;
    }

    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" --set-mark ");
        result.append(value);
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
        ActionMark that = (ActionMark) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }
}

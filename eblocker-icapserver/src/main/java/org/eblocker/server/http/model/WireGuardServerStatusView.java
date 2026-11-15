package org.eblocker.server.http.model;

/**
 * Combines persistent desired state with the observed WireGuard runtime state.
 */
public class WireGuardServerStatusView {

    private boolean enabled;
    private WireGuardStatus runtime;

    public WireGuardServerStatusView() {
    }

    public WireGuardServerStatusView(
            boolean enabled,
            WireGuardStatus runtime) {

        this.enabled = enabled;
        this.runtime = runtime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public WireGuardStatus getRuntime() {
        return runtime;
    }

    public void setRuntime(
            WireGuardStatus runtime) {

        this.runtime = runtime;
    }
}

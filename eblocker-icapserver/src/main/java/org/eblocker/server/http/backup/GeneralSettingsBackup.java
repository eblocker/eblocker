package org.eblocker.server.http.backup;

/**
 * Class for storing global settings in the backup.
 */
public class GeneralSettingsBackup {
    private long deviceScanningInterval;
    private boolean autoEnableNewDevices;
    private boolean squidWarningServiceEnabled;

    public long getDeviceScanningInterval() {
        return deviceScanningInterval;
    }

    public void setDeviceScanningInterval(long deviceScanningInterval) {
        this.deviceScanningInterval = deviceScanningInterval;
    }

    public boolean isAutoEnableNewDevices() {
        return autoEnableNewDevices;
    }

    public void setAutoEnableNewDevices(boolean autoEnableNewDevices) {
        this.autoEnableNewDevices = autoEnableNewDevices;
    }

    public boolean isSquidWarningServiceEnabled() {
        return squidWarningServiceEnabled;
    }

    public void setSquidWarningServiceEnabled(boolean squidWarningServiceEnabled) {
        this.squidWarningServiceEnabled = squidWarningServiceEnabled;
    }
}

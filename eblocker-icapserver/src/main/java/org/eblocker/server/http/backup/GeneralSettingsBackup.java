package org.eblocker.server.http.backup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.crypto.json.JsonEncrypt;
import org.eblocker.server.common.data.LocaleSettings;

import javax.annotation.Nullable;

/**
 * Class for storing global settings in the backup.
 */
public class GeneralSettingsBackup {
    private long deviceScanningInterval;
    private boolean autoEnableNewDevices;
    private boolean squidWarningServiceEnabled;
    private LocaleSettings localeSettings;
    private byte[] consolePasswordHash;

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

    public LocaleSettings getLocaleSettings() {
        return localeSettings;
    }

    public void setLocaleSettings(LocaleSettings localeSettings) {
        this.localeSettings = localeSettings;
    }

    @JsonProperty
    @JsonEncrypt
    @Nullable
    public byte[] getConsolePasswordHash() {
        return consolePasswordHash;
    }

    public void setConsolePasswordHash(@Nullable byte[] consolePasswordHash) {
        this.consolePasswordHash = consolePasswordHash;
    }
}

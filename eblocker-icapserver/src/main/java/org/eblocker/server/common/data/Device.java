/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a network device. A device has a unique MAC address and optionally
 * an IP address.
 */
public class Device extends ModelObject {

    public static final String ID_PREFIX = "device:";
    private static Pattern pattern = Pattern
            .compile(ID_PREFIX + "([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})");

    private List<IpAddress> ipAddresses = Collections.emptyList();
    private boolean enabled = true;
    private boolean paused = false;
    private boolean useAnonymizationService;
    private boolean routeThroughTor = false;
    private Integer useVPNProfileID;
    private FilterMode filterMode = FilterMode.AUTOMATIC;
    private boolean filterAdsEnabled = true;
    private boolean filterTrackersEnabled = true;
    private boolean malwareFilterEnabled = true;
    private boolean sslEnabled = false;
    private boolean sslRecordErrorsEnabled = true;
    private boolean domainRecordingEnabled = false;
    private boolean hasDownloadedRootCA = false;
    private DisplayIconMode iconMode = DisplayIconMode.getDefault();
    private DisplayIconPosition iconPosition = DisplayIconPosition.getDefault();
    private String name;// optional
    private String vendor;
    private boolean isCurrentDevice = false;// just use this boolean temporarily to avoid looping over all devices again
                                            // (does not have to be saved to redis)
    private boolean areDeviceMessagesSettingsDefault = true;
    @JsonProperty
    private boolean isOnline = false;
    private boolean isGateway = false;
    private boolean isEblocker = false;
    // About DHCP
    private boolean ipAddressFixed = true;
    // For parental control

    private Integer assignedUser;
    private Integer operatingUser;
    // Setting an invalid default value to ensure that the int value is never
    // undefined.
    // MUST be replaced with an ID of an existing user, before the device is used
    // anywhere.
    // Latest the next restart - UserService.init() - will generate and set a valid
    // default system user.
    private int defaultSystemUser = -1;

    private boolean isVpnClient = false;

    private boolean messageShowInfo = true;
    private boolean messageShowAlert = true;

    private boolean showPauseDialog = true;
    private boolean showPauseDialogDoNotShowAgain = true;
    private boolean showDnsFilterInfoDialog = true;
    private boolean showBookmarkDialog = true;
    private boolean showWelcomePage = true;

    private boolean controlBarAutoMode = true;
    private boolean mobileState = true;
    private boolean mobilePrivateNetworkAccess;
    private Instant lastSeen;
    private String lastSeenString = "";

    public Device() {
    }

    public enum DisplayIconPosition {
        LEFT,
        RIGHT;

        public static DisplayIconPosition getDefault() {
            return RIGHT;
        }
    }

    @JsonProperty
    /**
     * Extracts the MAC address from the device ID.
     * 
     * @return MAC address (in the range from 00:00:00:00:00:00 up to
     *         ff:ff:ff:ff:ff:ff)
     */
    public String getHardwareAddress() {
        return getHardwareAddress(true);
    }

    public String getHardwareAddress(boolean colonSeparated) {
        if (getId() == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(this.getId());
        if (matcher.matches()) {
            if (colonSeparated) {
                return String.format("%s:%s:%s:%s:%s:%s", matcher.group(1), matcher.group(2), matcher.group(3),
                        matcher.group(4), matcher.group(5), matcher.group(6));
            } else {
                return String.format("%s%s%s%s%s%s", matcher.group(1), matcher.group(2), matcher.group(3),
                        matcher.group(4), matcher.group(5), matcher.group(6));
            }
        } else {
            return null;
        }
    }

    public String getHardwareAddressPrefix() {
        if (getId() == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(this.getId());
        if (matcher.matches()) {
            return String.format("%s%s%s", matcher.group(1), matcher.group(2), matcher.group(3));
        } else {
            return null;
        }
    }

    public List<IpAddress> getIpAddresses() {
        return new ArrayList<>(ipAddresses);
    }

    public void setIpAddresses(List<IpAddress> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Returns true if the device has an IP address
     *
     * @return
     */
    public boolean isActive() {
        return !ipAddresses.isEmpty();
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public boolean getAreDeviceMessagesSettingsDefault() {
        return this.areDeviceMessagesSettingsDefault;
    }

    public void setAreDeviceMessagesSettingsDefault(boolean settings) {
        this.areDeviceMessagesSettingsDefault = settings;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    public boolean isOnline() {
        return isOnline;
    }

    @JsonProperty
    public boolean isGateway() {
        return this.isGateway;
    }

    public void setIsGateway(boolean isGateway) {
        this.isGateway = isGateway;
    }

    public void markAsCurrentDevice() {
        this.isCurrentDevice = true;
    }

    public boolean isCurrentDevice() {
        return isCurrentDevice;
    }

    public String toString() {
        return "Device(" + getHardwareAddress() + ", IPs: " + Joiner.on(',').join(ipAddresses) + ")";
    }

    public boolean isUseAnonymizationService() {
        return useAnonymizationService;
    }

    public void setUseAnonymizationService(boolean useAnonymizationService) {
        this.useAnonymizationService = useAnonymizationService;
    }

    public boolean isRoutedThroughTor() {
        return routeThroughTor;
    }

    public void setRouteThroughTor(boolean useTor) {
        this.routeThroughTor = useTor;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    public boolean isFilterAdsEnabled() {
        return filterAdsEnabled;
    }

    public void setFilterAdsEnabled(boolean filterAdsEnabled) {
        this.filterAdsEnabled = filterAdsEnabled;
    }

    public boolean isFilterTrackersEnabled() {
        return filterTrackersEnabled;
    }

    public void setFilterTrackersEnabled(boolean filterTrackersEnabled) {
        this.filterTrackersEnabled = filterTrackersEnabled;
    }

    public boolean isMalwareFilterEnabled() {
        return malwareFilterEnabled;
    }

    public void setMalwareFilterEnabled(boolean malwareFilterEnabled) {
        this.malwareFilterEnabled = malwareFilterEnabled;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public boolean isSslRecordErrorsEnabled() {
        return sslRecordErrorsEnabled;
    }

    public void setSslRecordErrorsEnabled(boolean sslRecordErrorsEnabled) {
        this.sslRecordErrorsEnabled = sslRecordErrorsEnabled;
    }

    public void setIconMode(DisplayIconMode iconMode) {
        this.iconMode = iconMode;
    }

    public DisplayIconMode getIconMode() {
        if (iconMode != null)
            return iconMode;
        else
            return DisplayIconMode.getDefault();
    }

    public void setIconPosition(DisplayIconPosition iconPosition) {
        this.iconPosition = iconPosition;
    }

    public DisplayIconPosition getIconPosition() {
        if (iconPosition != null) {
            return iconPosition;
        }
        return DisplayIconPosition.getDefault();
    }

    /**
     * Did this device download the current root CA certificate already?
     *
     * @return
     */
    public boolean hasRootCAInstalled() {
        return hasDownloadedRootCA;
    }

    public void setHasRootCAInstalled(boolean installed) {
        this.hasDownloadedRootCA = installed;
    }

    public Integer getUseVPNProfileID() {
        return useVPNProfileID;
    }

    public void setUseVPNProfileID(Integer vpnProfileID) {
        this.useVPNProfileID = vpnProfileID;
    }

    public boolean isIpAddressFixed() {
        return ipAddressFixed;
    }

    public void setIpAddressFixed(boolean fixed) {
        this.ipAddressFixed = fixed;
    }

    public int getAssignedUser() {
        return assignedUser == null ? defaultSystemUser : assignedUser;
    }

    public void setAssignedUser(int assignedUser) {
        this.assignedUser = assignedUser;
    }

    public int getOperatingUser() {
        return operatingUser == null ? defaultSystemUser : operatingUser;
    }

    public void setOperatingUser(int operatingUser) {
        this.operatingUser = operatingUser;
    }

    public int getDefaultSystemUser() {
        return defaultSystemUser;
    }

    public void setDefaultSystemUser(int defaultSystemUser) {
        this.defaultSystemUser = defaultSystemUser;
    }

    public boolean isEblocker() {
        return isEblocker;
    }

    public void setIsEblocker(boolean isEblocker) {
        this.isEblocker = isEblocker;
    }

    public String getUserFriendlyName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (!ipAddresses.isEmpty()) {
            return ipAddresses.get(0).toString();
        }
        return getHardwareAddress();
    }

    public boolean isVpnClient() {
        return this.isVpnClient;
    }

    public void setIsVpnClient(boolean flag) {
        this.isVpnClient = flag;
    }

    public boolean isMessageShowInfo() {
        return messageShowInfo;
    }

    public void setMessageShowInfo(boolean messageShowInfo) {
        this.messageShowInfo = messageShowInfo;
    }

    public boolean isMessageShowAlert() {
        return messageShowAlert;
    }

    public void setMessageShowAlert(boolean messageShowAlert) {
        this.messageShowAlert = messageShowAlert;
    }

    public boolean isShowPauseDialog() {
        return showPauseDialog;
    }

    public void setShowPauseDialog(boolean showPauseDialog) {
        this.showPauseDialog = showPauseDialog;
    }

    public boolean isShowPauseDialogDoNotShowAgain() {
        return showPauseDialogDoNotShowAgain;
    }

    public void setShowPauseDialogDoNotShowAgain(boolean showPauseDialogDoNotShowAgain) {
        this.showPauseDialogDoNotShowAgain = showPauseDialogDoNotShowAgain;
    }

    public boolean isShowDnsFilterInfoDialog() {
        return showDnsFilterInfoDialog;
    }

    public void setShowDnsFilterInfoDialog(boolean showDnsFilterInfoDialog) {
        this.showDnsFilterInfoDialog = showDnsFilterInfoDialog;
    }

    public boolean isShowBookmarkDialog() {
        return showBookmarkDialog;
    }

    public void setShowBookmarkDialog(boolean showBookmarkDialog) {
        this.showBookmarkDialog = showBookmarkDialog;
    }

    public boolean isShowWelcomePage() {
        return showWelcomePage;
    }

    public void setShowWelcomePage(boolean showWelcomePage) {
        this.showWelcomePage = showWelcomePage;
    }

    public boolean isControlBarAutoMode() {
        return controlBarAutoMode;
    }

    public void setControlBarAutoMode(boolean controlBarAutoMode) {
        this.controlBarAutoMode = controlBarAutoMode;
    }

    public void setMobileState(boolean state) {
        this.mobileState = state;
    }

    public boolean isEblockerMobileEnabled() {
        return mobileState;
    }

    public boolean isMobilePrivateNetworkAccess() {
        return mobilePrivateNetworkAccess;
    }

    public void setMobilePrivateNetworkAccess(boolean mobilePrivateNetworkAccess) {
        this.mobilePrivateNetworkAccess = mobilePrivateNetworkAccess;
    }

    public boolean isDomainRecordingEnabled() {
        return domainRecordingEnabled;
    }

    public void setDomainRecordingEnabled(boolean domainRecordingEnabled) {
        this.domainRecordingEnabled = domainRecordingEnabled;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void getOfflineSinceString() {
        if (lastSeen == null) {
            lastSeenString = "";
            return;
        }
        ZonedDateTime lastSeenZoned = lastSeen.atZone(ZoneId.systemDefault());
        // If offline since today, show time only
        if (lastSeenZoned.toLocalDate().isEqual(LocalDate.now())) {
            lastSeenString = DateTimeFormatter.ofPattern("HH:mm").format(lastSeenZoned);
        }
        // If offline for more than 14 days, show date only
        else if (lastSeenZoned.isBefore(ZonedDateTime.now().minus(14, ChronoUnit.DAYS))) {
            lastSeenString = DateTimeFormatter.ofPattern("dd.MM.uuuu").format(lastSeenZoned);
        }
        // If offline since more than 24 hours, show days only
        else if (lastSeenZoned.isBefore(ZonedDateTime.now().minus(1, ChronoUnit.DAYS))) {
            lastSeenString = DateTimeFormatter.ofPattern("dd.MM").format(lastSeenZoned);
        }

    }

    public String getLastSeenString() {
        return lastSeenString;
    }

}

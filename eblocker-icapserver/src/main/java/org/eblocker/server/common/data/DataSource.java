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

import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.update.AutomaticUpdaterConfiguration;
import org.eblocker.server.http.ssl.AppWhitelistModule;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Provides access to persistent data, e.g. devices, users, ...
 */
public interface DataSource {

    SortedSet<String> getIds(Class<?> entityClass);

    <T> T get(Class<T> entityClass, int id);

    <T> List<T> getAll(Class<T> entityClass);

    <T> T save(T entity, int id);

    void delete(Class<?> entityClass, int id);

    void deleteAll(Class<?> entityClass);

    int nextId(Class<?> entityClass);

    <T> T get(Class<T> entityClass);

    <T> T save(T entity);

    void delete(Class<?> entityClass);

    Set<String> keys(String globPattern);

    void delete(String key);

    /**
     * Sets id sequence to initial value. This method should only used by migrations.
     *
     * @return
     */
    void setIdSequence(Class<?> entityClass, int value);

    /**
     * Returns the router's IP address
     */
    String getGateway();

    /**
     * Set the router's IP address
     *
     * @param gateway
     */
    void setGateway(String gateway);

    /**
     * Returns a sorted set of all device IDs
     */
    SortedSet<String> getDeviceIds();

    /**
     * Returns a sorted set of all devices
     */
    Set<Device> getDevices();

    /**
     * Returns a sorted set of all ACTIVE devices
     */
    Set<Device> getActiveDevices();

    /**
     * Returns a device with a specific ID
     */
    Device getDevice(String deviceId);

    /**
     * Returns all devices whose IP addresses are fixed
     *
     * @return
     */
    Set<Device> getDevicesIpFixed();

    /**
     * Get the pause in seconds between scanning for new devices
     *
     * @return
     */
    Long getDeviceScanningInterval();

    /**
     * Set the pause in seconds between scanning for new devices
     */
    void setDeviceScanningInterval(Long seconds);

    /**
     * Returns a user with a specific ID
     *
     * @param userId
     * @return
     */
    User getUser(String userId);

    /**
     * Returns all user IDs
     *
     * @return
     */
    Set<String> getUserIds();

    /**
     * Save a device
     *
     * @param device
     */
    void save(Device device);

    /**
     * Save a user
     *
     * @param user
     */
    void save(User user);

    /**
     * Add a new user
     *
     * @param user
     * @return
     */
    User addUser(User user);

    /**
     * Deletes a user
     *
     * @param user
     */
    void delete(User user);

    /**
     * Delete a device
     *
     * @param device
     */
    void delete(Device device);

    /**
     * Returns the current schema version. This method should only used by migrations.
     *
     * @return
     */
    String getVersion();

    /**
     * Sets the current schema version. This method should only be used by migrations.
     *
     * @param version
     */
    void setVersion(String version);

    /**
     * @param networkState
     */
    void setCurrentNetworkState(NetworkStateId networkState);

    /**
     * @return
     */
    NetworkStateId getCurrentNetworkState();

    /**
     * Sets the range of IP addresses the DHCP server should give to clients
     *
     * @param range
     */
    void setDhcpRange(DhcpRange range);

    /**
     * Removes the DHCP range.
     */
    void clearDhcpRange();

    /**
     * Returns the range of IP addresses the DHCP server should give to clients
     *
     * @return
     */
    DhcpRange getDhcpRange();

    /**
     * Return the configuration for automatic updates
     *
     * @return
     */
    AutomaticUpdaterConfiguration getAutomaticUpdateConfig();

    /**
     * Save the new time frame for automatic updates
     *
     * @param configuration
     */
    void save(AutomaticUpdaterConfiguration configuration);

    /**
     * Save the state of the AutomaticUpdater
     *
     * @param activated
     */
    void setAutomaticUpdatesActivated(boolean activated);

    /**
     * Get the state of the AutomaticUpdater (active or not)
     *
     * @return String "true" or "false" if this information exists, null otherwise
     */
    String getAutomaticUpdatesActivated();

    /**
     * Persist a snapshot of the database to disk. Call this method after important data has been
     * written.
     */
    void createSnapshot();

    void saveSynchronously();

    /**
     * Save the last time an update was executed (either automatic or manually)
     *
     * @param lastUpdate
     */
    void saveLastUpdateTime(LocalDateTime lastUpdate);

    /**
     * Get the last time an update was performed
     *
     * @return
     */
    LocalDateTime getLastUpdateTime();

    boolean getSSLEnabledState();

    void setSSLEnabledState(boolean enabled);

    /**
     * Save the current set of exit nodes in allowed countries for Tor instance
     *
     * @param selectedCountries
     */
    void saveCurrentTorExitNodes(Set<String> selectedCountries);

    /**
     * Get the current set of country names, which were chosen as allowed tor exit node countries
     *
     * @return
     */
    Set<String> getCurrentTorExitNodes();

    /**
     * Enable or disable the WebRTCBlockerProcessor, to block WebRTC connection establishments because it might
     * leak the IP (even though the client is routed through TOR)
     *
     * @param enabled
     */
    void setWebRTCBlockingState(boolean enabled);

    /**
     * Get the current state of the blocking of WebRTC
     *
     * @return
     */
    boolean getWebRTCBlockingState();

    /**
     * Enable or disable the deletion of the HTTP Referer header from the requests
     *
     * @param enabled
     */
    void setHTTPRefererRemovingState(boolean enabled);

    boolean getHTTPRefererRemovingState();

    /**
     * Enable or disable the direct responding (by the eBlocker) to the "Google Captive Portal check"
     *
     * @param enabled
     */
    void setGoogleCaptivePortalRedirectorState(boolean enabled);

    boolean getGoogleCaptivePortalRedirectorState();

    /**
     * Enable or disable setting the DNT (Do not track) Header
     *
     * @param enabled
     */
    void setDntHeaderState(boolean enabled);

    boolean getDntHeaderState();

    /**
     * Enable or disable displaying the license expiration reminder to the user
     *
     * @param show
     */
    void setDoNotShowReminder(boolean show);

    boolean isDoNotShowReminder();

    /**
     * Enable or disable displaying of the splash screen for eBlocker 2
     *
     * @param show
     */
    void setShowSplashScreen(boolean show);

    boolean isShowSplashScreen();

    void setAutoEnableNewDevices(boolean autoEnableNewDevices);

    boolean isAutoEnableNewDevices();

    CompressionMode getCompressionMode();

    void setCompressionMode(CompressionMode compressionMode);

    boolean getSslRecordErrors();

    void setSslRecordErrors(boolean recordSslErrors);

    /**
     * Check from when the last SSL domain whitelist is
     *
     * @return
     */
    ZonedDateTime getLastSSLWhitelistDate();

    /**
     * Set the last date from which we saw a SSL default whitelist
     *
     * @param defaultDomainFileLastModified
     */
    void setLastModifiedSSLDefaultWhitelist(ZonedDateTime defaultDomainFileLastModified);

    /**
     * Get the currently used language in the frontend
     *
     * @return
     */
    Language getCurrentLanguage();

    /**
     * Set the current language for the frontend
     *
     * @param language
     */
    void setCurrentLanguage(Language language);

    /**
     * Save the POSIX timezone String for the default timezone to use
     *
     * @param posixString
     */
    void setTimezone(String posixString);

    /**
     * Get the POSIX timezone string
     *
     * @return
     */
    String getTimezone();

    /**
     * Check when the last update of the default AppModules list happened
     *
     * @return
     */
    ZonedDateTime getLastAppWhitelistModulesDate();

    /**
     * Set the last date from which we saw, that there has been a change in the appWhitelistModules JSCN file
     *
     * @param appWhitelistModulesJSONFileLastModified
     */
    void setLastAppWhitelistModuleDate(ZonedDateTime appWhitelistModulesJSONFileLastModified);

    /**
     * Get the 'enabled' status of the AppWhitelistModule with a certain ID
     *
     * @param moduleID
     * @return DEFAULT: if no status was set by the user, it is still the default
     * ENABLED: the user enabled the module
     * DISABLED: the user disabled the module
     */
    AppWhitelistModule.State getAppModuleState(int moduleID);

    /**
     * Set an 'enabled' status for a certain AppWhitelistModule
     *
     * @param moduleID
     * @param enabled
     */
    void setAppWhitelistModuleStatus(int moduleID, boolean enabled);

    Decision getRedirectDecision(String sessionId, String domain);

    void setRedirectDecision(String sessionId, String domain, Decision decision);

    void setPasswordHash(byte[] passwordHash);

    byte[] getPasswordHash();

    void deletePasswordHash();

    String getListsPackageVersion();

    boolean isIpFixedByDefault();

    void setIsExpertMode(boolean expert);

    boolean isExpertMode();

    void setIpFixedByDefault(boolean ipFixedByDefault);

    void setIpAddressesFixed(boolean fixed);

    /**
     * Returns the flag that indicates that the last shutdown was clean.
     *
     * @return true if the shutdown was clean (or if it has never been set yet)
     */
    boolean getCleanShutdownFlag();

    /**
     * Sets the flag that indicates that the last shutdown was clean.
     * Set this to false during startup (after evaluating the flag).
     * Set this to true in a shutdown hook.
     *
     * @param cleanShutdown
     */
    void setCleanShutdownFlag(boolean cleanShutdown);

    void setOpenVpnServerState(boolean state);

    boolean getOpenVpnServerState();

    void setOpenVpnServerFirstRun(boolean state);

    boolean getOpenVpnServerFirstRun();

    void setOpenVpnServerHost(String host);

    String getOpenVpnServerHost();

    Integer getOpenVpnMappedPort();

    void setOpenVpnMappedPort(Integer port);

    PortForwardingMode getOpenVpnPortForwardingMode();

    void setOpenVpnPortForwardingMode(PortForwardingMode mode);

    ExternalAddressType getOpenVpnExternalAddressType();

    void setOpenVpnExternalAddressType(ExternalAddressType type);

    String getResolvedDnsGateway();

    void setResolvedDnsGateway(String gateway);

    Integer getDhcpLeaseTime();

    void setDhcpLeaseTime(Integer leaseTime);

    boolean isMalwareUrlFilterEnabled();

    void setMalwareUrlFilterEnabled(boolean enabled);
}

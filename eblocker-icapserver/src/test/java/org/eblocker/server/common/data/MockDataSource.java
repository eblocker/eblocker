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
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.update.AutomaticUpdaterConfiguration;
import org.eblocker.server.http.ssl.AppWhitelistModule;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

public class MockDataSource implements DataSource {

    private String gatewayIP;
    private Set<VpnProfile> vpnProfiles = new HashSet<>();

    public void addVPNProfile(VpnProfile profile) {
        if (profile != null) {
            vpnProfiles.add(profile);
        }
    }

    public void removeAllVPNProfiles() {
        vpnProfiles.clear();
    }

    @Override
    public SortedSet<String> getIds(Class<?> entityClass) {
        return null;
    }

    @Override
    public <T> T get(Class<T> entityClass, int id) {
        return null;
    }

    @Override
    public <T> List<T> getAll(Class<T> entityClass) {
        return null;
    }

    @Override
    public <T> T save(T entity, int id) {
        return null;
    }

    @Override
    public void delete(Class<?> entityClass, int id) {
    }

    @Override
    public void deleteAll(Class<?> entityClass) {

    }

    @Override
    public int nextId(Class<?> entityClass) {
        return 0;
    }

    @Override
    public void setIdSequence(Class<?> entityClass, int value) {
    }

    @Override
    public <T> T get(Class<T> entityClass) {
        return null;
    }

    @Override
    public <T> T save(T entity) {
        return null;
    }

    @Override
    public void delete(Class<?> entityClass) {

    }

    @Override
    public Set<String> keys(String globPattern) {
        return null;
    }

    @Override
    public void delete(String key) {

    }

    @Override
    public String getGateway() {
        return gatewayIP;
    }

    @Override
    public void setGateway(String gateway) {
        gatewayIP = gateway;
    }

    @Override
    public SortedSet<String> getDeviceIds() {
        return null;
    }

    @Override
    public Set<Device> getDevices() {
        return null;
    }

    @Override
    public Set<Device> getActiveDevices() {
        return null;
    }

    @Override
    public Device getDevice(String deviceId) {
        return null;
    }

    public void updateVpnProfile(String deviceId, Integer vpnProfileId) {
    }

    @Override
    public User getUser(String userId) {
        return null;
    }

    @Override
    public Set<String> getUserIds() {
        return null;
    }

    @Override
    public void save(Device device) {

    }

    @Override
    public void save(User user) {

    }

    @Override
    public User addUser(User user) {
        return null;
    }

    @Override
    public void delete(User user) {

    }

    @Override
    public void delete(Device device) {

    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public void setVersion(String version) {

    }

    @Override
    public void setCurrentNetworkState(NetworkStateId networkState) {

    }

    @Override
    public NetworkStateId getCurrentNetworkState() {
        return null;
    }

    @Override
    public void setDhcpRange(DhcpRange range) {

    }

    @Override
    public void clearDhcpRange() {

    }

    @Override
    public DhcpRange getDhcpRange() {
        return null;
    }

    @Override
    public AutomaticUpdaterConfiguration getAutomaticUpdateConfig() {
        return null;
    }

    @Override
    public void save(AutomaticUpdaterConfiguration configuration) {

    }

    @Override
    public void setAutomaticUpdatesActivated(boolean activated) {

    }

    @Override
    public String getAutomaticUpdatesActivated() {
        return null;
    }

    @Override
    public void createSnapshot() {

    }

    @Override
    public void saveSynchronously() {

    }

    @Override
    public void saveLastUpdateTime(LocalDateTime lastUpdate) {

    }

    @Override
    public LocalDateTime getLastUpdateTime() {
        return null;
    }

    @Override
    public boolean getSSLEnabledState() {
        return false;
    }

    @Override
    public void setSSLEnabledState(boolean enabled) {

    }

    @Override
    public void saveCurrentTorExitNodes(Set<String> selectedCountries) {

    }

    @Override
    public Set<String> getCurrentTorExitNodes() {
        return null;
    }

    @Override
    public void setWebRTCBlockingState(boolean enabled) {

    }

    @Override
    public boolean getWebRTCBlockingState() {
        return false;
    }

    @Override
    public void setHTTPRefererRemovingState(boolean enabled) {

    }

    @Override
    public boolean getHTTPRefererRemovingState() {
        return false;
    }

    @Override
    public void setGoogleCaptivePortalRedirectorState(boolean enabled) {

    }

    @Override
    public boolean getGoogleCaptivePortalRedirectorState() {
        return false;
    }

    @Override
    public CompressionMode getCompressionMode() {
        return CompressionMode.OFF;
    }

    @Override
    public void setCompressionMode(CompressionMode compressionMode) {
    }

    @Override
    public boolean getSslRecordErrors() {
        return false;
    }

    @Override
    public void setSslRecordErrors(boolean recordSslErrors) {
    }

    @Override
    public ZonedDateTime getLastSSLWhitelistDate() {
        return null;
    }

    @Override
    public void setLastModifiedSSLDefaultWhitelist(ZonedDateTime defaultDomainFileLastModified) {

    }

    @Override
    public Language getCurrentLanguage() {
        return null;
    }

    @Override
    public void setCurrentLanguage(Language language) {

    }

    @Override
    public void setTimezone(String posixString) {

    }

    @Override
    public String getTimezone() {
        return null;
    }

    @Override
    public ZonedDateTime getLastAppWhitelistModulesDate() {
        return null;
    }

    @Override
    public void setLastAppWhitelistModuleDate(ZonedDateTime appWhitelistModulesJSONFileLastModified) {

    }

    @Override
    public AppWhitelistModule.State getAppModuleState(int moduleID) {
        return null;
    }

    @Override
    public void setAppWhitelistModuleStatus(int moduleID, boolean enabled) {

    }

    @Override
    public Decision getRedirectDecision(String sessionId, String domain) {
        return null;
    }

    @Override
    public void setRedirectDecision(String sessionId, String domain, Decision decision) {

    }

    @Override
    public void setPasswordHash(byte[] passwordHash) {

    }

    @Override
    public byte[] getPasswordHash() {
        return new byte[0];
    }

    @Override
    public void deletePasswordHash() {

    }

    @Override
    public String getListsPackageVersion() {
        return null;
    }

    @Override
    public Set<Device> getDevicesIpFixed() {
        return null;
    }

    @Override
    public Long getDeviceScanningInterval() {
        return null;
    }

    @Override
    public void setDeviceScanningInterval(Long seconds) {

    }

    @Override
    public boolean isIpFixedByDefault() {
        return false;
    }

    @Override
    public void setIsExpertMode(boolean expert) {

    }

    @Override
    public boolean isExpertMode() {
        return false;
    }

    @Override
    public void setIpFixedByDefault(boolean ipFixedByDefault) {

    }

    @Override
    public void setIpAddressesFixed(boolean fixed) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean getCleanShutdownFlag() {
        return false;
    }

    @Override
    public void setCleanShutdownFlag(boolean cleanShutdown) {
    }

    @Override
    public void setOpenVpnServerState(boolean state) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getOpenVpnServerState() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setOpenVpnServerFirstRun(boolean state) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getOpenVpnServerFirstRun() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setOpenVpnServerHost(String host) {
        // TODO Auto-generated method stub
    }

    @Override
    public String getOpenVpnServerHost() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Integer getOpenVpnMappedPort() {
        return null;
    }

    @Override
    public void setOpenVpnMappedPort(Integer port) {

    }

    @Override
    public ExternalAddressType getOpenVpnExternalAddressType() {
        return null;
    }

    @Override
    public void setOpenVpnExternalAddressType(ExternalAddressType type) {

    }

    @Override
    public String getResolvedDnsGateway() {
        return null;
    }

    @Override
    public void setResolvedDnsGateway(String gateway) {
    }

    @Override
    public void setDntHeaderState(boolean enabled) {

    }

    @Override
    public boolean getDntHeaderState() {
        return false;
    }

    @Override
    public void setDoNotShowReminder(boolean show) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isDoNotShowReminder() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public PortForwardingMode getOpenVpnPortForwardingMode() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setOpenVpnPortForwardingMode(PortForwardingMode mode) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setShowSplashScreen(boolean show) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isShowSplashScreen() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setAutoEnableNewDevices(boolean autoEnableNewDevices) {

    }

    @Override
    public boolean isAutoEnableNewDevices() {
        return false;
    }

    @Override
    public Integer getDhcpLeaseTime() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setDhcpLeaseTime(Integer leaseTime) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isMalwareUrlFilterEnabled() {
        return false;
    }

    @Override
    public void setMalwareUrlFilterEnabled(boolean enabled) {
    }

    @Override
    public boolean isContentFilterEnabled() {
        return false;
    }

    @Override
    public void setContentFilterEnabled(boolean enabled) {

    }
}

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.codec.binary.Base64;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.update.AutomaticUpdaterConfiguration;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.DATABASE_CLIENT, allowUninitializedCalls = false)
public class JedisDataSource implements DataSource {

    private static final Logger LOG = LoggerFactory.getLogger(JedisDataSource.class);

    private static final String KEY_NAME = "name";
    private static final String KEY_VERSION = "version";
    private static final String KEY_USERS = "users";
    private static final String KEY_GATEWAY = "gateway";
    private static final String KEY_IP_ADDRESS = "ipAddress";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_NETWORK_STATE = "networkState";
    private static final String KEY_DHCP_RANGE = "dhcpRange";
    private static final String KEY_IP_ADDRESS_FIRST = "first";
    private static final String KEY_IP_ADDRESS_LAST = "last";
    private static final String KEY_AUTOUPDATE = "autoupdate";
    private static final String KEY_AUTOUPDATE_ACTIVE = "autoupdate_active";
    private static final String KEY_LASTUPDATE = "lastUpdate";
    private static final String KEY_MALWARE_FILTER_ENABLED = "malware_filter_enabled";
    private static final String KEY_SSL_ENABLED = "ssl_enabled";
    private static final String KEY_SSL_RECORD_ERRORS = "ssl_record_errors";
    private static final String KEY_ROOT_CA_INSTALLED = "root_ca_installed";
    private static final String KEY_WEBRTC_BLOCKING_STATE = "webrtc_block_enabled";
    private static final String KEY_HTTP_REFERER_REMOVE_STATE = "http_referer_remove_enabled";
    private static final String KEY_GOOGLE_CAPTIVE_PORTAL_CHECK_RESPONDER_STATE = "google_CPC_responder_enabled";
    private static final String KEY_DNT_HEADER_STATE = "dnt_header_enabled";
    private static final String KEY_DOMAIN_RECORDING_ENABLED = "domain_recording_enabled";
    private static final String KEY_DO_NOT_SHOW_REMINDER = "do_not_show_reminder";
    private static final String KEY_SHOW_SPLASH_SCREEN = "showSplashScreen";
    private static final String KEY_AUTO_ENABLE_NEW_DEVICES = "autoEnableNewDevices";
    private static final String KEY_COMPRESSION_MODE = "compression_mode";
    private static final String KEY_LAST_SSL_DEFAULT_WHITELIST_UPDATE = "ssl_default_whitelist_date";
    private static final String KEY_LAST_APPMODULES_DEFAULT_FILE_UPDATE = "appmodules_default_json_file_update";
    private static final String VALUE_FALSE = "false";
    private static final String VALUE_TRUE = "true";
    private static final String KEY_MESSAGE_SHOW_INFO = "messageShowInfo";
    private static final String KEY_MESSAGE_SHOW_ALERT = "messageShowAlert";
    private static final String KEY_SHOW_PAUSE_DIALOG = "showPauseDialog";
    private static final String KEY_PAUSE_DIALOG_DO_NOT_SHOW_AGAIN = "showPauseDialogDoNotShowAgain";
    private static final String KEY_SHOW_DNS_FILTER_UPDATE_INFO = "showDnsFilterInfoDialog";
    private static final String KEY_SHOW_BOOKMARK_DIALOG = "showBookmarkDialog";
    private static final String KEY_SHOW_WELCOME_PAGE = "showWelcomePage";
    private static final String KEY_IS_CONTROLBAR_AUTO_MODE = "isControlBarAutoMode";
    private static final String KEY_IS_MOBILE_ENABLED = "isMobileEnabled";
    private static final String KEY_MOBILE_PRIVATE_NETWORK_ACCESS = "mobilePrivateNetworkAccess";
    private static final String KEY_RESOLVED_DNS_GATEWAY = "resolved_dns_gateway";

    private static final int MAX_DATABASES = 16;
    private static final String KEY_USE_ANONYMIZATION_SERVICE = "useAnonymizationService";
    private static final String KEY_USE_TOR = "useTor";
    private static final String KEY_SHOW_WARNINGS = "showWarnings";
    private static final String KEY_TOR_CURRENT_EXIT_NODES = "torCurrentExitNodes";
    private static final String KEY_ICON_MODE = "display_icon_mode";
    private static final String KEY_ICON_POSITION = "display_icon_position";
    private static final String KEY_LANGUAGE = "frontend_language";
    private static final String KEY_TIMEZONE = "timezone";

    private static final String KEY_VPNPROFILE_ID = "vpn_profile_id";
    private static final String KEY_DHCP_FIXED_IP = "dhcp_fixed_ip";
    private static final String KEY_DHCP_IP_FIXED_BY_DEFAULT = "dhcp_ip_fixed_by_default";
    private static final String KEY_NETWORK_IS_EXPERT_MODE = "network_is_expert_mode";

    private static final String KEY_CLEAN_SHUTDOWN = "clean_shutdown";

    private static final String ID_PREFIX_SEPARATOR = ":";
    private static final String ID_SEQUENCE_SUFFIX = "sequence";

    private static final String KEY_PARENTAL_CONTROL_USER_ID = "parentalControlUserId";
    private static final String KEY_PARENTAL_CONTROL_OPERATING_USER_ID = "parentalControlOperatingUserId";
    private static final String KEY_DEFAULT_SYSTEM_USER_ID = "defaultSystemUserId";

    private static final String KEY_IS_OPENVPN_CLIENT = "IsOpenVpnClient";
    private static final String KEY_OPENVPN_SERVER_ENABLED = "OpenVpnServerEnabled";
    private static final String KEY_OPENVPN_FIRST_RUN = "OpenVpnFirstRun";
    private static final String KEY_OPENVPN_SERVER_HOST = "OpenVpnHost";
    private static final String KEY_OPENVPN_MAPPED_PORT = "OpenVpnMappedPort";
    private static final String KEY_OPENVPN_PORT_FORWARDING_MODE = "OpenVpnPortForwardingMode";
    private static final String KEY_OPENVPN_EXTERNAL_ADDRESS_TYPE = "OpenVpnExternalAddressType";

    private static final String KEY_FILTER_MODE = "filter_mode";
    private static final String KEY_FILTER_PLUG_AND_PLAY_ADS_ENABLED = "filter_plug_and_play_ads_enabled";
    private static final String KEY_FILTER_PLUG_AND_PLAY_TRACKERS_ENABLED = "filter_plug_and_play_trackers_enabled";

    private static final String KEY_MALWARE_URL_FILTER_ENABLED = "malware_url_filter_enabled";

    private static final String KEY_DHCP_LEASE_TIME = "dhcpLeaseTime";

    static final String KEY_DEVICE_SCANNING_INTERVAL = "deviceScanningInterval";

    // map containing key prefixes for entities with different prefix than from class.getSimpleName()
    private static final Map<Class<?>, String> ID_PREFIX = new HashMap<>();

    static {
        ID_PREFIX.put(AppWhitelistModule.class, "AppModuleDetails");
    }

    private final JedisPool pool;
    private final ObjectMapper objectMapper;

    @Inject
    public JedisDataSource(JedisPool pool, ObjectMapper objectMapper) {
        this.pool = pool;
        this.objectMapper = objectMapper;
    }

    /* Generic DAO methods */
    protected String getKey(Class<?> entityClass, int id) {
        return getKey(entityClass) + ID_PREFIX_SEPARATOR + id;
    }

    protected String getKey(Class<?> entityClass) {
        String prefix = ID_PREFIX.get(entityClass);
        if (prefix == null) {
            return entityClass.getSimpleName();
        }
        return prefix;
    }

    protected String getIdSequenceKey(Class<?> entityClass) {
        return getKey(entityClass) + ID_PREFIX_SEPARATOR + ID_SEQUENCE_SUFFIX;
    }

    //TODO: Should be renamed to getKeys(), as it returns the complete keys, not only the numerical ids.
    @Override
    public SortedSet<String> getIds(Class<?> entityClass) {
        try (Jedis jedis = pool.getResource()) {
            return getKeys(jedis, entityClass);
        }
    }

    private SortedSet<String> getKeys(Jedis jedis, Class<?> entityClass) {
        return new TreeSet<>(jedis.keys(getKey(entityClass) + ID_PREFIX_SEPARATOR + "[0-9]*"));
    }

    @Override
    public <T> T get(Class<T> entityClass, int id) {
        return get(entityClass, getKey(entityClass, id));
    }

    @Override
    public <T> T get(Class<T> entityClass) {
        return get(entityClass, getKey(entityClass));
    }

    protected <T> T get(Class<T> entityClass, String id) {
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.get(id);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, entityClass);

        } catch (IOException e) {
            LOG.error("Cannot deserialize entity of type {}.", entityClass.getName(), e);
            return null;
        }
    }

    @Override
    public <T> List<T> getAll(Class<T> entityClass) {
        SortedSet<String> ids = getIds(entityClass);
        List<T> entities = new ArrayList<>();
        for (String id : ids) {
            T entity = get(entityClass, id);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    @Override
    public <T> T save(T entity, int id) {
        try (Jedis jedis = pool.getResource()) {
            String json = objectMapper.writeValueAsString(entity);
            jedis.set(getKey(entity.getClass(), id), json);
            return entity;

        } catch (JsonProcessingException e) {
            LOG.error("Cannot save entity {} with id {}.", entity.getClass().getName(), id, e);
            return null;
        }
    }

    @Override
    public <T> T save(T entity) {
        try (Jedis jedis = pool.getResource()) {
            String json = objectMapper.writeValueAsString(entity);
            jedis.set(getKey(entity.getClass()), json);
            return entity;

        } catch (JsonProcessingException e) {
            LOG.error("Cannot save singleton entity {}.", entity.getClass().getName(), e);
            return null;
        }
    }

    @Override
    public void delete(Class<?> entityClass, int id) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(getKey(entityClass, id));
        }
    }

    @Override
    public void delete(Class<?> entityClass) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(getKey(entityClass));
        }
    }

    @Override
    public Set<String> keys(String globPattern) {
        try (Jedis jedis = pool.getResource()) {
            return new TreeSet<>(jedis.keys(globPattern));
        }
    }

    @Override
    public void delete(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    @Override
    public void deleteAll(Class<?> entityClass) {
        try (Jedis jedis = pool.getResource()) {
            for (String id : getKeys(jedis, entityClass)) {
                jedis.del(id);
            }
        }
    }

    @Override
    public int nextId(Class<?> entityClass) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.incr(getIdSequenceKey(entityClass)).intValue();
        }
    }

    @Override
    public void setIdSequence(Class<?> entityClass, int value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(getIdSequenceKey(entityClass), String.valueOf(value));
        }
    }

    /* specific database access methods */

    @Override
    public String getGateway() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_GATEWAY);
        }
    }

    @Override
    public void setGateway(String gateway) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_GATEWAY, gateway);
        }
    }

    @Override
    public SortedSet<String> getDeviceIds() {
        try (Jedis jedis = pool.getResource()) {
            return new TreeSet<>(jedis.keys(Device.ID_PREFIX + "*"));
        }
    }

    @Override
    public Set<Device> getDevices() {
        SortedSet<String> deviceIds = getDeviceIds();
        Set<Device> devices = new HashSet<>();

        for (String deviceId : deviceIds) {
            Device device = getDevice(deviceId);
            devices.add(device);
        }
        return devices;
    }

    @Override
    public Set<Device> getActiveDevices() {
        return getDevices().stream().filter(Device::isActive).collect(Collectors.toSet());
    }

    @Override
    public Set<Device> getDevicesIpFixed() {
        return getDevices().stream().filter(Device::isIpAddressFixed).collect(Collectors.toSet());
    }

    @Override
    public Long getDeviceScanningInterval() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_DEVICE_SCANNING_INTERVAL);
            if (value == null) {
                return null;
            } else {
                return Long.valueOf(value);
            }
        }
    }

    @Override
    public void setDeviceScanningInterval(Long seconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_DEVICE_SCANNING_INTERVAL, seconds.toString());
        }
    }

    @Override
    public Device getDevice(String deviceId) {
        Map<String, String> map;
        try (Jedis jedis = pool.getResource()) {
            map = jedis.hgetAll(deviceId);
        }

        if (map.isEmpty()) {
            return null;
        }

        Device device = new Device();
        device.setId(deviceId);
        device.setName(map.get(KEY_NAME));

        if (map.get(KEY_IP_ADDRESS) != null) {
            List<IpAddress> ipAddresses = new ArrayList<>();
            for (String ip : map.get(KEY_IP_ADDRESS).split(",")) {
                ipAddresses.add(IpAddress.parse(ip));
            }
            device.setIpAddresses(ipAddresses);
        } else {
            device.setIpAddresses(Collections.emptyList());
        }

        String enabled = map.get(KEY_ENABLED);
        device.setEnabled((enabled == null || enabled.equals(VALUE_TRUE)));
        String paused = map.get(KEY_PAUSED);
        device.setPaused((paused != null && paused.equals(VALUE_TRUE)));

        String showMessageInfo = map.get(KEY_MESSAGE_SHOW_INFO);
        device.setMessageShowInfo((showMessageInfo == null || showMessageInfo.equals(VALUE_TRUE)));
        String showMessageAlert = map.get(KEY_MESSAGE_SHOW_ALERT);
        device.setMessageShowAlert((showMessageAlert == null || showMessageAlert.equals(VALUE_TRUE)));

        String showPauseDialog = map.get(KEY_SHOW_PAUSE_DIALOG);
        device.setShowPauseDialog((showPauseDialog == null || showPauseDialog.equals(VALUE_TRUE)));

        String showDnsFilterInfoDialog = map.get(KEY_SHOW_DNS_FILTER_UPDATE_INFO);
        device.setShowDnsFilterInfoDialog((showDnsFilterInfoDialog == null || showDnsFilterInfoDialog.equals(VALUE_TRUE)));

        String showBookmarkDialog = map.get(KEY_SHOW_BOOKMARK_DIALOG);
        device.setShowBookmarkDialog((showBookmarkDialog == null || showBookmarkDialog.equals(VALUE_TRUE)));

        String showWelcomePage = map.get(KEY_SHOW_WELCOME_PAGE);
        device.setShowWelcomePage((showWelcomePage == null || showWelcomePage.equals(VALUE_TRUE)));

        String controlBarAutoMode = map.get(KEY_IS_CONTROLBAR_AUTO_MODE);
        // defaults to false, unless explicitly set to true
        device.setControlBarAutoMode((controlBarAutoMode != null && controlBarAutoMode.equals(VALUE_TRUE)));

        String isMobileEnabled = map.get(KEY_IS_MOBILE_ENABLED);
        // defaults to false, unless explicitly set to true
        device.setMobileState(isMobileEnabled == null || isMobileEnabled.equals(VALUE_TRUE));

        device.setMobilePrivateNetworkAccess(Boolean.parseBoolean(map.get(KEY_MOBILE_PRIVATE_NETWORK_ACCESS)));

        String pauseDialogDoNotShow = map.get(KEY_PAUSE_DIALOG_DO_NOT_SHOW_AGAIN);
        device.setShowPauseDialogDoNotShowAgain((pauseDialogDoNotShow == null || pauseDialogDoNotShow.equals(VALUE_TRUE)));

        device.setRouteThroughTor(VALUE_TRUE.equals(map.get(KEY_USE_TOR)));

        String vpnProfileIDString = map.get(KEY_VPNPROFILE_ID);
        if (vpnProfileIDString != null) {
            device.setUseVPNProfileID(Integer.parseInt(vpnProfileIDString));
        }

        String useAnonymizationService = map.get(KEY_USE_ANONYMIZATION_SERVICE);
        if (useAnonymizationService != null) {
            device.setUseAnonymizationService(VALUE_TRUE.equals(useAnonymizationService));
        } else {
            // deduce value if not present (backward compatibility)
            device.setUseAnonymizationService(device.isRoutedThroughTor() || device.getUseVPNProfileID() != null);
        }

        String parentalControlUserId = map.get(KEY_PARENTAL_CONTROL_USER_ID);
        if (parentalControlUserId != null) {
            device.setAssignedUser(Integer.parseInt(parentalControlUserId));
        }
        String parentalControlOperatingUserId = map.get(KEY_PARENTAL_CONTROL_OPERATING_USER_ID);
        if (parentalControlOperatingUserId != null) {
            device.setOperatingUser(Integer.parseInt(parentalControlOperatingUserId));
        }
        String defaultSystemUserId = map.get(KEY_DEFAULT_SYSTEM_USER_ID);
        if (defaultSystemUserId != null) {
            device.setDefaultSystemUser(Integer.parseInt(defaultSystemUserId));
        }

        String showWarnings = map.get(KEY_SHOW_WARNINGS);
        device.setAreDeviceMessagesSettingsDefault((showWarnings == null || showWarnings.equals(VALUE_TRUE)));

        String gateway = getGateway();
        device.setIsGateway(gateway != null && device.getIpAddresses().contains(IpAddress.parse(gateway)));

        String malwareEnabled = map.get(KEY_MALWARE_FILTER_ENABLED);
        device.setMalwareFilterEnabled(malwareEnabled == null || Boolean.parseBoolean(malwareEnabled));

        String sslEnabled = map.get(KEY_SSL_ENABLED);
        if (sslEnabled != null) {
            device.setSslEnabled((sslEnabled.equals(VALUE_TRUE)));
        }

        String sslRecordErrors = map.get(KEY_SSL_RECORD_ERRORS);
        if (sslRecordErrors != null) {
            device.setSslRecordErrorsEnabled(Boolean.parseBoolean(sslRecordErrors));
        }

        String domainRecordingEnabled = map.get(KEY_DOMAIN_RECORDING_ENABLED);
        if (domainRecordingEnabled != null) {
            device.setDomainRecordingEnabled(Boolean.parseBoolean(domainRecordingEnabled));
        }

        String hasRootCAInstalled = map.get(KEY_ROOT_CA_INSTALLED);
        if (hasRootCAInstalled != null) {
            device.setHasRootCAInstalled((hasRootCAInstalled.equals(VALUE_TRUE)));
        }

        String iconModeString = map.get(KEY_ICON_MODE);
        if (iconModeString != null) {
            DisplayIconMode iconMode = DisplayIconMode.valueOf(iconModeString);
            device.setIconMode(iconMode);
        }

        String iconPositionString = map.get(KEY_ICON_POSITION);
        if (iconPositionString != null) {
            Device.DisplayIconPosition iconPosition = Device.DisplayIconPosition.valueOf(iconPositionString);
            device.setIconPosition(iconPosition);
        }

        String fixedString = map.get(KEY_DHCP_FIXED_IP);
        boolean fixed;
        if (fixedString == null) {
            String fixedByDefaultString = map.get(KEY_DHCP_IP_FIXED_BY_DEFAULT);
            if (fixedByDefaultString == null) {
                fixed = true;
            } else {
                fixed = Boolean.valueOf(fixedByDefaultString);
            }
        } else {
            fixed = Boolean.valueOf(fixedString);
        }
        device.setIpAddressFixed(fixed);

        String isVpnClient = map.get(KEY_IS_OPENVPN_CLIENT);
        if (isVpnClient != null) {
            device.setIsVpnClient(isVpnClient.equals(VALUE_TRUE));
        }

        String filterModeName = map.get(KEY_FILTER_MODE);
        if (filterModeName != null) {
            device.setFilterMode(FilterMode.valueOf(filterModeName));
            device.setFilterPlugAndPlayAdsEnabled(Boolean.parseBoolean(map.get(KEY_FILTER_PLUG_AND_PLAY_ADS_ENABLED)));
            device.setFilterPlugAndPlayTrackersEnabled(Boolean.parseBoolean(map.get(KEY_FILTER_PLUG_AND_PLAY_TRACKERS_ENABLED)));
        }

        return device;
    }

    @Override
    public void save(Device device) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> map = new HashMap<>();
            if (device.getName() != null) {
                map.put(KEY_NAME, device.getName());
            }

            if (!device.getIpAddresses().isEmpty()) {
                map.put(KEY_IP_ADDRESS, device.getIpAddresses().stream()
                        .map(IpAddress::toString)
                        .collect(Collectors.joining(",")));
            } else {
                jedis.hdel(device.getId(), KEY_IP_ADDRESS);
            }

            map.put(KEY_MESSAGE_SHOW_INFO, device.isMessageShowInfo() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_MESSAGE_SHOW_ALERT, device.isMessageShowAlert() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_SHOW_PAUSE_DIALOG, device.isShowPauseDialog() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_PAUSE_DIALOG_DO_NOT_SHOW_AGAIN, device.isShowPauseDialogDoNotShowAgain() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_SHOW_DNS_FILTER_UPDATE_INFO, device.isShowDnsFilterInfoDialog() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_SHOW_WELCOME_PAGE, device.isShowWelcomePage() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_SHOW_BOOKMARK_DIALOG, device.isShowBookmarkDialog() ? VALUE_TRUE : VALUE_FALSE);

            map.put(KEY_IS_CONTROLBAR_AUTO_MODE, device.isControlBarAutoMode() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_IS_MOBILE_ENABLED, device.isEblockerMobileEnabled() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_MOBILE_PRIVATE_NETWORK_ACCESS, Boolean.toString(device.isMobilePrivateNetworkAccess()));
            map.put(KEY_USE_ANONYMIZATION_SERVICE, device.isUseAnonymizationService() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_USE_TOR, device.isRoutedThroughTor() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_MALWARE_FILTER_ENABLED, Boolean.toString(device.isMalwareFilterEnabled()));
            map.put(KEY_SSL_ENABLED, Boolean.toString(device.isSslEnabled()));
            map.put(KEY_SSL_RECORD_ERRORS, Boolean.toString(device.isSslRecordErrorsEnabled()));
            map.put(KEY_DOMAIN_RECORDING_ENABLED, Boolean.toString(device.isDomainRecordingEnabled()));
            map.put(KEY_ROOT_CA_INSTALLED, device.hasRootCAInstalled() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_ICON_MODE, device.getIconMode().name());
            map.put(KEY_ICON_POSITION, device.getIconPosition().name());
            map.put(KEY_ENABLED, device.isEnabled() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_PAUSED, device.isPaused() ? VALUE_TRUE : VALUE_FALSE);
            if (device.getUseVPNProfileID() != null) {
                map.put(KEY_VPNPROFILE_ID, device.getUseVPNProfileID().toString());
            } else {
                jedis.hdel(device.getId(), KEY_VPNPROFILE_ID);
            }
            map.put(KEY_SHOW_WARNINGS, device.getAreDeviceMessagesSettingsDefault() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_DHCP_FIXED_IP, device.isIpAddressFixed() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_PARENTAL_CONTROL_USER_ID, String.valueOf(device.getAssignedUser()));
            map.put(KEY_PARENTAL_CONTROL_OPERATING_USER_ID, String.valueOf(device.getOperatingUser()));
            map.put(KEY_DEFAULT_SYSTEM_USER_ID, String.valueOf(device.getDefaultSystemUser()));
            map.put(KEY_IS_OPENVPN_CLIENT, device.isVpnClient() ? VALUE_TRUE : VALUE_FALSE);
            map.put(KEY_FILTER_MODE, device.getFilterMode().name());
            map.put(KEY_FILTER_PLUG_AND_PLAY_ADS_ENABLED, Boolean.toString(device.isFilterPlugAndPlayAdsEnabled()));
            map.put(KEY_FILTER_PLUG_AND_PLAY_TRACKERS_ENABLED, Boolean.toString(device.isFilterPlugAndPlayTrackersEnabled()));
            jedis.hmset(device.getId(), map);
        }
    }

    @Override
    public void setIpAddressesFixed(boolean fixed) {
        try (Jedis jedis = pool.getResource()) {
            for (String deviceId : getDeviceIds()) {
                Map<String, String> map = jedis.hgetAll(deviceId);
                map.put(KEY_DHCP_FIXED_IP, (fixed ? VALUE_TRUE : VALUE_FALSE));
                jedis.hmset(deviceId, map);
            }
        }
    }

    @Override
    public boolean isIpFixedByDefault() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_DHCP_IP_FIXED_BY_DEFAULT);
            if (value == null) {
                return false;
            }
            return value.equals(VALUE_TRUE);
        }
    }

    @Override
    public boolean isExpertMode() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_NETWORK_IS_EXPERT_MODE);
            return value == null ? false : value.equals(VALUE_TRUE);
        }
    }

    @Override
    public void setIsExpertMode(boolean expert) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_NETWORK_IS_EXPERT_MODE, (expert ? VALUE_TRUE : VALUE_FALSE));
        }
    }

    @Override
    public void setIpFixedByDefault(boolean ipFixedByDefault) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_DHCP_IP_FIXED_BY_DEFAULT, ipFixedByDefault ? VALUE_TRUE
                    : VALUE_FALSE);
        }
    }

    @Override
    public Set<String> getUserIds() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.smembers(KEY_USERS);
        }
    }

    @Override
    @Deprecated
    public User getUser(String userId) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> user = jedis.hgetAll(userId);
            if (user.isEmpty()) {
                return null;
            }
            User result = new User();
            result.setId(userId);
            result.setName(user.get(KEY_NAME));
            return result;
        }
    }

    @Override
    @Deprecated
    public void save(User user) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put(KEY_NAME, user.getName());

            jedis.hmset(user.getId(), map);
        }
    }

    @Override
    @Deprecated
    public User addUser(User user) {
        Set<String> userIds = getUserIds();
        for (int i = 1; i <= MAX_DATABASES; i++) {
            String newId = User.ID_PREFIX + i;
            if (!userIds.contains(newId)) {
                user.setId(newId);
                try (Jedis jedis = pool.getResource()) {
                    jedis.sadd(KEY_USERS, newId);
                }
                save(user);
                return user;
            }
        }
        return null;
    }

    @Override
    @Deprecated
    public void delete(User user) {
        try (Jedis jedis = pool.getResource()) {
            jedis.srem(KEY_USERS, user.getId());
            jedis.del(user.getId());
        }
    }

    @Override
    public void delete(Device device) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(device.getId());
        }
    }

    @Override
    public String getVersion() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_VERSION);
        }
    }

    @Override
    public void setVersion(String version) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_VERSION, version);
        }
    }

    @Override
    public void setCurrentNetworkState(NetworkStateId networkState) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_NETWORK_STATE, networkState.name());
        }
    }

    @Override
    public NetworkStateId getCurrentNetworkState() {
        try (Jedis jedis = pool.getResource()) {
            String networkStateName = jedis.get(KEY_NETWORK_STATE);
            if (networkStateName == null) {
                return NetworkStateId.PLUG_AND_PLAY; // the default mode
            }
            return NetworkStateId.valueOf(networkStateName);
        }
    }

    @Override
    public void setDhcpRange(DhcpRange range) {
        try (Jedis jedis = pool.getResource()) {
            if (range.getFirstIpAddress() != null) {
                jedis.hset(KEY_DHCP_RANGE, KEY_IP_ADDRESS_FIRST, range.getFirstIpAddress());
            }
            if (range.getLastIpAddress() != null) {
                jedis.hset(KEY_DHCP_RANGE, KEY_IP_ADDRESS_LAST, range.getLastIpAddress());
            }
        }
    }

    @Override
    public DhcpRange getDhcpRange() {
        try (Jedis jedis = pool.getResource()) {
            String firstIpAddress = jedis.hget(KEY_DHCP_RANGE, KEY_IP_ADDRESS_FIRST);
            String lastIpAddress = jedis.hget(KEY_DHCP_RANGE, KEY_IP_ADDRESS_LAST);
            return new DhcpRange(firstIpAddress, lastIpAddress);
        }
    }

    @Override
    public void clearDhcpRange() {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_DHCP_RANGE);
        }
    }

    @Override
    public void createSnapshot() {
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.bgsave();
            LOG.debug("BGSave returned: {}", result);

        } catch (JedisDataException e) {
            LOG.error("Cannot save DB: {}", e.getMessage());
            //FIXME handle exception
            //is appearing when there is already a save instance running
        }
    }

    @Override
    public void saveSynchronously() {
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.save();
            LOG.debug("Save returned: {}", result);

        } catch (JedisDataException e) {
            LOG.error("Cannot save DB synchronously: {}", e.getMessage());
            //FIXME handle exception
            //is appearing when there is already a save instance running
        }
    }

    @Override
    public void saveLastUpdateTime(LocalDateTime lastUpdate) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_LASTUPDATE, lastUpdate.toString());
        }
    }

    @Override
    public LocalDateTime getLastUpdateTime() {
        try (Jedis jedis = pool.getResource()) {
            String lastUpdateString = jedis.get(KEY_LASTUPDATE);
            if (lastUpdateString != null) {
                try {
                    LocalDateTime lastUpdate = LocalDateTime.parse(lastUpdateString);
                    return lastUpdate;
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
            return null;
        }
    }

    @Override
    public AutomaticUpdaterConfiguration getAutomaticUpdateConfig() {
        try (Jedis jedis = pool.getResource()) {
            String configJSON = jedis.get(KEY_AUTOUPDATE);
            if (configJSON == null)
                return null;
            AutomaticUpdaterConfiguration config = null;
            try {
                config = objectMapper.readValue(configJSON, AutomaticUpdaterConfiguration.class);

            } catch (IOException e) {
                LOG.error("Error in automatic update config", e);
            }
            return config;
        }
    }

    @Override
    public void save(AutomaticUpdaterConfiguration configuration) {
        if (configuration != null) {
            try (Jedis jedis = pool.getResource()) {
                String configJSON = configuration.toJSONString();
                jedis.set(KEY_AUTOUPDATE, configJSON);
            }
        }
    }

    @Override
    public void setAutomaticUpdatesActivated(boolean activated) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_AUTOUPDATE_ACTIVE, activated ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public String getAutomaticUpdatesActivated() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_AUTOUPDATE_ACTIVE);
        }
    }

    @Override
    public boolean getSSLEnabledState() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_SSL_ENABLED);

            // default if not set is ON:
            if (value == null) {
                return false;
            }

            return value.equals(VALUE_TRUE);
        }

    }

    @Override
    public void setSSLEnabledState(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_SSL_ENABLED, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public void setOpenVpnServerState(boolean state) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_OPENVPN_SERVER_ENABLED, state ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public void setOpenVpnServerHost(String host) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_OPENVPN_SERVER_HOST, host);
        }
    }

    @Override
    public String getOpenVpnServerHost() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_OPENVPN_SERVER_HOST);
        }
    }

    @Override
    public Integer getOpenVpnMappedPort() {
        try (Jedis jedis = pool.getResource()) {
            String num = jedis.get(KEY_OPENVPN_MAPPED_PORT);
            if (num != null) {
                return Integer.valueOf(num);
            }
            return null;
        }
    }

    @Override
    public void setOpenVpnMappedPort(Integer port) {
        if (port != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_OPENVPN_MAPPED_PORT, port.toString());
            }
        }
    }

    @Override
    public PortForwardingMode getOpenVpnPortForwardingMode() {
        try (Jedis jedis = pool.getResource()) {
            String mode = jedis.get(KEY_OPENVPN_PORT_FORWARDING_MODE);
            if (mode != null) {
                return PortForwardingMode.valueOf(mode);
            }
            return PortForwardingMode.getDefault();
        }
    }

    @Override
    public void setOpenVpnPortForwardingMode(PortForwardingMode mode) {
        if (mode != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_OPENVPN_PORT_FORWARDING_MODE, mode.toString());
            }
        }
    }

    @Override
    public ExternalAddressType getOpenVpnExternalAddressType() {
        try (Jedis jedis = pool.getResource()) {
            String type = jedis.get(KEY_OPENVPN_EXTERNAL_ADDRESS_TYPE);
            if (type != null) {
                return ExternalAddressType.valueOf(ExternalAddressType.class, jedis.get(KEY_OPENVPN_EXTERNAL_ADDRESS_TYPE));
            }
            return null;

        }
    }

    @Override
    public void setOpenVpnExternalAddressType(ExternalAddressType type) {
        if (type != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_OPENVPN_EXTERNAL_ADDRESS_TYPE, type.toString());
            }
        }
    }

    @Override
    public boolean getOpenVpnServerState() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_OPENVPN_SERVER_ENABLED);

            // default if not set is OFF:
            if (value == null) {
                return false;
            }

            return value.equals(VALUE_TRUE);
        }
    }

    @Override
    public void setOpenVpnServerFirstRun(boolean state) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_OPENVPN_FIRST_RUN, state ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public boolean getOpenVpnServerFirstRun() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_OPENVPN_FIRST_RUN);

            // default if not set is ON:
            if (value == null) {
                return true;
            }

            return value.equals(VALUE_TRUE);
        }
    }

    @Override
    public void saveCurrentTorExitNodes(Set<String> selectedCountries) {
        try (Jedis jedis = pool.getResource()) {
            //Build JSON from countries
            try {
                //create JSON from countries set
                String countriesJSON = objectMapper.writeValueAsString(selectedCountries);
                //save JSON
                jedis.set(KEY_TOR_CURRENT_EXIT_NODES, countriesJSON);
            } catch (JsonProcessingException e) {
                LOG.error("Error while saving current tor exit nodes", e);
            }

        }
    }

    @Override
    public Set<String> getCurrentTorExitNodes() {
        try (Jedis jedis = pool.getResource()) {
            //load JSON
            String countriesJSON = jedis.get(KEY_TOR_CURRENT_EXIT_NODES);
            if (countriesJSON == null)
                return null;
            //create Set<String> from JSON
            try {
                HashSet<String> countries = objectMapper.readValue(countriesJSON, HashSet.class);
                return countries;
            } catch (IOException e) {
                LOG.error("Error'while getting current to exit nodes", e);
            }
        }
        return null;
    }

    @Override
    public void setWebRTCBlockingState(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_WEBRTC_BLOCKING_STATE, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public boolean getWebRTCBlockingState() {
        try (Jedis jedis = pool.getResource()) {
            String state = jedis.get(KEY_WEBRTC_BLOCKING_STATE);
            if (state != null) {
                return state.equals(VALUE_TRUE);
            }
        }
        //default
        return false;
    }

    @Override
    public void setHTTPRefererRemovingState(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_HTTP_REFERER_REMOVE_STATE, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public boolean getHTTPRefererRemovingState() {
        try (Jedis jedis = pool.getResource()) {
            String state = jedis.get(KEY_HTTP_REFERER_REMOVE_STATE);
            if (state != null) {
                return state.equals(VALUE_TRUE);
            }
        }
        //default
        return false;
    }

    @Override
    public void setGoogleCaptivePortalRedirectorState(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_GOOGLE_CAPTIVE_PORTAL_CHECK_RESPONDER_STATE, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    /**
     * Returns true if eBlocker should respond to Google captive portal check requests.
     * <p>
     * If the option is not set in Redis, true is returned.
     */
    @Override
    public boolean getGoogleCaptivePortalRedirectorState() {
        try (Jedis jedis = pool.getResource()) {
            String state = jedis.get(KEY_GOOGLE_CAPTIVE_PORTAL_CHECK_RESPONDER_STATE);
            if (state != null) {
                return state.equals(VALUE_TRUE);
            }
        }
        //default
        return true;
    }

    @Override
    public void setDntHeaderState(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_DNT_HEADER_STATE, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    /**
     * Returns true the the eBlocker should add the DNT Header
     * <p>
     * If the option is not set in Redis, false is returned.
     */
    @Override
    public boolean getDntHeaderState() {
        try (Jedis jedis = pool.getResource()) {
            String state = jedis.get(KEY_DNT_HEADER_STATE);
            if (state != null) {
                return state.equals(VALUE_TRUE);
            }
        }
        // default
        return false;
    }

    @Override
    public void setDoNotShowReminder(boolean show) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_DO_NOT_SHOW_REMINDER, show ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    /**
     * Returns true if the eBlocker should not display the license expiration reminder to the user
     * <p>
     * If the option is not set in Redis, false is returned.
     */
    @Override
    public boolean isDoNotShowReminder() {
        try (Jedis jedis = pool.getResource()) {
            String show = jedis.get(KEY_DO_NOT_SHOW_REMINDER);
            if (show != null) {
                return show.equals(VALUE_TRUE);
            }
        } // default
        return false;
    }

    @Override
    public void setShowSplashScreen(boolean show) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_SHOW_SPLASH_SCREEN, show ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public boolean isShowSplashScreen() {
        try (Jedis jedis = pool.getResource()) {
            String show = jedis.get(KEY_SHOW_SPLASH_SCREEN);
            if (show != null) {
                return show.equals(VALUE_TRUE);
            }
        } // default
        return true;
    }

    @Override
    public void setAutoEnableNewDevices(boolean autoEnableNewDevices) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_AUTO_ENABLE_NEW_DEVICES, autoEnableNewDevices ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public boolean isAutoEnableNewDevices() {
        try (Jedis jedis = pool.getResource()) {
            String autoEnableNewDevices = jedis.get(KEY_AUTO_ENABLE_NEW_DEVICES);
            if (autoEnableNewDevices != null) {
                return autoEnableNewDevices.equals(VALUE_TRUE);
            }
        } // default
        return true;
    }

    @Override
    public CompressionMode getCompressionMode() {
        try (Jedis jedis = pool.getResource()) {
            return CompressionMode.failSafeValueOf(jedis.get(KEY_COMPRESSION_MODE));
        }
    }

    @Override
    public void setCompressionMode(CompressionMode compressionMode) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_COMPRESSION_MODE, compressionMode.name());
        }
    }

    @Override
    public boolean getSslRecordErrors() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_SSL_RECORD_ERRORS);
            return value != null && Boolean.parseBoolean(value);
        }
    }

    @Override
    public void setSslRecordErrors(boolean recordSslErrors) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_SSL_RECORD_ERRORS, Boolean.toString(recordSslErrors));
        }
    }

    @Override
    public ZonedDateTime getLastSSLWhitelistDate() {
        try (Jedis jedis = pool.getResource()) {
            String lastModified = jedis.get(KEY_LAST_SSL_DEFAULT_WHITELIST_UPDATE);
            if (lastModified != null) {
                return ZonedDateTime.parse(lastModified);
            }
        }
        return null;
    }

    @Override
    public void setLastModifiedSSLDefaultWhitelist(ZonedDateTime defaultSSLWhitelistLastModified) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_LAST_SSL_DEFAULT_WHITELIST_UPDATE, defaultSSLWhitelistLastModified.toString());
        }
    }

    @Override
    public Language getCurrentLanguage() {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> map = jedis.hgetAll(KEY_LANGUAGE);
            String langID = map.get("ID");
            String langName = map.get("name");
            if (langID != null && !"".equals(langID) && langName != null) {
                return new Language(langID, langName);
            }
        }
        return new Language("en", "English");
    }

    @Override
    public void setCurrentLanguage(Language language) {
        if (language != null) {
            try (Jedis jedis = pool.getResource()) {
                Map<String, String> map = new HashMap<>();

                map.put("ID", language.getId());
                map.put("name", language.getName());

                jedis.hmset(KEY_LANGUAGE, map);
            }
        }
    }

    @Override
    public ZonedDateTime getLastAppWhitelistModulesDate() {
        try (Jedis jedis = pool.getResource()) {
            String lastModified = jedis.get(KEY_LAST_APPMODULES_DEFAULT_FILE_UPDATE);
            if (lastModified != null) {
                return ZonedDateTime.parse(lastModified);
            }
        }
        return null;
    }

    @Override
    public void setLastAppWhitelistModuleDate(ZonedDateTime appWhitelistModulesJSONFileLastModified) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_LAST_APPMODULES_DEFAULT_FILE_UPDATE, appWhitelistModulesJSONFileLastModified.toString());
        }
    }

    @Override
    public void setTimezone(String posixString) {
        if (posixString != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_TIMEZONE, posixString);
            }
        }
    }

    @Override
    public String getTimezone() {
        String timezone = null;
        try (Jedis jedis = pool.getResource()) {
            timezone = jedis.get(KEY_TIMEZONE);
        }
        return timezone;
    }

    public AppWhitelistModule.State getAppModuleState(int moduleID) {
        if (moduleID >= 0) {//only positive IDs allowed
            try (Jedis jedis = pool.getResource()) {
                String state = jedis.get("appmodule:" + moduleID);
                if (state == null) {
                    return AppWhitelistModule.State.DEFAULT;
                } else if (state.equals(VALUE_TRUE)) {
                    return AppWhitelistModule.State.ENABLED;
                }
                return AppWhitelistModule.State.DISABLED;
            }
        }
        return AppWhitelistModule.State.MODULE_NOT_FOUND;
    }

    @Override
    public void setAppWhitelistModuleStatus(int moduleID, boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("appmodule:" + moduleID, enabled ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    public Decision getRedirectDecision(String sessionId, String domain) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.hget("session:" + sessionId, "redirectDecision::" + domain);
            for (Decision decision : Decision.values()) {
                if (decision.toString().equals(value)) {
                    return decision;
                }
            }
            return Decision.NO_DECISION;
        }
    }

    @Override
    public void setRedirectDecision(String sessionId, String domain, Decision decision) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hset("session:" + sessionId, "redirectDecision::" + domain, decision.toString());
        }
    }

    @Override
    public void setPasswordHash(byte[] passwordHash) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("consolePassword", Base64.encodeBase64String(passwordHash));
        }
    }

    @Override
    public byte[] getPasswordHash() {
        try (Jedis jedis = pool.getResource()) {
            return Base64.decodeBase64(jedis.get("consolePassword"));
        }
    }

    @Override
    public void deletePasswordHash() {
        try (Jedis jedis = pool.getResource()) {
            jedis.del("consolePassword");
        }
    }

    @Override
    public String getListsPackageVersion() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get("lists-version");
        }
    }

    @Override
    public boolean getCleanShutdownFlag() {
        try (Jedis jedis = pool.getResource()) {
            String clean = jedis.get(KEY_CLEAN_SHUTDOWN);
            if (clean == null) {
                return true; // default if this feature is used for the first time: assume the last shutdown was clean
            }
            return clean.equals(VALUE_TRUE);
        }
    }

    @Override
    public void setCleanShutdownFlag(boolean cleanShutdown) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_CLEAN_SHUTDOWN, cleanShutdown ? VALUE_TRUE : VALUE_FALSE);
        }
    }

    @Override
    public String getResolvedDnsGateway() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_RESOLVED_DNS_GATEWAY);
        }
    }

    @Override
    public void setResolvedDnsGateway(String gateway) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_RESOLVED_DNS_GATEWAY, gateway);
        }
    }

    @Override
    public Integer getDhcpLeaseTime() {
        try (Jedis jedis = pool.getResource()) {
            String leaseTime = jedis.get(KEY_DHCP_LEASE_TIME);
            if (leaseTime != null) {
                return Integer.valueOf(leaseTime);
            }
            return null;
        }
    }

    @Override
    public void setDhcpLeaseTime(Integer leaseTime) {
        if (leaseTime != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_DHCP_LEASE_TIME, leaseTime.toString());
            }
        }
    }

    @Override
    public boolean isMalwareUrlFilterEnabled() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_MALWARE_FILTER_ENABLED);
            return value == null || Boolean.parseBoolean(value);
        }
    }

    @Override
    public void setMalwareUrlFilterEnabled(boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_MALWARE_FILTER_ENABLED, Boolean.toString(enabled));
        }
    }
}

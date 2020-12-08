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
package org.eblocker.server.common.data.events;

import org.eblocker.server.common.data.NetworkStateId;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Factory methods for events
 */
public class Events {
    private static final String KEY_USER_NAME = "userName";

    private static Event eventOfType(EventType type) {
        return eventOfType(type, Collections.emptyMap());
    }

    private static Event eventOfType(EventType type, Map<String, String> details) {
        Instant now = Instant.now();
        Event event = new Event();
        event.setType(type);
        event.setTimestamp(now);
        event.setEventDetails(details);
        return event;
    }

    public static Event networkInterfaceDown() {
        return eventOfType(EventType.NETWORK_INTERFACE_DOWN);
    }

    public static Event networkInterfaceUp() {
        return eventOfType(EventType.NETWORK_INTERFACE_UP);
    }

    public static Event powerFailure() {
        return eventOfType(EventType.POWER_FAILURE);
    }

    public static Event licenseUpgraded() {
        return eventOfType(EventType.LICENSE_UPGRADED);
    }

    public static Event licenseUpgradFailed() {
        return eventOfType(EventType.LICENSE_UPGRADE_FAILED);
    }

    public static Event networkModeChange(NetworkStateId current) {
        switch (current) {
            case PLUG_AND_PLAY:
                return eventOfType(EventType.NETWORK_MODE_PNP);
            case EXTERNAL_DHCP:
                return eventOfType(EventType.NETWORK_MODE_EXTERNAL_DHCP);
            case LOCAL_DHCP:
                return eventOfType(EventType.NETWORK_MODE_LOCAL_DHCP);

            default:
                break;
        }
        return null;
    }

    public static Event systemEvent(Boolean reboot) {
        if (reboot) {
            return eventOfType(EventType.REBOOT_CONSOLE);
        }
        return eventOfType(EventType.SHUTDOWN_CONSOLE);
    }

    public static Event serverIcapServerStarted() {
        return eventOfType(EventType.ICAP_SERVER_STARTED);
    }

    public static Event serverIcapServerStartedWithWarnings() {
        return eventOfType(EventType.ICAP_SERVER_STARTED_WITH_WARNINGS);
    }

    public static Event licenseExpired() {
        return eventOfType(EventType.LICENSE_EXPIRED);
    }

    public static Event updateEblockerOsInstalled() {
        return eventOfType(EventType.UPDATE_EBLOCKER_OS_INSTALLED);
    }

    public static Event redisBackupFailed() {
        return eventOfType(EventType.REDIS_BACKUP_FAILED);
    }

    public static Event redisBackupRestored() {
        return eventOfType(EventType.REDIS_BACKUP_RESTORED);
    }

    public static Event redisBackupRestoreFailed() {
        return eventOfType(EventType.REDIS_BACKUP_RESTORE_FAILED);
    }

    public static Event adminPasswordChanged(Map<String, String> details) {
        return eventOfType(details.containsKey(KEY_USER_NAME) ? EventType.ADMIN_PASSWORD_CHANGED_BY_USER
            : EventType.ADMIN_PASSWORD_CHANGED, details);
    }

    public static Event adminPasswordRemoved(Map<String, String> details) {
        return eventOfType(details.containsKey(KEY_USER_NAME) ? EventType.ADMIN_PASSWORD_REMOVED_BY_USER
            : EventType.ADMIN_PASSWORD_REMOVED, details);
    }

    public static Event adminPasswordReset(Map<String, String> details) {
        return eventOfType(details.containsKey(KEY_USER_NAME) ? EventType.ADMIN_PASSWORD_RESET_BY_USER
            : EventType.ADMIN_PASSWORD_RESET, details);
    }

    public static Event upnpPortForwardingFailed() {
        return eventOfType(EventType.UPNP_PORT_FORWARDING_FAILED);
    }
}

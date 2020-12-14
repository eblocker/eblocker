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

/**
 * The event type defines the text to display to the user.
 * For every type there must be an entry in the translation table.
 */
public enum EventType {
    NETWORK_INTERFACE_DOWN,
    NETWORK_INTERFACE_UP,
    POWER_FAILURE,

    LICENSE_UPGRADED,
    LICENSE_UPGRADE_FAILED,

    NETWORK_MODE_PNP,
    NETWORK_MODE_EXTERNAL_DHCP,
    NETWORK_MODE_LOCAL_DHCP,

    UPDATE_EBLOCKER_OS_INSTALLED,
    LICENSE_EXPIRED,
    REBOOT_CONSOLE,
    SHUTDOWN_CONSOLE,
    ICAP_SERVER_STARTED,
    ICAP_SERVER_STARTED_WITH_WARNINGS,

    REDIS_BACKUP_FAILED,
    REDIS_BACKUP_RESTORED,
    REDIS_BACKUP_RESTORE_FAILED,

    ADMIN_PASSWORD_CHANGED,
    ADMIN_PASSWORD_CHANGED_BY_USER,
    ADMIN_PASSWORD_REMOVED,
    ADMIN_PASSWORD_REMOVED_BY_USER,
    ADMIN_PASSWORD_RESET,
    ADMIN_PASSWORD_RESET_BY_USER,

    UPNP_PORT_FORWARDING_FAILED,
}

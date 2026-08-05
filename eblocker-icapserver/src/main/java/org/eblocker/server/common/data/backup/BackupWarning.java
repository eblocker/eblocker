/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.data.backup;

import java.util.Objects;

/**
 * Warnings that can occur during export or import of a backup.
 * They should be shown to the user.
 */
public class BackupWarning {
    private Id id;
    private ItemId itemId;
    private String itemName;

    public enum Id {
        LICENSE_CRYPTO_FAILURE,
        NO_PASSWORD_HTTPS_CA_NOT_IMPORTED,
        NO_PASSWORD_OPENVPN_SERVER_NOT_IMPORTED,
        NO_PASSWORD_OPENVPN_CLIENTS_NOT_IMPORTED,
        NO_PASSWORD_REGISTRATION_NOT_IMPORTED,
        UPNP_PORT_FORWARDING_FAILURE,
        ITEM_NOT_EXPORTED,
        ITEM_NOT_IMPORTED;
    }

    public enum ItemId {
        VPN_PROFILE;
    }

    public BackupWarning(Id id) {
        this.id = id;
    }

    public BackupWarning(Id id, ItemId itemId, String itemName) {
        this.id = id;
        this.itemId = itemId;
        this.itemName = itemName;
    }

    public Id getId() {
        return id;
    }

    public ItemId getItemId() {
        return itemId;
    }

    public void setItemId(ItemId itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        BackupWarning that = (BackupWarning) o;
        return id == that.id && itemId == that.itemId && Objects.equals(itemName, that.itemName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, itemId, itemName);
    }
}

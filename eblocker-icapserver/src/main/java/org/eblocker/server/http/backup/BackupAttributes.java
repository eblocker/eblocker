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
package org.eblocker.server.http.backup;

import java.util.jar.Attributes;

/**
 * Defines meta-data about a backup
 */
public class BackupAttributes {
    private static final String CURRENT_VERSION_KEY = "eBlocker-Backup-Version";
    private static final String CURRENT_SCHEMA_VERSION = "Schema-Version";
    private static final String FALLBACK_SCHEMA_VERSION = "0";
    private static final String PASSWORD_REQUIRED = "Password-Required";
    private static final String PASSWORD_REQUIRED_DEFAULT = "false";

    private final int version;
    private final int schemaVersion;
    private boolean passwordRequired = false;

    public BackupAttributes(int version, int schemaVersion, boolean passwordRequired) {
        this.version = version;
        this.schemaVersion = schemaVersion;
        this.passwordRequired = passwordRequired;
    }

    public BackupAttributes(Attributes attributes) {
        version = Integer.parseInt(attributes.getValue(CURRENT_VERSION_KEY));
        schemaVersion = Integer.parseInt(getOrDefault(attributes, CURRENT_SCHEMA_VERSION, FALLBACK_SCHEMA_VERSION));
        passwordRequired = Boolean.parseBoolean(getOrDefault(attributes, PASSWORD_REQUIRED, PASSWORD_REQUIRED_DEFAULT));
    }

    private String getOrDefault(Attributes attributes, String key, String defaultValue) {
        String value = attributes.getValue(key);
        return value != null ? value : defaultValue;
    }

    public void addToAttributes(Attributes attributes) {
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue(CURRENT_VERSION_KEY, String.valueOf(version));
        attributes.putValue(CURRENT_SCHEMA_VERSION, String.valueOf(schemaVersion));
        attributes.putValue(PASSWORD_REQUIRED, String.valueOf(passwordRequired));
    }

    public int getVersion() {
        return version;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }
}

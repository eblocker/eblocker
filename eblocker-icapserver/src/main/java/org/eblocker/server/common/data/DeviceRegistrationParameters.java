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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters users must enter to register their eBlocker.
 */
public class DeviceRegistrationParameters {
    private final String emailAddress;
    private final String deviceName;
    private final String licenseKey;
    private final String serialNumber;
    private final Boolean confirmed;
    private final String tosVersion;
    private final Boolean fallback;

    @JsonCreator
    public DeviceRegistrationParameters(
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("deviceName") String deviceName,
            @JsonProperty("licenseKey") String licenseKey,
            @JsonProperty("serialNumber") String serialNumber,
            @JsonProperty("confirmed") Boolean confirmed,
            @JsonProperty("tosVersion") String tosVersion,
            @JsonProperty("fallbackRegistration") boolean fallback) {
        this.emailAddress = emailAddress;
        this.deviceName = deviceName;
        this.licenseKey = licenseKey;
        this.serialNumber = serialNumber;
        this.confirmed = confirmed;
        this.tosVersion = tosVersion;
        this.fallback = fallback;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public Boolean isConfirmed() {
        return confirmed;
    }

    public String getTosVersion() {
        return tosVersion;
    }

    public Boolean getFallback() {
        return fallback;
    }
}

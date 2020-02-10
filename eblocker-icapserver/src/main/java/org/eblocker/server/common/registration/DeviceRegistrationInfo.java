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
package org.eblocker.server.common.registration;

import org.eblocker.registration.LicenseType;
import org.eblocker.registration.ProductInfo;

import java.util.Date;

public class DeviceRegistrationInfo {

    private final RegistrationState registrationState;

    private final Date deviceRegisteredAt;

    private final String deviceId;

    private final String deviceName;

    private final LicenseType licenseType;

    private final Date licenseNotValidAfter;

    private final boolean licenseLifetime;

    private final boolean licenseAboutToExpire;

    private final String deviceRegisteredBy;

    private final ProductInfo productInfo;

    private final boolean needsConfirmation;

    private final String[] confirmationMsgKeys;

    private final Object postRegistrationInformation;

    public DeviceRegistrationInfo(DeviceRegistrationProperties properties, ProductInfo productInfo, Object postRegistrationInformation) {
        this.registrationState = properties.getRegistrationState();
        this.deviceRegisteredAt = properties.getDeviceRegisteredAt();
        this.deviceId = properties.getDeviceId();
        this.deviceName = properties.getDeviceName();
        this.licenseType = properties.getLicenseType();
        this.licenseNotValidAfter = properties.getLicenseNotValidAfter();
        this.licenseLifetime = properties.isLicenseLifetime();
        this.licenseAboutToExpire = properties.isLicenseAboutToExpire();
        this.deviceRegisteredBy = properties.getDeviceRegisteredBy();
        this.productInfo = productInfo;
        this.needsConfirmation = false;
        this.confirmationMsgKeys = null;
        this.postRegistrationInformation = postRegistrationInformation;
    }

    public DeviceRegistrationInfo(boolean needsConfirmation, String[] confirmationMsgKeys, Object postRegistrationInformation) {
        this.registrationState = null;
        this.deviceRegisteredAt = null;
        this.deviceId = null;
        this.deviceName = null;
        this.licenseType = null;
        this.licenseNotValidAfter = null;
        this.licenseLifetime = false;
        this.licenseAboutToExpire = false;
        this.deviceRegisteredBy = null;
        this.productInfo = null;
        this.needsConfirmation = needsConfirmation;
        this.confirmationMsgKeys = confirmationMsgKeys;
        this.postRegistrationInformation = postRegistrationInformation;
    }

    public RegistrationState getRegistrationState() {
        return registrationState;
    }

    public Date getDeviceRegisteredAt() {
        return deviceRegisteredAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public LicenseType getLicenseType() {
        return licenseType;
    }

    public Date getLicenseNotValidAfter() {
        return licenseNotValidAfter;
    }

    public boolean isLicenseLifetime() {
        return licenseLifetime;
    }

    public boolean isLicenseAboutToExpire() {
        return licenseAboutToExpire;
    }

    public String getDeviceRegisteredBy() {
        return deviceRegisteredBy;
    }

    public ProductInfo getProductInfo() {
        return productInfo;
    }

    public boolean isNeedsConfirmation() {
        return this.needsConfirmation;
    }

    public String[] getConfirmationMsgKeys() {
        return this.confirmationMsgKeys;
    }

    public Object getPostRegistrationInformation() {
        return this.postRegistrationInformation;
    }
}

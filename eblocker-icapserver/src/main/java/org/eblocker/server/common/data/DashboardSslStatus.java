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

public class DashboardSslStatus {
    private Certificate currentCertificate = null;
    private DateTuple currentCertificateEndDate = null;
    private Certificate renewalCertificate = null;
    private DateTuple renewalCertificateEndDate = null;
    private boolean globalSslStatus = false;
    private boolean deviceSslStatus = false;
    private Boolean currentCertificateInstalled = false;
    private Boolean renewalCertificateInstalled = false;

    private String rootCertSerialNumber = null;
    private String renewalCertSerialNumber = null;

    // Should the dashboard execute the background check and show the installation status?
    private boolean executeSslBackgroundCheck = false;

    private Integer caRenewWeeks;

    public void setCurrentCertificate(Certificate cert) {
        currentCertificate = cert;
        currentCertificateEndDate = new DateTuple(cert.getNotAfter());
    }

    @JsonProperty
    public Certificate getCurrentCertificate() {
        return currentCertificate;
    }

    @JsonProperty
    public DateTuple getCurrentCertificateEndDate() {
        return currentCertificateEndDate;
    }

    public void setRenewalCertificate(Certificate cert) {
        renewalCertificate = cert;
        renewalCertificateEndDate = new DateTuple(cert.getNotAfter());
    }

    @JsonProperty
    public Certificate getRenewalCertificate() {
        return renewalCertificate;
    }

    @JsonProperty
    public DateTuple getRenewalCertificateEndDate() {
        return renewalCertificateEndDate;
    }

    @JsonProperty
    public boolean isCurrentCertificateAvailable() {
        return currentCertificate != null;
    }

    @JsonProperty
    public boolean isRenewalCertificateAvailable() {
        return renewalCertificate != null;
    }

    public void setGlobalSslStatus(boolean status) {
        globalSslStatus = status;
    }

    @JsonProperty
    public boolean getGlobalSslStatus() {
        return globalSslStatus;
    }

    public void setDeviceSslStatus(boolean status) {
        deviceSslStatus = status;
    }

    @JsonProperty
    public boolean getDeviceSslStatus() {
        return deviceSslStatus;
    }

    public void setCurrentCertificateInstalled(Boolean installed) {
        currentCertificateInstalled = installed;
    }

    @JsonProperty
    public Boolean isCurrentCertificateInstalled() {
        return currentCertificateInstalled;
    }

    public void setRenewalCertificateInstalled(Boolean installed) {
        renewalCertificateInstalled = installed;
    }

    @JsonProperty
    public Boolean isRenewalCertificateInstalled() {
        return renewalCertificateInstalled;
    }

    @JsonProperty
    public String getRootCertSerialNumber() {
        return rootCertSerialNumber;
    }

    public void setRootCertSerialNumber(String rootCertSerialNumber) {
        this.rootCertSerialNumber = rootCertSerialNumber;
    }

    @JsonProperty
    public String getRenewalCertSerialNumber() {
        return renewalCertSerialNumber;
    }

    public void setRenewalCertSerialNumber(String renewalCertSerialNumber) {
        this.renewalCertSerialNumber = renewalCertSerialNumber;
    }

    @JsonProperty
    public boolean isExecuteSslBackgroundCheck() {
        return executeSslBackgroundCheck;
    }

    public void setExecuteSslBackgroundCheck(boolean executeSslBackgroundCheck) {
        this.executeSslBackgroundCheck = executeSslBackgroundCheck;
    }

    @JsonProperty
    public Integer getCaRenewWeeks() {
        return caRenewWeeks;
    }

    public void setCaRenewWeeks(Integer caRenewWeeks) {
        this.caRenewWeeks = caRenewWeeks;
    }
}

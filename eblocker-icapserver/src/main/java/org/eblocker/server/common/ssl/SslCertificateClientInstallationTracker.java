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
package org.eblocker.server.common.ssl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@SubSystemService(SubSystem.HTTPS_SERVER)
public class SslCertificateClientInstallationTracker {

    private static final Logger log = LoggerFactory.getLogger(SslCertificateClientInstallationTracker.class);

    public enum Status {NOT_AVAILABLE, UNKNOWN, INSTALLED, NOT_INSTALLED}

    private final SslService sslService;

    private BigInteger caSerialNumber;
    private BigInteger renewalCaSerialNumber;

    private Map<Key, Boolean> certificateStatus;

    @Inject
    public SslCertificateClientInstallationTracker(SslService sslService) {
        this.sslService = sslService;
        certificateStatus = new ConcurrentHashMap<>(64, 0.75f, 4);

        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                updateSerialNumbers();
            }

            @Override
            public void onCaChange() {
                updateSerialNumbers();
            }

            @Override
            public void onRenewalCaChange() {
                updateSerialNumbers();
            }
        });

    }

    public void markCertificateAsInstalled(String deviceId, String userAgent, BigInteger serialNumber, boolean isInstalled) {
        log.debug("marking certificate {} as {} for {} / {}", serialNumber, isInstalled, deviceId, userAgent);
        certificateStatus.put(new Key(serialNumber, deviceId, userAgent), isInstalled);
    }

    /**
     * Checks if the current ca is installed on device / useragent.
     *
     * @param deviceId
     * @param userAgent
     * @return true or false if installation status is known otherwise null
     */
    public Status isCaCertificateInstalled(String deviceId, String userAgent) {
        return isCertificateInstalled(caSerialNumber, deviceId, userAgent);
    }

    /**
     * Checks if the future ca is installed on device / useragent.
     *
     * @param deviceId
     * @param userAgent
     * @return true or false if installation status is known otherwise null
     */
    public Status isFutureCaCertificateInstalled(String deviceId, String userAgent) {
        return isCertificateInstalled(renewalCaSerialNumber, deviceId, userAgent);
    }

    private Status isCertificateInstalled(BigInteger serialNumber, String deviceId, String userAgent) {
        if (serialNumber == null) {
            return Status.NOT_AVAILABLE;
        }

        Boolean isInstalled = certificateStatus.get(new Key(serialNumber, deviceId, userAgent));
        if (isInstalled == null) {
            return Status.UNKNOWN;
        } else if (isInstalled) {
            return Status.INSTALLED;
        }
        return Status.NOT_INSTALLED;
    }

    private void updateSerialNumbers() {
        caSerialNumber = getSerial(sslService.getCa());
        renewalCaSerialNumber = getSerial(sslService.getRenewalCa());
        log.debug("updated serial numbers, ca: {} future ca: {}", caSerialNumber, renewalCaSerialNumber);

        long start = System.currentTimeMillis();
        int initialSize = certificateStatus.size();
        Iterator<Key> i = certificateStatus.keySet().iterator();
        while (i.hasNext()) {
            BigInteger serial = i.next().getSerialNumber();
            if (!serial.equals(caSerialNumber) && !serial.equals(renewalCaSerialNumber)) {
                i.remove();
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        int size = certificateStatus.size();
        int removed = initialSize - size;
        log.debug("Removed {} from and kept {} in cache in {}ms.", removed, size, elapsed);
    }

    private BigInteger getSerial(EblockerCa ca) {
        return ca != null ? ca.getCertificate().getSerialNumber() : null;
    }

    public static class Key {
        private BigInteger serialNumber;
        private String deviceId;
        private String userAgent;

        public Key(BigInteger serialNumber, String deviceId, String userAgent) {
            this.serialNumber = serialNumber;
            this.deviceId = deviceId;
            this.userAgent = userAgent;
        }

        public BigInteger getSerialNumber() {
            return serialNumber;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getUserAgent() {
            return userAgent;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (o == this) {
                return true;
            }

            Key that = (Key) o;
            return this.serialNumber.equals(that.getSerialNumber()) && this.deviceId.equals(that.getDeviceId()) && userAgent.equals(that.getUserAgent());
        }

        @Override
        public int hashCode() {
            int result = serialNumber.hashCode();
            result = 31 * result + deviceId.hashCode();
            result = 31 * result + userAgent.hashCode();
            return result;
        }
    }
}

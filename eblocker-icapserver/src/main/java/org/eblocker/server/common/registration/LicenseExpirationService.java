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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;

import java.util.ArrayList;
import java.util.List;

@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = 50)
public class LicenseExpirationService {
    private final List<LicenseExpirationListener> listeners = new ArrayList<>();
    private final DeviceRegistrationProperties deviceRegistrationProperties;

    @Inject
    public LicenseExpirationService(DeviceRegistrationProperties deviceRegistrationProperties) {
        this.deviceRegistrationProperties = deviceRegistrationProperties;
    }

    @SubSystemInit
    public void init() {
        isExpired();
    }

    public boolean isExpired() {
        if (deviceRegistrationProperties.isLicenseExpired()) {
            notifyListeners();
            return true;
        }
        return false;
    }

    public void addListener(LicenseExpirationListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(listener -> listener.onChange());
    }

    public interface LicenseExpirationListener {
        void onChange();
    }
}

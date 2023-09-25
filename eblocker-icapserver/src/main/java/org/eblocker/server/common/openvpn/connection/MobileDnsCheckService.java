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
package org.eblocker.server.common.openvpn.connection;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.util.Ip4Utils;
import org.eblocker.server.http.service.OpenVpnServerService;

@Singleton
public class MobileDnsCheckService {

    private final DeviceRegistrationClient deviceRegistrationClient;
    private final OpenVpnServerService openVpnServerService;

    @Inject
    public MobileDnsCheckService(DeviceRegistrationClient deviceRegistrationClient,
                                 OpenVpnServerService openVpnServerService) {
        this.deviceRegistrationClient = deviceRegistrationClient;
        this.openVpnServerService = openVpnServerService;
    }

    public boolean check() {
        String hostname = openVpnServerService.getOpenVpnServerHost();
        if (Strings.isNullOrEmpty(hostname) || Ip4Utils.isIPAddress(hostname)) {
            return true;
        }
        return deviceRegistrationClient.requestMobileDnsCheck(hostname);
    }
}

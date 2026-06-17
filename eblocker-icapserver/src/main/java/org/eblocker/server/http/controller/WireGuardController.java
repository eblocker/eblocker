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
package org.eblocker.server.http.controller;

import org.eblocker.server.common.data.vpn.VpnConfigurationViewModel;
import org.eblocker.server.common.data.vpn.VpnProfile;
import org.eblocker.server.common.data.vpn.VpnStatus;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Collection;

public interface WireGuardController {
    //
    // profile management
    //
    Collection<VpnProfile> getProfiles(Request request, Response response);

    VpnProfile createProfile(Request request, Response response);

    VpnProfile getProfile(Request request, Response response);

    VpnProfile updateProfile(Request request, Response response);

    void deleteProfile(Request request, Response response);

    VpnConfigurationViewModel getProfileConfig(Request request, Response response);

    VpnConfigurationViewModel uploadProfileConfig(Request request, Response response);

    VpnConfigurationViewModel uploadProfileConfigOption(Request request, Response response);

    //
    // control / runtime information
    //
    VpnStatus getVpnStatusByDevice(Request request, Response response);

    VpnStatus getVpnStatus(Request request, Response response);

    VpnStatus setVpnStatus(Request request, Response response);

    boolean getVpnDeviceStatus(Request request, Response response);

    void setVpnDeviceStatus(Request request, Response response);

    void setVpnThisDeviceStatus(Request request, Response response);
}

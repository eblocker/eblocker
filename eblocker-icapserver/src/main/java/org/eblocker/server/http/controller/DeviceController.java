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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IconSettings;
import org.eblocker.server.common.data.ShowWelcomeFlags;
import org.eblocker.server.common.util.RemainingPause;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;

public interface DeviceController {
    Object deleteDevice(Request request, Response response);

    List<Device> getAllDevices(Request request, Response response);

    List<Device> getOperatingUserDevices(Request request, Response response);

    Device getDeviceById(Request request, Response response);

    Object updateDevice(Request request, Response response);

    Object updateDeviceDashboard(Request request, Response response);

    Object scanDevices(Request request, Response response);

    boolean isScanningAvailable(Request request, Response response);

    Long getScanningInterval(Request request, Response response);

    void setScanningInterval(Request request, Response response);

    Boolean isAutoEnableNewDevices(Request request, Response response);

    void setAutoEnableNewDevices(Request request, Response response);

    void setAutoEnableNewDevicesAndResetExisting(Request request, Response response);

    RemainingPause getPauseByDeviceId(Request request, Response response);

    RemainingPause setPauseByDeviceId(Request request, Response response);

    RemainingPause getPauseCurrentDevice(Request request, Response response);

    RemainingPause pauseCurrentDevice(Request request, Response response);

    RemainingPause pauseCurrentDeviceIfNotYetPausing(Request request, Response response);

    boolean getShowWarnings(Request request, Response response);

    Object postShowWarnings(Request request, Response response);

    void logoutUserFromDevice(Request request, Response response);

    Device getCurrentDevice(Request request, Response response);

    boolean getPauseDialogStatus(Request request, Response response);

    boolean getPauseDialogStatusDoNotShowAgain(Request request, Response response);

    void updatePauseDialogStatus(Request request, Response response);

    void updatePauseDialogStatusDoNotShowAgain(Request request, Response response);

    IconSettings getIconSettings(Request request, Response response);

    IconSettings setIconSettings(Request request, Response response);

    IconSettings resetIconSettings(Request request, Response response);

    Device.DisplayIconPosition setIconPosition(Request request, Response response);

    void updateDeviceDnsAdsEnabledStatus(Request request, Response response);

    void updateDeviceDnsTrackersEnabledStatus(Request request, Response response);

    Device resetDevice(Request request, Response response);

    ShowWelcomeFlags updateShowWelcomeFlags(Request request, Response response);
}

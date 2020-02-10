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

import org.eblocker.server.common.data.UserModuleTransport;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.messagecenter.IconState;
import org.eblocker.server.http.model.DeviceDTO;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Map;

public interface ControlBarController {
    String getConsoleUrl(Request request, Response response);

    String getConsoleIp(Request request, Response response);

    UserProfileModule getUserProfile(Request request, Response response);

    UserModuleTransport getUser(Request request, Response response);

    Map<Integer, UserModuleTransport> getUsers(Request request, Response response);

    DeviceDTO getDevice(Request request, Response response);

    void setOperatingUser(Request request, Response response);

    Boolean getDeviceRestrictions(Request request, Response response);

    IconState getIconState(Request request, Response response);
}

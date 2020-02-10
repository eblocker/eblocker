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
package org.eblocker.server.upnp;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.igd.callback.PortMappingAdd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class UpnpPortForwardingAdd extends PortMappingAdd {
    private static final Logger log = LoggerFactory.getLogger(UpnpPortForwardingAdd.class);
    private UpnpPortForwardingResult result;
    private Service service;
    private UpnpManagementService callingService;

    @Inject
    public UpnpPortForwardingAdd(@Assisted Service service, @Assisted ControlPoint controlPoint,
            @Assisted UpnpPortForwarding portForwarding, @Assisted UpnpManagementService callingService) {
        super(service, controlPoint, portForwarding);
        result = new UpnpPortForwardingResult(portForwarding);
        this.service = service;
        this.callingService = callingService;
    }

    @Override
    public void success(ActionInvocation invocation) {
        log.info("Port mapping added: {}", result.getCorrespondingPortForwarding());
        callingService.addOrUpdateForwardingForService(result.getCorrespondingPortForwarding(), service);
        result.setSuccess(true);
    }

    @Override
    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
        log.error("Port mapping failed: {}, Reason: {}", result.getCorrespondingPortForwarding(), defaultMsg);
        result.setSuccess(false);
        result.setErrorMsg(defaultMsg);
    }

    public UpnpPortForwardingResult getResult() {
        return result;
    }
}

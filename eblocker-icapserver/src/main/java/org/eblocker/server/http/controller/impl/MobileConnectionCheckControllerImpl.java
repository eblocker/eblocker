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
package org.eblocker.server.http.controller.impl;

import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.openvpn.connection.MobileConnectionCheckService;
import org.eblocker.server.common.openvpn.connection.MobileConnectionCheckStatus;
import org.eblocker.server.http.controller.MobileConnectionCheckController;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.ws.rs.InternalServerErrorException;

import org.restexpress.Request;
import org.restexpress.Response;

@Singleton
public class MobileConnectionCheckControllerImpl implements MobileConnectionCheckController {

    private final MobileConnectionCheckService testService;

    @Inject
    public MobileConnectionCheckControllerImpl(MobileConnectionCheckService testService) {
        this.testService = testService;
    }

    @Override
    public void start(Request request, Response response) {
        try{
        testService.start();
        } catch (UpnpPortForwardingException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public void stop(Request request, Response response) {
        testService.stop();
    }

    @Override
    public MobileConnectionCheckStatus getStatus(Request request, Response response) {
        return testService.getStatus();
    }
}

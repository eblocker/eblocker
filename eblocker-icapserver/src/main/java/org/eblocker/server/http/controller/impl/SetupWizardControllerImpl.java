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

import java.util.Map;

import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.http.controller.SetupWizardController;
import org.eblocker.server.http.service.RegistrationServiceAvailabilityCheck;

import org.eblocker.server.common.data.SetupWizardInfo;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.app.DeviceProperties;
import com.google.inject.Inject;

/** This controller can just answer the question if the setup wizard was done before
 *
 */
public class SetupWizardControllerImpl implements SetupWizardController {

    private static final Logger log = LoggerFactory.getLogger(SetupWizardControllerImpl.class);

    private final DeviceProperties deviceProperties;
    private final DeviceRegistrationProperties registrationProperties;
    private final RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck;


    @Inject
    public SetupWizardControllerImpl(DeviceProperties deviceProperties,
                                     DeviceRegistrationProperties deviceRegistration,
                                     RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck){
        this.deviceProperties = deviceProperties;
        this.registrationProperties = deviceRegistration;
        this.registrationServiceAvailabilityCheck = registrationServiceAvailabilityCheck;
    }

    @Override
    public SetupWizardInfo getInfo(Request request, Response response){
        return new SetupWizardInfo(
            registrationProperties.getRegistrationState() == RegistrationState.NEW,
            deviceProperties.isSerialNumberAvailable(),
            deviceProperties.getSerialNumberPattern(),
            deviceProperties.getSerialnumberExample(),
            registrationServiceAvailabilityCheck.isRegistrationAvailable()
        );
    }

    /**
     * Did the user finish the setup wizard and configure everything ready to start, like registering the device and
     * setting the language and so on...might be important to know, so that the setup wizard = bootstrap state is not shown all the time
     *
     * @param request
     * @param response
     * @return
     */
    @Override
    public boolean didUserFinishSetupWizard(Request request, Response response){
        boolean result = registrationProperties.getRegistrationState() != RegistrationState.NEW;
        log.debug("User already finished setup wizard?: "+result);
        return result;
    }



    /**
     * Should the wizard ask for the serial number, or not?
     * @param request
     * @param response
     * @return
     */
    @Override
    public Object askForSerialNumber(Request request, Response response){
        return deviceProperties.isSerialNumberAvailable();
    }

    /**
     * Get example for serial number
     * @param request
     * @param response
     * @return
     */
    @Override
    public Object getSerialNumberExample(Request request, Response response){
        return deviceProperties.getSerialnumberExample();
    }

    /**
     * Check if the serial number is valid (right format)
     * @param request
     * @param response
     * @return
     */
    @Override
    public Object checkSerialNumber(Request request, Response response){
        Map<String,String> map = request.getBodyAs(Map.class);
        String serialNumber = map.get("deviceSerialNumber");
        if(serialNumber == null){
            return false;
        }
        boolean result = deviceProperties.isSerialNumberMatching(serialNumber);
        if(result){
            log.info("Serial number is well formatted.");
        }
        else{
            log.info("Serial number is in wrong format.");
        }

        return result;
    }
}

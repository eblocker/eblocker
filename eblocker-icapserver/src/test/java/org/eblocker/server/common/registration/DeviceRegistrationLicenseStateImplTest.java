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

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.CommandRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DeviceRegistrationLicenseStateImplTest {
    private CommandRunner commandRunner;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        commandRunner = Mockito.mock(CommandRunner.class);
    }

    @Test
    public void testIsRevoked() throws IOException, InterruptedException {
        Mockito.when(commandRunner.runCommandWithOutput(Mockito.anyString(), Mockito.any())).thenReturn("403");
        DeviceRegistrationLicenseState registrationState = new DeviceRegistrationLicenseStateImpl(commandRunner, "curl-fake", null, null, null, null, null);

        assertEquals(RegistrationState.INVALID, registrationState.checkCertificate());
    }

    @Test
    public void testIsNotRevoked() throws IOException, InterruptedException {
        Mockito.when(commandRunner.runCommandWithOutput(Mockito.anyString(), Mockito.any())).thenReturn("200");
        DeviceRegistrationLicenseState registrationState = new DeviceRegistrationLicenseStateImpl(commandRunner, "curl-fake", null, null, null, null, null);

        assertEquals(RegistrationState.OK, registrationState.checkCertificate());
    }

    @Test
    public void testUnknownResponse() throws IOException, InterruptedException {
        Mockito.when(commandRunner.runCommandWithOutput(Mockito.anyString(), Mockito.any())).thenReturn("asd sad -asdasdas-");
        DeviceRegistrationLicenseState registrationState = new DeviceRegistrationLicenseStateImpl(commandRunner, "curl-fake", null, null, null, null, null);

        thrown.expect(EblockerException.class);
        thrown.expectMessage("Unknown response. Revokation state check failed.");
        registrationState.checkCertificate();
    }
}

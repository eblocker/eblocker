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
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.ActionArgument;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.Datatype;
import org.fourthline.cling.support.model.PortMapping.Protocol;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class UpnpPortForwardingAddTest {
    private UpnpPortForwardingAdd add;

    private Service service;
    private UpnpPortForwarding forwarding;
    private UpnpManagementService callingService;

    @Before
    public void setup() {
        Datatype dataType = Mockito.mock(Datatype.class);
        Mockito.when(dataType.isValid(Mockito.any())).thenReturn(true);
        Mockito.when(dataType.getString(Mockito.any())).thenReturn("");

        ActionArgument actionArgument = Mockito.mock(ActionArgument.class);
        Mockito.when(actionArgument.getDatatype()).thenReturn(dataType);

        Action action = Mockito.mock(Action.class);
        Mockito.when(action.getInputArgument(Mockito.anyString())).thenReturn(actionArgument);

        service = Mockito.mock(Service.class);
        Mockito.when(service.getAction("AddPortMapping")).thenReturn(action);

        ControlPoint controlpoint = Mockito.mock(ControlPoint.class);

        callingService = Mockito.mock(UpnpManagementService.class);

        forwarding = new UpnpPortForwarding(1, 2, "3.3.3.3", 4, "5", Protocol.TCP, true);
        add = new UpnpPortForwardingAdd(service, controlpoint, forwarding, callingService);
    }

    @After
    public void teardown() {

    }

    @Test
    public void testSuccess() {
        ActionInvocation invocation = Mockito.mock(ActionInvocation.class);

        add.success(invocation);

        Assert.assertTrue(add.getResult().isSuccess());
    }

    @Test
    public void testFailure() {
        ActionInvocation invocation = Mockito.mock(ActionInvocation.class);
        UpnpResponse operation = null;// parameter not used in function
        String defaultMsg = "defaultMsg";

        add.failure(invocation, operation, defaultMsg);

        Assert.assertFalse(add.getResult().isSuccess());
        Assert.assertEquals(defaultMsg, add.getResult().getErrorMsg());
    }
}

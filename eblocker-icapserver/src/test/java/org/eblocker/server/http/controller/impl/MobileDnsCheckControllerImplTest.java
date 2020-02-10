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

import org.eblocker.server.common.openvpn.connection.MobileDnsCheckService;
import org.eblocker.server.http.controller.MobileDnsCheckController;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

public class MobileDnsCheckControllerImplTest {

    @Test
    public void testCheck() {
        MobileDnsCheckService mobileDnsCheckService = Mockito.mock(MobileDnsCheckService.class);
        Request request = Mockito.mock(Request.class);
        Response response = Mockito.mock(Response.class);
        MobileDnsCheckController controller = new MobileDnsCheckControllerImpl(mobileDnsCheckService);

        Assert.assertFalse(controller.check(request, response));

        Mockito.when(mobileDnsCheckService.check()).thenReturn(true);
        Assert.assertTrue(controller.check(request, response));
    }

}

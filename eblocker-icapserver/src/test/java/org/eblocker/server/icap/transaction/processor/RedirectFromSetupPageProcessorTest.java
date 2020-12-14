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
package org.eblocker.server.icap.transaction.processor;

import ch.mimo.netty.handler.codec.icap.DefaultIcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapMethod;
import ch.mimo.netty.handler.codec.icap.IcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapVersion;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.http.service.DnsService;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RedirectFromSetupPageProcessorTest {
    private RedirectFromSetupPageProcessor processor;
    private Transaction transaction;
    private Session session = Mockito.mock(Session.class);
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private DnsService dnsService;

    @Before
    public void setUp() {
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        dnsService = Mockito.mock(DnsService.class);
        NetworkServices networkServices = Mockito.mock(NetworkServices.class);
        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);
        Mockito.when(dnsService.isEnabled()).thenReturn(true);
        NetworkConfiguration networkConfiguration = Mockito.mock(NetworkConfiguration.class);
        Mockito.when(networkConfiguration.getIpAddress()).thenReturn("192.168.2.1");
        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfiguration);

        processor = new RedirectFromSetupPageProcessor(
            "http://setup.eblocker.com",
            "/de/ /en/ /de/index.html /en/index.html",
            "eblocker.box",
            "/_check_/routing",
            deviceRegistrationProperties,
            dnsService,
            networkServices);
    }

    @Test
    public void testNoRedirect() {
        transaction = makeTransaction("http://www.eblocker.com/en/");
        assertTrue(processor.process(transaction));
        assertNull(transaction.getResponse().headers().get("Location"));
    }

    @Test
    public void testProcessEn() {
        transaction = makeTransaction("http://setup.eblocker.com/en/");
        assertFalse(processor.process(transaction));
        assertEquals("http://192.168.2.1/setup/#!/en", transaction.getResponse().headers().get("Location"));
    }

    @Test
    public void testProcessDe() {
        transaction = makeTransaction("http://setup.eblocker.com/de/");
        assertFalse(processor.process(transaction));
        assertEquals("http://192.168.2.1/setup/#!/de", transaction.getResponse().headers().get("Location"));
    }

    @Test
    public void testRedirectToDashboard() {
        transaction = makeTransaction("http://setup.eblocker.com/de/");
        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK);
        assertFalse(processor.process(transaction));
        assertEquals("http://eblocker.box", transaction.getResponse().headers().get("Location"));
    }

    @Test
    public void testRedirectDnsDisabled() {
        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK);
        Mockito.when(dnsService.isEnabled()).thenReturn(false);
        transaction = makeTransaction("http://setup.eblocker.com/de/");
        assertFalse(processor.process(transaction));
        assertEquals("http://192.168.2.1/dashboard", transaction.getResponse().headers().get("Location"));
    }

    private Transaction makeTransaction(String uri) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, uri, "someHost");

        transaction = new IcapTransaction(request);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        transaction.setRequest(httpRequest);
        transaction.setResponse(httpResponse);

        transaction.setSession(session);

        return transaction;
    }
}

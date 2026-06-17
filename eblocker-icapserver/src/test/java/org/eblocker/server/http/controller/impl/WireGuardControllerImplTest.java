/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.vpn.VpnConfigurationViewModel;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.wireguard.WireGuardService;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardPeer;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class WireGuardControllerImplTest {
    private AnonymousService anonymousService;
    private WireGuardService wireGuardService;
    private SessionStore sessionStore;
    private DeviceService deviceService;
    private WireGuardControllerImpl controller;

    @Before
    public void setUp() {
        anonymousService = Mockito.mock(AnonymousService.class);
        wireGuardService = Mockito.mock(WireGuardService.class);
        sessionStore = Mockito.mock(SessionStore.class);
        deviceService = Mockito.mock(DeviceService.class);
        controller = new WireGuardControllerImpl(anonymousService, wireGuardService, sessionStore, deviceService);
    }

    @Test
    public void createProfilePersistsWireGuardProfile() throws Exception {
        Request request = Mockito.mock(Request.class);
        Response response = new Response();
        WireGuardProfile submitted = new WireGuardProfile(null, "provider");
        WireGuardProfile saved = new WireGuardProfile(23, "provider");

        Mockito.when(request.getBodyAs(WireGuardProfile.class)).thenReturn(submitted);
        Mockito.when(wireGuardService.saveProfile(submitted)).thenReturn(saved);

        Assert.assertSame(saved, controller.createProfile(request, response));
        Assert.assertEquals(HttpResponseStatus.CREATED.code(), response.getResponseStatus().code());
        Assert.assertEquals("/anonymous/vpn/profile/23", response.getHeader("Location"));
    }

    @Test
    public void uploadProfileConfigMapsWireGuardConfigurationToCompatibilityViewModel() throws Exception {
        Request request = Mockito.mock(Request.class);
        Response response = new Response();
        String config = "[Interface]\nPrivateKey = private\nAddress = 10.8.0.2/32\n";
        WireGuardConfiguration configuration = new WireGuardConfiguration(
                "private",
                Arrays.asList("10.8.0.2/32", "fd00::2/128"),
                Arrays.asList("1.1.1.1"),
                1420,
                Arrays.asList(new WireGuardPeer("public", "psk", "vpn.example.net:51820", Arrays.asList("0.0.0.0/0", "::/0"), 25)));

        Mockito.when(request.getHeader("id")).thenReturn("7");
        Mockito.when(request.getBodyAsStream()).thenReturn(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
        Mockito.when(wireGuardService.setProfileClientConfig(7, config)).thenReturn(configuration);

        VpnConfigurationViewModel model = controller.uploadProfileConfig(request, response);

        Assert.assertFalse(model.isCredentialsRequired());
        Assert.assertTrue(model.getRequiredFiles().isEmpty());
        Assert.assertTrue(model.getValidationErrors().isEmpty());
        Collection<String> lines = model.getActiveOptions().stream().map(line -> line.line).collect(Collectors.toList());
        Assert.assertTrue(lines.contains("PrivateKey = private"));
        Assert.assertTrue(lines.contains("Address = 10.8.0.2/32, fd00::2/128"));
        Assert.assertTrue(lines.contains("DNS = 1.1.1.1"));
        Assert.assertTrue(lines.contains("MTU = 1420"));
        Assert.assertTrue(lines.contains("PublicKey = public"));
        Assert.assertTrue(lines.contains("PresharedKey = psk"));
        Assert.assertTrue(lines.contains("Endpoint = vpn.example.net:51820"));
        Assert.assertTrue(lines.contains("AllowedIPs = 0.0.0.0/0, ::/0"));
        Assert.assertTrue(lines.contains("PersistentKeepalive = 25"));
    }

    @Test(expected = BadRequestException.class)
    public void uploadProfileConfigOptionRejectsExternalOptionFiles() {
        controller.uploadProfileConfigOption(Mockito.mock(Request.class), new Response());
    }
}

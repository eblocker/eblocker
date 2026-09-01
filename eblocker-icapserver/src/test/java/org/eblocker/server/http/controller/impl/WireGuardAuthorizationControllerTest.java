package org.eblocker.server.http.controller.impl;

import org.eblocker.server.http.model.WireGuardAuthorizationOverviewView;
import org.eblocker.server.http.model.WireGuardAuthorizationView;
import org.eblocker.server.http.service.WireGuardAuthorizationManagementService;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardAuthorizationControllerTest {

    private WireGuardAuthorizationManagementService managementService;
    private WireGuardServerControllerImpl controller;
    private Request request;
    private Response response;

    @BeforeEach
    public void setUp() {
        managementService =
                Mockito.mock(
                        WireGuardAuthorizationManagementService.class
                );

        controller = new WireGuardServerControllerImpl(
                Mockito.mock(WireGuardServerService.class),
                Mockito.mock(WireGuardServerControlService.class),
                Mockito.mock(WireGuardPeerService.class),
                Mockito.mock(WireGuardClientConfigurationService.class),
                managementService
        );

        request = Mockito.mock(Request.class);
        response = new Response();
    }

    @Test
    public void aggregateDeviceAuthorizationsCanBeLoaded() {
        WireGuardAuthorizationOverviewView overview =
                new WireGuardAuthorizationOverviewView(
                        true,
                        Collections.emptyList()
                );

        Mockito.when(
                managementService.getDeviceAuthorizations()
        ).thenReturn(overview);

        assertSame(
                overview,
                controller.getDeviceAuthorizations(
                        request,
                        response
                )
        );
    }

    @Test
    public void deviceAuthorizationCanBeUpdated() {
        WireGuardAuthorizationView view =
                new WireGuardAuthorizationView(
                        "device:001122334455",
                        true,
                        true,
                        7,
                        false,
                        null,
                        true,
                        "ALLOWED_NO_ASSIGNED_USER"
                );

        Mockito.when(
                request.getHeader("deviceId")
        ).thenReturn("device:001122334455");

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(true);

        Mockito.when(
                managementService.setDeviceAuthorization(
                        "device:001122334455",
                        true
                )
        ).thenReturn(true);

        Mockito.when(
                managementService.getDeviceAuthorization(
                        "device:001122334455"
                )
        ).thenReturn(view);

        assertSame(
                view,
                controller.setDeviceAuthorization(
                        request,
                        response
                )
        );
    }

    @Test
    public void missingDeviceIsReported() {
        Mockito.when(
                request.getHeader("deviceId")
        ).thenReturn("device:missing");

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> controller.setDeviceAuthorization(
                        request,
                        response
                )
        );
    }

    @Test
    public void userAuthorizationCanBeUpdated() {
        Mockito.when(
                request.getHeader("userId")
        ).thenReturn("42");

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(true);

        Mockito.when(
                managementService.setUserAuthorization(42, true)
        ).thenReturn(true);

        assertTrue(
                controller.setUserAuthorization(
                        request,
                        response
                )
        );
    }
}

/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.session;

import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    private SessionStore sessionStore;
    private DeviceService deviceService;
    private UserAgentService userAgentService;
    private TransactionIdentifier transactionIdentifier;
    private TestDeviceFactory tdf;
    private TestClock clock;
    private static final IpAddress clientIp = IpAddress.parse("192.168.23.42");
    private static final String clientUserAgent = "My Browser 0.1";

    @BeforeEach
    void setUp() {
        transactionIdentifier = Mockito.mock(TransactionIdentifier.class);

        deviceService = Mockito.mock(DeviceService.class);
        userAgentService = Mockito.mock(UserAgentService.class);
        clock = new TestClock(LocalDateTime.now());
        sessionStore = new SessionStore(deviceService, userAgentService, clock);

        tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("123456abcdef", clientIp.toString(), true);
        Mockito.when(transactionIdentifier.getOriginalClientIP()).thenReturn(clientIp);
        Mockito.when(transactionIdentifier.getUserAgent()).thenReturn(clientUserAgent);
    }

    @Test
    void noClientIP() {
        Mockito.when(transactionIdentifier.getOriginalClientIP()).thenReturn(null);
        assertThrows(EblockerException.class, () -> {
            sessionStore.getSession(transactionIdentifier);
        });
    }

    @Test
    void noDeviceFound() {
        Mockito.when(transactionIdentifier.getOriginalClientIP()).thenReturn(IpAddress.parse("9.9.9.9"));
        assertThrows(EblockerException.class, () -> {
            sessionStore.getSession(transactionIdentifier);
        });
    }

    @Test
    void sessionCreated() {
        Session session = sessionStore.getSession(transactionIdentifier);
        assertNotNull(sessionStore.findSession(session.getSessionId()));
        assertNull(sessionStore.findSession("other session ID"));

        // get the identical object back:
        Session retrieved = sessionStore.getSession(transactionIdentifier);
        assertTrue(session == retrieved);
    }

    @Test
    void purgeSessions() {
        Session session = sessionStore.getSession(transactionIdentifier);
        sessionStore.purgeSessions();
        assertNotNull(sessionStore.findSession(session.getSessionId()));

        // after 25 hours the session is purged:
        clock.setLocalDateTime(LocalDateTime.now().plusHours(25));
        sessionStore.purgeSessions();
        assertNull(sessionStore.findSession(session.getSessionId()));
    }
}

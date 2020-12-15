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
package org.eblocker.server.http.security;

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.EventType;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.common.update.SystemUpdater.State;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.EmbeddedRedisServiceTestBase;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.UserService;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.restexpress.exception.UnauthorizedException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

public class SecurityServiceTest extends EmbeddedRedisServiceTestBase {

    private static final IpAddress IP_ADDRESS = IpAddress.parse("1.2.3.4");

    @Mock
    private BaseURLs baseURLs;
    private final long passwordResetValiditySeconds = 3600;
    private final long passwordResetGracePeriodSeconds = 3600;
    @Mock
    private SystemUpdater systemUpdater;
    @Mock
    private AutomaticUpdater automaticUpdater;
    @Mock
    private ScriptRunner scriptRunner;
    @Mock
    private EventLogger eventLogger;
    @Mock
    private DeviceService deviceService;
    @Mock
    UserService userService;
    @Mock
    ProductInfoService productInfoService;

    Clock clock = Mockito.mock(Clock.class);

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Override
    protected void doSetup() {
        super.doSetup();
        when(baseURLs.getHttpURL()).thenReturn("http://test.eblocker.com");
        when(baseURLs.getHttpsURL()).thenReturn("https://test.eblocker.com");
    }

    @Test
    public void test_generateAndVerifyJWT_ok() {
        generateAndVerifyJWT_ok(s -> s.generateToken(new Credentials(null, null), IP_ADDRESS, AppContext.defaultValue()));
    }

    private void generateAndVerifyJWT_ok(Function<SecurityService, JsonWebToken> generator) {
        SecurityService securityService = createSecurityService();

        JsonWebToken token = generator.apply(securityService);

        assertNotNull(token);

        String encodedToken = token.getToken();

        TokenInfo verified = securityService.verifyToken(encodedToken);

        assertNotNull(verified);
        assertEquals(token.getAppContext(), verified.getAppContext());
        assertEquals(token.getExpiresOn(), verified.getExpiresOn());
    }

    @Test
    public void test_generateAndVerifyJWT_expired() {
        SecurityService securityService = createSecurityService(-3600);

        JsonWebToken token = securityService.generateToken(new Credentials(null, null), IP_ADDRESS, AppContext.defaultValue());

        assertNotNull(token);

        String encodedToken = token.getToken();

        try {
            securityService.verifyToken(encodedToken);
            fail("Expected UnauthorizedException");

        } catch (UnauthorizedException e) {
            assertEquals("error.token.invalid", e.getMessage());
        }
    }

    @Test
    public void test_generateAndVerifyJWT_corrupt() {
        SecurityService securityService = createSecurityService();
        SecurityService otherSecurityService = createSecurityService(3600);

        // use other security service (with different secret) to generate token
        JsonWebToken token = otherSecurityService.generateToken(new Credentials(null, null), IP_ADDRESS, AppContext.defaultValue());

        assertNotNull(token);

        String encodedToken = token.getToken();

        try {
            securityService.verifyToken(encodedToken);
            fail("Expected UnauthorizedException");

        } catch (UnauthorizedException e) {
            assertEquals("error.token.invalid", e.getMessage());
        }
    }

    @Test
    public void test_generateAndVerifyJWT_randomSecret_ok() {
        SecurityService securityService = createSecurityService(3600);

        JsonWebToken token = securityService.generateToken(new Credentials(null, null), IP_ADDRESS, AppContext.defaultValue());

        assertNotNull(token);

        String encodedToken = token.getToken();

        TokenInfo verified = securityService.verifyToken(encodedToken);

        assertNotNull(verified);
        assertEquals(token.getAppContext(), verified.getAppContext());
        assertEquals(token.getExpiresOn(), verified.getExpiresOn());
    }

    @Test
    public void test_renewJWT_ok() throws InterruptedException {
        SecurityService securityService = createSecurityService(3600); // within grace period

        JsonWebToken token = securityService.generateToken(new Credentials(null, null), IP_ADDRESS, AppContext.defaultValue());

        Thread.sleep(1000L); // to get a measurable time difference to renewed token

        JsonWebToken renewed = securityService.renewToken(new TokenInfo(token.getAppContext(), token.getExpiresOn(), 3600, true));

        assertNotNull(renewed);

        // renewed is valid at least one second longer than original token
        assertTrue(1 <= renewed.getExpiresOn() - token.getExpiresOn());
    }

    @Test
    public void test_setVerifyRemovePassword_ok() {
        SecurityService securityService = createSecurityService();
        Instant first = Instant.ofEpochSecond(1000L);// Trying correct
        Instant second = first.plusSeconds(1000L);// Testing wrong password, 1
        Instant third = second.plusSeconds(1000L);// Testing wrong password, 1
        Instant fourth = third.plusSeconds(1000L);// Changing password
        Instant fifth = fourth.plusSeconds(1000L);// Trying correct
        Instant sixth = fifth.plusSeconds(1000L); // Testing wrong password, 1
        Instant seventh = sixth.plusSeconds(1000L);// Testing wrong password, 1
        Instant eighth = seventh.plusSeconds(1000L);// Failing to remove
        Instant ninth = eighth.plusSeconds(1000L);// Failing to remove
        Instant tenth = ninth.plusSeconds(1000L);// Successfully removed
        when(clock.instant()).thenReturn(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth, tenth);

        IpAddress[] ipAddresses = { IpAddress.parse("1.2.3.4"), IpAddress.parse("2.3.4.5"), IpAddress.parse("3.4.5.6"), IpAddress.parse("4.5.6.7") };

        // No password set, all credentials are valid. Even <null>.
        securityService.verifyPassword(new Credentials(null, null), ipAddresses[0]);
        securityService.verifyPassword(new Credentials("", null), ipAddresses[0]);
        securityService.verifyPassword(new Credentials("mY-s3creT-p@ssw0rD", null), ipAddresses[0]);

        // Set new password
        securityService.setPassword(new Credentials(null, "mY-s3creT-p@ssw0rD"), ipAddresses[0]);
        // verify change is logged!
        Mockito.verify(eventLogger).log(Mockito.any());
        Mockito.reset(eventLogger);

        // Now, only the correct password is valid
        securityService.verifyPassword(new Credentials("mY-s3creT-p@ssw0rD", null), ipAddresses[0]);

        // All other passwords are not accepted
        assertInvalidCredentials(securityService, "wrong-password", ipAddresses[0]);
        assertInvalidCredentials(securityService, "", ipAddresses[0]);
        assertInvalidCredentials(securityService, null, ipAddresses[0]);

        // Cannot set new password, w/o providing the old one
        try {
            securityService.setPassword(new Credentials(null, "new-password"), ipAddresses[0]);
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException e) {
            assertEquals("error.credentials.invalid", e.getMessage());
        }

        // But can set new password, if old one is correct
        securityService.setPassword(new Credentials("mY-s3creT-p@ssw0rD", "new-password"), ipAddresses[1]);
        // verify change is logged!
        Mockito.verify(eventLogger).log(Mockito.any());
        Mockito.reset(eventLogger);

        // Now, only the new password is valid
        securityService.verifyPassword(new Credentials("new-password", null), ipAddresses[1]);

        // All other passwords are not accepted
        assertInvalidCredentials(securityService, "wrong-password", ipAddresses[1]);
        assertInvalidCredentials(securityService, "", ipAddresses[1]);
        assertInvalidCredentials(securityService, null, ipAddresses[1]);

        // Cannot remove password, w/o providing the current one
        try {
            securityService.removePassword(new Credentials("xyz-abc", null), ipAddresses[2]);
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException e) {
            assertEquals("error.credentials.invalid", e.getMessage());
        }

        // But can remove password, if old one is correct
        securityService.removePassword(new Credentials("new-password", null), ipAddresses[3]);
        // verify change is logged!
        Mockito.verify(eventLogger).log(Mockito.any());

        // Now, all passwords are accepted again
        securityService.verifyPassword(new Credentials(null, null), ipAddresses[3]);
        securityService.verifyPassword(new Credentials("", null), ipAddresses[3]);
        securityService.verifyPassword(new Credentials("mY-s3creT-p@ssw0rD", null), ipAddresses[3]);
        securityService.verifyPassword(new Credentials("wrong-password", null), ipAddresses[3]);

    }

    @Test(expected = UnauthorizedException.class)
    public void testAttemptTooSoonRejected() {
        SecurityService securityService = createSecurityService();
        Instant first = Instant.ofEpochSecond(1000L);// Trying wrong password
        Instant second = first.plusSeconds(0L);// Setting wait period for wrong password
        Instant third = second.plusSeconds(1L);// Testing how long to wait
        Instant fourth = third.plusSeconds(1L);// Correct password rejected, entered too soon
        Instant fifth = fourth.plusSeconds(0L);// Correct password rejected, entered too soon
        when(clock.instant()).thenReturn(first, second, third, fourth, fifth);

        // Set new password
        securityService.setPassword(new Credentials(null, "mY-s3creT-p@ssw0rD"), IP_ADDRESS);
        // verify change is logged!
        Mockito.verify(eventLogger).log(Mockito.any());
        Mockito.reset(eventLogger);

        // First attempt at verification, everything allright (i.e. rejected due to wrong password)
        try {
            securityService.verifyPassword(new Credentials("n0t-mY-s3creT-p@ssw0rD", null), IP_ADDRESS);
            fail("Expected UnauthorizedException");
        } catch (Exception e) {
            // Expected
        }

        // We need to wait
        assertTrue(securityService.passwordEntryInSeconds(IP_ADDRESS) >= 1);

        // Second attempt at verification without waiting - even correct password is rejected
        securityService.verifyPassword(new Credentials("mY-s3creT-p@ssw0rD", null), IP_ADDRESS);
    }

    @Test
    public void testAttemptFromDifferentIpAccepted() {
        SecurityService securityService = createSecurityService();
        Instant first = Instant.ofEpochSecond(1000L);// Trying wrong password
        Instant second = first.plusSeconds(0L);// Setting wait period for wrong password
        Instant third = second.plusSeconds(1L);// Testing how long to wait
        Instant fourth = third.plusSeconds(1000L);// Successful login
        when(clock.instant()).thenReturn(first, second, third, fourth);

        // Set new password
        securityService.setPassword(new Credentials(null, "mY-s3creT-p@ssw0rD"), IP_ADDRESS);
        // verify change is logged!
        Mockito.verify(eventLogger).log(Mockito.any());
        Mockito.reset(eventLogger);

        // First attempt at verification, everything allright (i.e. rejected due to wrong password)
        try {
            securityService.verifyPassword(new Credentials("n0t-mY-s3creT-p@ssw0rD", null), IpAddress.parse("123.123.123.123"));
            fail("Expected UnauthorizedException");
        } catch (Exception e) {
            // Expected
        }

        // We do not need to wait
        assertEquals(0, securityService.passwordEntryInSeconds(IP_ADDRESS));

        // Second attempt at verification without waiting - but from different IP and therefore accepted
        try {
            securityService.verifyPassword(new Credentials("mY-s3creT-p@ssw0rD", null), IP_ADDRESS);
        } catch (Exception e) {
            // Any exception is not accepted
            fail("Expected no UnauthorizedException");
        }
    }

    @Test
    public void testPasswordResetEventLogged() throws IOException, InterruptedException {
        when(automaticUpdater.isActivated()).thenReturn(false);
        when(systemUpdater.getUpdateStatus()).thenReturn(State.IDLING);

        SecurityService securityService = createSecurityService();

        PasswordResetToken passwordResetTokenReceived = securityService.initiateReset();
        Mockito.verify(automaticUpdater).setActivated(false);
        Mockito.verify(systemUpdater).getUpdateStatus();
        Mockito.verify(scriptRunner).runScript("shutdownScript");

        securityService.executeReset(passwordResetTokenReceived, IP_ADDRESS);

        Map<String, String> expectedResetDetails = new HashMap<>();
        expectedResetDetails.put("ipAddress", IP_ADDRESS.toString());

        Mockito.verify(eventLogger).log(Mockito.argThat((event ->
                event.getType() == EventType.ADMIN_PASSWORD_RESET &&
                        event.getEventDetails().equals(expectedResetDetails)
        )));
    }

    private void assertInvalidCredentials(SecurityService securityService, String password, IpAddress ip) {
        try {
            securityService.verifyPassword(new Credentials(password, null), ip);
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException e) {
            assertEquals("error.credentials.invalid", e.getMessage());
        }
    }

    private SecurityService createSecurityService() {
        return createSecurityService(3600);
    }

    private SecurityService createSecurityService(long validity) {
        return new SecurityService(
                new JsonWebTokenHandler(
                        validity,
                        validity,
                        baseURLs
                ),
                validity,
                validity,
                passwordResetValiditySeconds,
                passwordResetGracePeriodSeconds,
                dataSource,
                systemUpdater,
                automaticUpdater,
                scriptRunner,
                "shutdownScript",
                eventLogger, // Eventlogger
                deviceService, // Device Service
                userService, // User Service
                productInfoService, // ProductInfoService
                100, // Max time to wait after entering wrong password
                10, // Increment time to wait after entering wrong password by this number of seconds
                clock
        );
    }

}

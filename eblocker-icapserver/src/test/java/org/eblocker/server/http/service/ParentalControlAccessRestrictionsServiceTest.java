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
package org.eblocker.server.http.service;

import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.AccessRestriction;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UsageAccount;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParentalControlAccessRestrictionsServiceTest {

    private static Set<InternetAccessContingent> ALICE_CONTINGENTS;
    private static Set<InternetAccessContingent> BOB_CONTINGENTS;
    private static Set<InternetAccessContingent> CHARLIE_CONTINGENTS;

    private static Device ALICE_DEVICE;
    private static Device BOB_DEVICE;
    private static Device CHARLIE_DEVICE;

    private static UserProfileModule ALICE_PROFILE;
    private static UserProfileModule BOB_PROFILE;
    private static UserProfileModule CHARLIE_PROFILE;

    private UserModule aliceUser;
    private UserModule bobUser;
    private UserModule charlieUser;

    private SettingsService settingsService = Mockito.mock(SettingsService.class);
    private ParentalControlService parentalControlService = Mockito.mock(ParentalControlService.class);
    private DeviceService deviceService = Mockito.mock(DeviceService.class);
    private ProductInfoService productInfoService = Mockito.mock(ProductInfoService.class);
    private ParentalControlUsageService parentalControlUsageService = Mockito.mock(ParentalControlUsageService.class);
    private UserService userService = Mockito.mock(UserService.class);
    private ParentalControlAccessRestrictionsService.AccessRestrictionsChangeListener changeListener = new Mockito().mock(ParentalControlAccessRestrictionsService.AccessRestrictionsChangeListener.class);

    private ParentalControlAccessRestrictionsService restrictionsService = new ParentalControlAccessRestrictionsService(
            deviceService,
            settingsService,
            parentalControlService,
            productInfoService,
            parentalControlUsageService,
            userService
    );

    @Before
    public void setupListener() {
        restrictionsService.addListener(changeListener);
    }

    @Before
    public void initFixtures() {
        // Contingents for Alice
        ALICE_CONTINGENTS = new HashSet<>();
        // Weekday (Tuesday)
        ALICE_CONTINGENTS.add(new InternetAccessContingent(2, 14 * 60, 18 * 60, 4 * 60));
        // Weekend (Saturday)
        ALICE_CONTINGENTS.add(new InternetAccessContingent(6, 10 * 60, 22 * 60, 12 * 60));
        // Any weekday
        ALICE_CONTINGENTS.add(new InternetAccessContingent(8, 2 * 60, 3 * 60, 60));
        // Any day on weekend
        ALICE_CONTINGENTS.add(new InternetAccessContingent(9, 5 * 60, 6 * 60, 60));
        // From one day till the next
        ALICE_CONTINGENTS.add(new InternetAccessContingent(5, 23 * 60, 24 * 60, 60));
        ALICE_CONTINGENTS.add(new InternetAccessContingent(6, 0, 60, 60));

        // Contingents for Bob
        BOB_CONTINGENTS = new HashSet<>();

        // Contingents for Charlie
        CHARLIE_CONTINGENTS = new HashSet<>();
        CHARLIE_CONTINGENTS.add(new InternetAccessContingent(2, 0, 18 * 60 + 5, 60));

        ALICE_PROFILE = new UserProfileModule(1, null, null, null, null, null, false, null, null, UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, ALICE_CONTINGENTS, Collections.emptyMap(), null, false, null);
        ALICE_PROFILE.setControlmodeTime(true);

        BOB_PROFILE = new UserProfileModule(2, null, null, null, null, null, false, null, null, UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, BOB_CONTINGENTS, Collections.emptyMap(), null, false, null);
        BOB_PROFILE.setControlmodeTime(false);

        CHARLIE_PROFILE = new UserProfileModule(3, null, null, null, null, null, false, null, null, UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, CHARLIE_CONTINGENTS, Collections.emptyMap(), null, false, null);
        CHARLIE_PROFILE.setControlmodeTime(true);

        aliceUser = new UserModule(ALICE_PROFILE.getId(), ALICE_PROFILE.getId(), "alice", "alice-key", null, null, false, null, Collections.emptyMap(), null, null, null);
        bobUser = new UserModule(BOB_PROFILE.getId(), BOB_PROFILE.getId(), "bob", "bob-key", null, null, false, null, Collections.emptyMap(), null, null, null);
        charlieUser = new UserModule(CHARLIE_PROFILE.getId(), CHARLIE_PROFILE.getId(), "charlie", "charlie-key", null, null, false, null, Collections.emptyMap(), null, null, null);

        ALICE_DEVICE = new Device();
        ALICE_DEVICE.setId("aaaaaaaaaaaa");
        ALICE_DEVICE.setIpAddresses(Collections.singletonList(IpAddress.parse("111.111.111.111")));
        ALICE_DEVICE.setAssignedUser(aliceUser.getId());
        ALICE_DEVICE.setOperatingUser(aliceUser.getId());
        ALICE_DEVICE.setId("alice");

        BOB_DEVICE = new Device();
        BOB_DEVICE.setId("bbbbbbbbbbbb");
        BOB_DEVICE.setIpAddresses(Collections.singletonList(IpAddress.parse("111.111.111.112")));
        BOB_DEVICE.setAssignedUser(bobUser.getId());
        BOB_DEVICE.setOperatingUser(bobUser.getId());
        BOB_DEVICE.setId("bob");

        CHARLIE_DEVICE = new Device();
        CHARLIE_DEVICE.setId("cccccccccccc");
        CHARLIE_DEVICE.setIpAddresses(Collections.singletonList(IpAddress.parse("111.111.111.113")));
        CHARLIE_DEVICE.setAssignedUser(charlieUser.getId());
        CHARLIE_DEVICE.setOperatingUser(charlieUser.getId());
        CHARLIE_DEVICE.setId("charlie");
    }

    private void initMocks() {
        initMocks(true, ALICE_CONTINGENTS, ALICE_PROFILE.getId());
    }

    private void initMocks(boolean hasFamilyFeature, Set<InternetAccessContingent> aliceContingents, int aliceUserId) {
        ALICE_PROFILE.setInternetAccessContingents(aliceContingents);
        ALICE_DEVICE.setAssignedUser(aliceUserId);
        ALICE_DEVICE.setOperatingUser(aliceUserId);

        when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(hasFamilyFeature);
        when(settingsService.getTimeZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        when(deviceService.getDevices(anyBoolean())).thenReturn(Arrays.asList(ALICE_DEVICE, BOB_DEVICE, CHARLIE_DEVICE));
        when(parentalControlService.getProfiles()).thenReturn(Arrays.asList(ALICE_PROFILE, BOB_PROFILE, CHARLIE_PROFILE));
        when(userService.getUserById(aliceUser.getId())).thenReturn(aliceUser);
        when(userService.getUserById(bobUser.getId())).thenReturn(bobUser);
        when(userService.getUserById(charlieUser.getId())).thenReturn(charlieUser);
    }

    @Test
    public void testSpecificWeekday() {
        initMocks();

        // Tuesday, Alice can go online between 14 and 18
        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(13, 59));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(15, 15));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
    }

    @Test
    public void testSpecificWeekendDay() {
        initMocks();
        // Saturday, Alice can go online between 10 and 22

        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 18), LocalTime.of(9, 59));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 18), LocalTime.of(16, 15));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 18), LocalTime.of(22, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
    }

    @Test
    public void testAnyWeekday() {
        initMocks();
        // Any Weekday (This is a thursday), Alice can go online between 2 and 3

        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 16), LocalTime.of(1, 59));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 16), LocalTime.of(2, 15));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 16), LocalTime.of(3, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
    }

    @Test
    public void testAnyWeekendDay() {
        initMocks();
        // Any Weekend Day (This is a Sunday), Alice can go online between 5 and 6

        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 19), LocalTime.of(4, 59));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 19), LocalTime.of(5, 15));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));

        now = LocalDateTime.of(LocalDate.of(1997, 10, 19), LocalTime.of(6, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
    }

    @Test
    public void testDevicesWrittenToAclFile() {
        initMocks();

        // Tuesday, everybody can go online
        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(15, 15));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // SquidConfigController was not called to reload the configuration
        verify(changeListener, never()).onChange(anySet());

        // One device is restricted
        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // SquidConfigController was called to write Alice's device to ACL file
        Set<Device> onlyAliceRestricted = new HashSet<>();
        onlyAliceRestricted.add(ALICE_DEVICE);
        verify(changeListener).onChange(eq(onlyAliceRestricted));

        // One device stays restricted, ACL file not written again (no changes)

        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 2));
        reset(changeListener);

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // SquidConfigController was not called to reload the configuration
        verify(changeListener, never()).onChange(anySet());
        ;

        // Second device runs out of time, also written to ACL file
        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 6));
        reset(changeListener);

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // Assert Charlie get no internet
        assertEquals(false, restrictionsService.isAccessPermitted(CHARLIE_DEVICE));
        // SquidConfigController was called to write Alice's and Charlie's device to ACL file
        Set<Device> aliceAndCharlieRestricted = new HashSet<>();
        aliceAndCharlieRestricted.add(ALICE_DEVICE);
        aliceAndCharlieRestricted.add(CHARLIE_DEVICE);
        verify(changeListener).onChange(aliceAndCharlieRestricted);
    }

    @Test
    public void testEmptyAclAtFamilyFeaturesEnd() {
        initMocks();

        // Tuesday, one device is restricted
        LocalDateTime now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 1));

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets no internet
        assertEquals(false, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // SquidConfigController was called to write Alice's device to ACL file
        Set<Device> onlyAliceRestricted = new HashSet<>();
        onlyAliceRestricted.add(ALICE_DEVICE);
        verify(changeListener).onChange(eq(onlyAliceRestricted));

        // Family feature ends
        when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(false);

        now = LocalDateTime.of(LocalDate.of(1997, 10, 14), LocalTime.of(18, 2));
        reset(changeListener);

        restrictionsService.updateAccessContingents(now);
        // Assert Alice gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
        // Assert Bob gets internet
        assertEquals(true, restrictionsService.isAccessPermitted(BOB_DEVICE));
        // SquidConfigController called to write no device to ACL file
        verify(changeListener).onChange(eq(Collections.emptySet()));
    }

    @Test(expected = EblockerException.class)
    public void test_runWithException() {
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        restrictionsService = new ParentalControlAccessRestrictionsService(
                deviceService,
                settingsService,
                parentalControlService,
                null,
                parentalControlUsageService,
                userService
        );

        when(settingsService.getTimeZone()).thenThrow(new java.time.zone.ZoneRulesException("Invalid time zone"));

        restrictionsService.run();
    }

    @Test
    public void test_noFamiliyFeature() {
        // no family feature
        initMocks(false, ALICE_CONTINGENTS, ALICE_PROFILE.getId());

        // access should be allowed at all times - even now
        restrictionsService.run();

        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
    }

    @Test
    public void test_inconsistentProfile() {
        // No contingents, but access control mode is ON
        initMocks(true, BOB_CONTINGENTS, ALICE_PROFILE.getId());

        // access should be allowed at all times - even now
        restrictionsService.run();

        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
    }

    @Test
    public void test_noProfileForDevice() {
        initMocks(true, ALICE_CONTINGENTS, -1);

        // access should be allowed at all times - even now
        restrictionsService.run();

        assertEquals(true, restrictionsService.isAccessPermitted(ALICE_DEVICE));
    }

    @Test
    public void testUsageLimits() {
        // setup mocks
        initMocks();

        // setup mock for UsageAccount
        UsageAccount mockUsageAccountMock = new UsageAccount();
        mockUsageAccountMock.setAllowed(true);
        mockUsageAccountMock.setActive(true);
        Mockito.when(parentalControlUsageService.getUsageAccount(Mockito.any(Device.class))).thenReturn(mockUsageAccountMock);

        // Tuesday, Alice can go online between 14 and 18
        LocalDateTime now = LocalDateTime.of(LocalDate.of(2017, 3, 14), LocalTime.of(14, 00));
        restrictionsService.updateAccessContingents(now);
        assertTrue(restrictionsService.isAccessPermitted(ALICE_DEVICE));

        // turn on max usage mode and check access is still allowed
        ALICE_PROFILE.setControlmodeMaxUsage(true);
        restrictionsService.updateAccessContingents(now);
        assertTrue(restrictionsService.isAccessPermitted(ALICE_DEVICE));

        // change usage mock to report user has disabled access but could re-enable it
        mockUsageAccountMock.setAllowed(true);
        mockUsageAccountMock.setActive(false);
        restrictionsService.updateAccessContingents(now);
        assertFalse(restrictionsService.isAccessPermitted(ALICE_DEVICE));
        assertEquals(restrictionsService.getAccessRestrictions(ALICE_DEVICE).size(), 1);
        Assert.assertTrue(restrictionsService.getAccessRestrictions(ALICE_DEVICE).contains(AccessRestriction.USAGE_TIME_DISABLED));

        // change usage mock to report no usage time left and check acccess is now denied
        mockUsageAccountMock.setAllowed(false);
        restrictionsService.updateAccessContingents(now);
        assertFalse(restrictionsService.isAccessPermitted(ALICE_DEVICE));
        assertEquals(restrictionsService.getAccessRestrictions(ALICE_DEVICE).size(), 1);
        Assert.assertTrue(restrictionsService.getAccessRestrictions(ALICE_DEVICE).contains(AccessRestriction.MAX_USAGE_TIME));
    }
}

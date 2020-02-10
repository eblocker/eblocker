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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.ws.Holder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TrafficAccount;
import org.eblocker.server.common.data.UsageChangeEvent;
import org.eblocker.server.common.data.UsageChangeEvents;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.network.TrafficAccounter;

public class ParentalControlUsageServiceTest {

    private ParentalControlUsageService service;
    private TestClock clock;
    private DataSource dataSource;
    private ParentalControlService parentalControlService;
    private TrafficAccounter trafficAccounter;
    private UserService userService;
    private Device device;
    private UserModule user;

    @Before
    public void setup() {
        // setup device mock
        device = new Device();
        device.setId("device:000000000000");
        device.setAssignedUser(0);
        device.setOperatingUser(0);

        // setup user mock
        user = new UserModule(0, 0, "name", "key", null, null, false, null, Collections.emptyMap(), null, null, null);

        // setup user profile
        Map<DayOfWeek, Integer> maxUsageByDay = new HashMap<>();
        maxUsageByDay.put(DayOfWeek.MONDAY, 120);
        maxUsageByDay.put(DayOfWeek.TUESDAY, 120);
        maxUsageByDay.put(DayOfWeek.SUNDAY, 120);
        UserProfileModule userProfile = new UserProfileModule(1, null, null, null, null, false, false,null, null, null, null, maxUsageByDay, null, false, null);
        userProfile.setControlmodeMaxUsage(true);

        // setup parentalcontrol service mock
        parentalControlService = Mockito.mock(ParentalControlService.class);
        Mockito.when(parentalControlService.getProfile(0)).thenReturn(userProfile);

        // setup clock
        clock = new TestClock(LocalDateTime.now());

        // setup datasource
        dataSource = Mockito.mock(DataSource.class);

        // setup traffic accounter
        trafficAccounter = Mockito.mock(TrafficAccounter.class);

        // setup user service
        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(0)).thenReturn(user);

        // setup parentcontrolservice usage service
        service = new ParentalControlUsageService(5, 5, clock, dataSource, parentalControlService, trafficAccounter, userService);
    }

    @Test
    public void testIntervalsStartOfDay() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 5, 0, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(5, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 10, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 15, 0, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(10, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testIntervals() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 1, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 6, 0, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(5, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 10, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 15, 0, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(10, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testMinimumUsage() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 4, 0, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(5, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testResetAtMidnight() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 23, 45, 0, 0));

        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 15, 0, 0));
        service.stopUsage(device);


        Assert.assertEquals(Duration.of(15, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testMinimumUsageRapidToggle() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 5, 0));
        service.stopUsage(device);

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 10, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 15, 0));
        service.stopUsage(device);

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 20, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 25, 0));
        service.stopUsage(device);

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 30, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 35, 0));
        service.stopUsage(device);

        Assert.assertEquals(Duration.of(5, ChronoUnit.MINUTES), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testInUse() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 0, 0));
        service.startUsage(device);

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 1, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        Assert.assertEquals(Duration.of(1, ChronoUnit.HOURS), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testLimits() {
        service.init();
        // check continous two hour limit on monday
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 0, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isActive());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 1, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 6, 2, 0, 0, 0));
        Assert.assertFalse(service.startUsage(device));
        service.accountUsages(Collections.singleton(device));
        Assert.assertFalse(service.getUsageAccount(device).isAllowed());
        Assert.assertEquals(Duration.ofHours(2), service.getUsageAccount(device).getAccountedTime());

        // check splitted two hour limit on tuesday
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        service.stopUsage(device);

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 3, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertFalse(service.getUsageAccount(device).isAllowed());
        Assert.assertFalse(service.startUsage(device));

        // check no access on wednesday (no limit defined)
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 8, 0, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertFalse(service.getUsageAccount(device).isAllowed());
        Assert.assertFalse(service.startUsage(device));
    }

    @Test
    public void testEndOfDayCutOff() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 23, 56, 0, 0));
        service.startUsage(device);
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        Assert.assertEquals(Duration.ofMinutes(4), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testDaylightSavingTimeStart() {
        service.init();
        clock.setZonedDateTime(ZonedDateTime.of(2017, 3, 26, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        service.startUsage(device);
        clock.setZonedDateTime(ZonedDateTime.of(2017, 3, 26, 6, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        service.stopUsage(device);
        Assert.assertEquals(Duration.ofHours(5), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testDaylightSavingTimeEnd() {
        service.init();
        clock.setZonedDateTime(ZonedDateTime.of(2017, 10, 29, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        service.startUsage(device);
        clock.setZonedDateTime(ZonedDateTime.of(2017, 10, 29, 6, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        service.stopUsage(device);
        Assert.assertEquals(Duration.ofHours(7), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testUsedAccountedTime() {
        service.init();
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 4, 0, 0));
        service.stopUsage(device);
        Assert.assertEquals(Duration.ofMinutes(4), service.getUsageAccount(device).getUsedTime());
        Assert.assertEquals(Duration.ofMinutes(5), service.getUsageAccount(device).getAccountedTime());

        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertEquals(Duration.ofMinutes(5), service.getUsageAccount(device).getUsedTime());
        Assert.assertEquals(Duration.ofMinutes(5), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testListenerCallbacks() {
        service.init();
        // add listener toggling flag to indicate callback has happened
        Holder<Boolean> callbackCalledHolder = new Holder<>(false);
        service.addChangeListener(()-> callbackCalledHolder.value = true);

        // enable usage -> call must be called
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0));
        service.startUsage(device);
        Assert.assertTrue(callbackCalledHolder.value);

        // enable while already enabled -> no callback expected
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 1, 0, 0));
        callbackCalledHolder.value = false;
        service.startUsage(device);
        Assert.assertFalse(callbackCalledHolder.value);

        // disable usage -> callback expected
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 5, 0, 0));
        callbackCalledHolder.value = false;
        service.stopUsage(device);
        Assert.assertTrue(callbackCalledHolder.value);

        // re-enabling to check no callback happens due to accounting
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 10, 0, 0));
        service.startUsage(device);
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 8, 10, 0, 0));
        callbackCalledHolder.value = false;
        service.accountUsages(Collections.singleton(device));
        Assert.assertFalse(callbackCalledHolder.value);
    }

    @Test
    public void testPersistenceInit() {
        // setup mock stored change events
        UsageChangeEvents events = new UsageChangeEvents();
        events.setId(0);
        events.setEvents(new LinkedList<>());
        events.getEvents().add(new UsageChangeEvent(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0), true));
        events.getEvents().add(new UsageChangeEvent(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0), false));
        Mockito.when(dataSource.getAll(UsageChangeEvents.class)).thenReturn(Collections.singletonList(events));

        // init service
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 1, 0, 0));
        service = new ParentalControlUsageService(5, 5, clock, dataSource, parentalControlService, trafficAccounter, userService);
        service.init();

        // check profile has been restored properly
        Assert.assertNotNull(service.getUsageAccount(device));
        Assert.assertEquals(Duration.ofHours(1), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testPersistenceEvents() {
        service.init();
        InOrder inOrder = Mockito.inOrder(dataSource);

        // start first time, must be saved
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        inOrder.verify(dataSource).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));

        // starting already started, must not save any new event
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 5, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        inOrder.verify(dataSource, Mockito.times(0)).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));

        // stopping, must be saved
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 10, 0, 0));
        service.stopUsage(device);
        inOrder.verify(dataSource).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));

        // stopping already stopped, must not save any new event
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 15, 0, 0));
        service.stopUsage(device);
        inOrder.verify(dataSource, Mockito.times(0)).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));

        // accounting usage -> usage is ok, no event saved
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        inOrder.verify(dataSource, Mockito.times(0)).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));

        // starting again and account again after max. usage has been reached -> two new event must be saved (start, stop)
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));
        inOrder.verify(dataSource).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 3, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertFalse(service.getUsageAccount(device).isAllowed());
        inOrder.verify(dataSource).save(Mockito.any(UsageChangeEvents.class), Mockito.eq(0));
    }

    @Test
    public void testAutoOff() {
        service.init();
        // setup mock
        TrafficAccount account = new TrafficAccount();
        Mockito.when(trafficAccounter.getTrafficAccount(Mockito.any(Device.class))).thenReturn(account);

        // start usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));

        // set activity and account usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 0, 8, 0, 0));
        account.setLastActivity(Date.from(clock.instant()));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());

        // advance time further and check accounting disables device at point of last activity
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 3, 0, 0, 0));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        Assert.assertFalse(service.getUsageAccount(device).isActive());
        Assert.assertEquals(Duration.ofMinutes(8), service.getUsageAccount(device).getAccountedTime());
    }

    @Test
    public void testAutoOffLastActivityBeforeStarting() {
        service.init();
        // setup mock
        TrafficAccount account = new TrafficAccount();
        Mockito.when(trafficAccounter.getTrafficAccount(Mockito.any(Device.class))).thenReturn(account);

        // start usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));

        // set activity to a point in the past and account usage which must not disable usage
        account.setLastActivity(Date.from(LocalDateTime.of(2017, 3, 7, 0, 8, 0, 0).atZone(clock.getZone()).toInstant()));
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(device).isAllowed());
        Assert.assertTrue(service.getUsageAccount(device).isActive());
    }

    @Test
    public void testAutoOffUserWithoutAssignedDevice() {
        service.init();
        // setup mock
        TrafficAccount account = new TrafficAccount();
        Mockito.when(trafficAccounter.getTrafficAccount(Mockito.any(Device.class))).thenReturn(account);

        // start usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));

        // disassociate device from user and check it is disabled after accouting
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 0, 0, 0));
        device.setAssignedUser(2);
        device.setOperatingUser(2);
        service.accountUsages(Collections.singleton(device));
        Assert.assertTrue(service.getUsageAccount(user).isAllowed());
        Assert.assertFalse(service.getUsageAccount(user).isActive());
    }

    @Test
    public void testAccountingDeviceChange() {
        service.init();
        // create additional mock device
        Device additionalDevice = new Device();
        additionalDevice.setId("device:0000000001");
        additionalDevice.setAssignedUser(-1);
        additionalDevice.setOperatingUser(-1);

        // start usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));

        // change device
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 0, 0, 0));
        device.setAssignedUser(-1);
        additionalDevice.setAssignedUser(0);
        service.accountUsages(Arrays.asList(device, additionalDevice));

        // further advance time and check accounting is correct
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 30, 0, 0));
        service.accountUsages(Arrays.asList(device, additionalDevice));
        Assert.assertTrue(service.getUsageAccount(user).isAllowed());
        Assert.assertTrue(service.getUsageAccount(user).isActive());
        Assert.assertEquals(Duration.ofMinutes(90), service.getUsageAccount(user).getAccountedTime());
    }

    @Test
    public void testAccountingTwoDevices() {
        service.init();
        // create additional mock device
        Device additionalDevice = new Device();
        additionalDevice.setId("device:0000000001");
        additionalDevice.setAssignedUser(-1);
        additionalDevice.setOperatingUser(-1);

        // start usage
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 1, 0, 0, 0));
        Assert.assertTrue(service.startUsage(device));

        // change device
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 0, 0, 0));
        additionalDevice.setAssignedUser(0);
        service.accountUsages(Arrays.asList(device, additionalDevice));

        // further advance time and check accounting is correct
        clock.setLocalDateTime(LocalDateTime.of(2017, 3, 7, 2, 30, 0, 0));
        service.accountUsages(Arrays.asList(device, additionalDevice));
        Assert.assertTrue(service.getUsageAccount(user).isAllowed());
        Assert.assertTrue(service.getUsageAccount(user).isActive());
        Assert.assertEquals(Duration.ofMinutes(90), service.getUsageAccount(user).getAccountedTime());
    }

    @Test
    public void testAccountingPriorInit() {
        service.accountUsages(Collections.singletonList(device));
        Assert.assertEquals(Duration.ZERO, service.getUsageAccount(device).getAccountedTime());
        Assert.assertEquals(Duration.ZERO, service.getUsageAccount(user).getAccountedTime());
    }

}

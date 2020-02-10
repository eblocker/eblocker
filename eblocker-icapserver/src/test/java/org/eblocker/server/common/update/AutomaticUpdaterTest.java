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
package org.eblocker.server.common.update;

import static org.junit.Assert.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.service.RegistrationService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class AutomaticUpdaterTest {
	private Clock localClock;
	private Random random;
    private RegistrationService registrationService;
    private DataSource dataSource;
    private SystemUpdater systemUpdater;
    private ScheduledExecutorService executorService;
    private static final ZoneOffset zoneOffsetCET = ZoneOffset.of("+01:00"); // CET in the winter

	@Before
	public void setup() {
	    localClock = Mockito.mock(Clock.class);
	    Mockito.when(localClock.getZone()).thenReturn(ZoneId.of("CET"));

	    random = Mockito.mock(Random.class);
        Mockito.when(random.nextDouble()).thenReturn(0.25);

        registrationService = Mockito.mock(RegistrationService.class);

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getLastUpdateTime()).thenReturn(null);

        systemUpdater = Mockito.mock(SystemUpdater.class);

        executorService = Mockito.mock(ScheduledExecutorService.class);
	}

	private AutomaticUpdater createUpdater(int startTimeHour, int startTimeMin, int endTimeHour, int endTimeMin) {
        String config = createConfiguration(startTimeHour, startTimeMin, endTimeHour, endTimeMin).toJSONString();
        AutomaticUpdater updater =  new AutomaticUpdater(executorService, systemUpdater, dataSource,new ObjectMapper(), config, localClock, random, registrationService);
        updater.init();
        return updater;
	}

	private AutomaticUpdaterConfiguration createConfiguration(int beginHour, int beginMin, int endHour, int endMin) {
	    AutomaticUpdaterConfiguration c = new AutomaticUpdaterConfiguration();
	    c.setBeginHour(beginHour);
	    c.setBeginMin(beginMin);
	    c.setEndHour(endHour);
	    c.setEndMin(endMin);
	    return c;
    }

	@Test
    public void testLoadConfigFromDatabase() {
	    AutomaticUpdaterConfiguration configDb = createConfiguration(6, 0, 7, 0);
	    Mockito.when(dataSource.getAutomaticUpdateConfig()).thenReturn(configDb);

	    AutomaticUpdater updater = createUpdater(3, 0, 4, 0);

        // Verify that the DB configuration is used instead of the default configuration:
	    AutomaticUpdaterConfiguration configOut = updater.getConfiguration();
	    assertEquals(LocalTime.of(6, 0), configOut.getNotBefore());
        assertEquals(LocalTime.of(7, 0), configOut.getNotAfter());
    }

    @Test
    public void testSetNewConfiguration() {
        AutomaticUpdater updater = createUpdater(3, 0, 4, 0);
        AutomaticUpdaterConfiguration newConfiguration = createConfiguration(6, 0, 7, 0);

        Instant now = LocalDateTime.of(2018, 12, 27, 17, 59, 0).toInstant(zoneOffsetCET);
        Mockito.when(localClock.instant()).thenReturn(now);

        ScheduledFuture updateTask = Mockito.mock(ScheduledFuture.class);
        Mockito.when(executorService.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MINUTES))).thenReturn(updateTask);

        updater.start();
        // Updates are scheduled for tomorrow between 3:00 and 4:00
        assertEquals(LocalDateTime.of(2018, 12, 28, 3, 15, 0), updater.getNextUpdate());

        // User sets a new configuration:
        updater.setNewConfiguration(newConfiguration);

        // Update task is cancelled:
        Mockito.verify(updateTask).cancel(false);

        // Updates are re-scheduled for tomorrow between 6:00 and 7:00
        assertEquals(LocalDateTime.of(2018, 12, 28, 6, 15, 0), updater.getNextUpdate());
    }

	@Test
    public void testScheduleAndStartUpdates() throws Exception {
	    LocalDateTime lastUpdate  = LocalDateTime.of(2018, 12, 27, 3, 20, 0);
	    Instant       bootTime    = LocalDateTime.of(2018, 12, 27, 3, 30, 0).toInstant(zoneOffsetCET);
	    LocalDateTime nextUpdate  = LocalDateTime.of(2018, 12, 28, 3, 15, 0);
	    LocalDateTime nextUpdate2 = LocalDateTime.of(2018, 12, 29, 3, 15, 0);

        Mockito.when(dataSource.getLastUpdateTime()).thenReturn(lastUpdate);
        Mockito.when(localClock.instant()).thenReturn(bootTime);
        AutomaticUpdater updater = createUpdater(3, 0, 4, 0);
	    updater.start();

	    // Updates are scheduled:
        assertEquals(nextUpdate, updater.getNextUpdate());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
	    Mockito.verify(executorService).schedule(task.capture(), Mockito.eq(24*60-15+1L), Mockito.eq(TimeUnit.MINUTES));

	    // Run update task (normally the executorService would do this):
        Mockito.when(localClock.instant()).thenReturn(nextUpdate.toInstant(zoneOffsetCET));
	    task.getValue().run();

	    // Updates were started:
	    Mockito.verify(systemUpdater).startUpdate();

	    // And the next update is scheduled:
	    assertEquals(nextUpdate2, updater.getNextUpdate());
    }

    @Test
    public void testCalculateNextUpdate() {
        // Window for updates: 3:00 to 4:00
        LocalTime notBefore = LocalTime.of(3, 0);
        LocalTime notAfter  = LocalTime.of(4, 0);
        LocalDateTime now, earliest, latest;

        // We are now BEFORE the time window for updates:
        now      = LocalDateTime.of(2019, 12, 27, 2, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 27, 3, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 27, 4, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));

        // We are now IN the time window for updates => schedule for next day
        now      = LocalDateTime.of(2019, 12, 27, 3, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 28, 3, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 28, 4, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));

        // We are now AFTER the time window for updates => schedule for next day
        now      = LocalDateTime.of(2019, 12, 27, 4, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 28, 3, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 28, 4, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));
    }

    @Test
    public void testCalculateNextUpdateWithDateChange() {
        // Window for updates: 23:00 to 1:00
        LocalTime notBefore = LocalTime.of(23, 0);
        LocalTime notAfter  = LocalTime.of(1, 0);
        LocalDateTime now, earliest, latest;

        // We are now BEFORE the time window for updates:
        now      = LocalDateTime.of(2019, 12, 27, 22, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 27, 23, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 28, 1, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));

        // We are now IN the time window for updates BEFORE MIDNIGHT => schedule for next day
        now      = LocalDateTime.of(2019, 12, 27, 23, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 28, 23, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 29, 1, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));

        // We are now IN the time window for updates AFTER MIDNIGHT => schedule for later today or next day
        now      = LocalDateTime.of(2019, 12, 27, 0, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 27, 23, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 28, 1, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));

        // We are now AFTER the time window for updates => schedule for later today or next day
        now      = LocalDateTime.of(2019, 12, 27, 1, 12, 16);
        earliest = LocalDateTime.of(2019, 12, 27, 23, 0, 0);
        latest   = LocalDateTime.of(2019, 12, 28, 1, 0, 0);
        assertEquals(earliest, AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 0.0));
        assertEquals(latest,   AutomaticUpdater.calculateNextUpdate(now, notBefore, notAfter, 1.0));
    }
}

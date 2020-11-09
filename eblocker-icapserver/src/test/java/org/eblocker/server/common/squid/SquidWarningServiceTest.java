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
package org.eblocker.server.common.squid;

import com.google.common.collect.Lists;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.TestClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SquidWarningServiceTest {

    private final long MAX_BACKLOG_DAYS = 3;
    private final long UPDATE_TASK_INITIAL_DELAY = 300;
    private final long UPDATE_TASK_FIXED_RATE = 900;
    private String IGNORED_ERRORS = "ignored:0, ignored:1";

    private TestClock clock;
    private DataSource dataSource;
    private DeviceService deviceService;
    private ScheduledExecutorService scheduledExecutorService;
    private SquidCacheLogReader squidCacheLogReader;
    private SquidConfigController squidConfigController;
    private SquidWarningService squidWarningService;

    @Before
    public void setUp() {
        clock = new TestClock(ZonedDateTime.of(2018, 1, 15, 16, 40, 0, 0, ZoneId.systemDefault()));

        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        squidConfigController = Mockito.mock(SquidConfigController.class);

        squidCacheLogReader = Mockito.mock(SquidCacheLogReader.class);
        Mockito.when(squidCacheLogReader.pollFailedConnections()).thenReturn(Collections.emptyList());

        squidWarningService = new SquidWarningService(MAX_BACKLOG_DAYS, IGNORED_ERRORS, UPDATE_TASK_INITIAL_DELAY, UPDATE_TASK_FIXED_RATE, clock, dataSource, deviceService, scheduledExecutorService, squidCacheLogReader, squidConfigController);
    }

    @Test
    public void testInitEnabled() throws IOException {
        Mockito.when(dataSource.getSslRecordErrors()).thenReturn(true);
        squidWarningService.init();

        Mockito.verify(squidCacheLogReader).start();
        Mockito.verify(scheduledExecutorService)
            .scheduleAtFixedRate(Mockito.any(Runnable.class),
                Mockito.eq(UPDATE_TASK_INITIAL_DELAY),
                Mockito.eq(UPDATE_TASK_FIXED_RATE),
                Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testInitDisabled() {
        Mockito.when(dataSource.getSslRecordErrors()).thenReturn(true);

        Mockito.verifyZeroInteractions(squidCacheLogReader);
        Mockito.verifyZeroInteractions(scheduledExecutorService);
    }

    @Test
    public void testEnable() throws IOException {
        squidWarningService.init();

        squidWarningService.setRecordingFailedConnectionsEnabled(true);

        Mockito.verify(scheduledExecutorService)
            .scheduleAtFixedRate(Mockito.any(Runnable.class),
                Mockito.eq(UPDATE_TASK_INITIAL_DELAY),
                Mockito.eq(UPDATE_TASK_FIXED_RATE),
                Mockito.eq(TimeUnit.SECONDS));
        Mockito.verify(squidCacheLogReader).start();
        Mockito.verify(dataSource).setSslRecordErrors(true);
        Mockito.verify(squidConfigController).updateSquidConfig();
    }

    @Test
    public void testDisable() throws IOException {
        Mockito.when(dataSource.getSslRecordErrors()).thenReturn(true);

        ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
        Mockito.when(scheduledExecutorService.scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(UPDATE_TASK_INITIAL_DELAY), Mockito.eq(UPDATE_TASK_FIXED_RATE), Mockito.eq(TimeUnit.SECONDS))).thenReturn(future);
        squidWarningService.init();

        squidWarningService.setRecordingFailedConnectionsEnabled(false);

        Mockito.verify(squidCacheLogReader).stop();
        Mockito.verify(dataSource).setSslRecordErrors(false);
        Mockito.verify(future).cancel(false);
        Mockito.verify(squidConfigController).updateSquidConfig();
        Mockito.verify(dataSource).delete(FailedConnectionsEntity.class);
    }

    @Test
    public void testRemoveOldEntries() {
        Mockito.when(dataSource.get(FailedConnectionsEntity.class)).thenReturn(new FailedConnectionsEntity(new ArrayList<>(Arrays.asList(
            new FailedConnection(Arrays.asList("device:000000000000"), Arrays.asList("eblocker.com"), Arrays.asList("error:0"), ZonedDateTime.of(2018, 1, 10, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000000"), Arrays.asList("etracker.com"), Arrays.asList("error:0"), ZonedDateTime.of(2018, 1, 14, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000000"), Arrays.asList("random.com"), Arrays.asList("error:0"), ZonedDateTime.of(2018, 1, 15, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant())))));

        clock.setZonedDateTime(ZonedDateTime.of(2018, 1, 15, 0, 0, 0, 0, ZoneId.systemDefault()));

        squidWarningService.updatedFailedConnections();

        ArgumentCaptor<FailedConnectionsEntity> captor = ArgumentCaptor.forClass(FailedConnectionsEntity.class);
        Mockito.verify(dataSource).save(captor.capture());

        FailedConnectionsEntity entity = captor.getValue();
        List<FailedConnection> connections = entity.getFailedConnections();
        Assert.assertEquals(2, connections.size());
        Assert.assertEquals("etracker.com", connections.get(0).getDomains().get(0));
        Assert.assertEquals("random.com", connections.get(1).getDomains().get(0));
    }

    @Test
    public void testClearFailedConnections() {
        squidWarningService.clearFailedConnections();

        Mockito.verify(squidCacheLogReader).pollFailedConnections();
        Mockito.verify(dataSource).delete(FailedConnectionsEntity.class);
    }

    @Test
    public void testDeviceChange() {
        Device device = setupMocksForFailedConnectionsDevice();

        ArgumentCaptor<DeviceService.DeviceChangeListener> captor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(captor.capture());
        DeviceService.DeviceChangeListener listener = captor.getValue();
        Assert.assertNotNull(listener);

        listener.onChange(device);

        assertFailedConnectionsDeleted();
    }

    @Test
    public void testDeviceDelete() {
        Device device = setupMocksForFailedConnectionsDevice();

        ArgumentCaptor<DeviceService.DeviceChangeListener> captor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(captor.capture());
        DeviceService.DeviceChangeListener listener = captor.getValue();
        Assert.assertNotNull(listener);

        listener.onDelete(device);

        assertFailedConnectionsDeleted();
    }

    @Test
    public void testClearFailedConnectionsForDevice() {
        Device device = setupMocksForFailedConnectionsDevice();
        squidWarningService.clearFailedConnections(device);
        assertFailedConnectionsDeleted();
    }

    private Device setupMocksForFailedConnectionsDevice() {
        Device device = createDevice("device:000000000000", true, true, false);
        Mockito.when(dataSource.get(FailedConnectionsEntity.class)).thenReturn(new FailedConnectionsEntity(Lists.newArrayList(
            new FailedConnection(Lists.newArrayList("device:000000000000"), Lists.newArrayList("eblocker.com"), Lists.newArrayList("error:0"), clock.instant()),
            new FailedConnection(Lists.newArrayList("device:000000000000", "device:000000000001"), Lists.newArrayList("random.com"), Lists.newArrayList("error:0"), clock.instant()))));
        return device;
    }

    private void assertFailedConnectionsDeleted() {
        ArgumentCaptor<FailedConnectionsEntity> captor = ArgumentCaptor.forClass(FailedConnectionsEntity.class);
        Mockito.verify(dataSource).save(captor.capture());
        List<FailedConnection> connections = captor.getValue().getFailedConnections();
        Assert.assertEquals(1, connections.size());
        Assert.assertEquals(Lists.newArrayList("device:000000000001"), connections.get(0).getDeviceIds());
        Assert.assertEquals(Lists.newArrayList("random.com"), connections.get(0).getDomains());
        Assert.assertEquals(Lists.newArrayList("error:0"), connections.get(0).getErrors());
        Assert.assertEquals(clock.instant(), connections.get(0).getLastOccurrence());
    }

    @Test
    public void testUpdate() {
        Mockito.when(deviceService.getDeviceById("device:000000000000")).thenReturn(createDevice("device:000000000000", true, true, true));
        Mockito.when(deviceService.getDeviceById("device:000000000001")).thenReturn(createDevice("device:000000000001", true, true, true));
        Mockito.when(deviceService.getDeviceById("device:000000000002")).thenReturn(createDevice("device:000000000002", true, true, true));
        Mockito.when(deviceService.getDeviceById("device:000000000003")).thenReturn(createDevice("device:000000000003", true, true, false));

        Mockito.when(dataSource.get(FailedConnectionsEntity.class)).thenReturn(new FailedConnectionsEntity(Lists.newArrayList(
            new FailedConnection(Lists.newArrayList("device:000000000000"), Lists.newArrayList("eblocker.com"), Lists.newArrayList("error:0"), clock.instant().minusSeconds(64)),
            new FailedConnection(Lists.newArrayList("device:000000000000"), Lists.newArrayList("random.com"), Lists.newArrayList("error:3", "error:4"), clock.instant().minusSeconds(63)),
            new FailedConnection(Lists.newArrayList("device:000000000000"), Lists.newArrayList("another.com"), Lists.newArrayList("error:3"), clock.instant().minusSeconds(62)),
            new FailedConnection(Lists.newArrayList("device:000000000000", "device:000000000001"), Lists.newArrayList("etracker.com"), Lists.newArrayList("error:2"), clock.instant().minusSeconds(61)))));

        Mockito.when(squidCacheLogReader.pollFailedConnections()).thenReturn(Lists.newArrayList(
            new FailedConnectionLogEntry(clock.instant().minusSeconds(4), "error:0", "device:000000000000", null, "eblocker.com", null),      // same error
            new FailedConnectionLogEntry(clock.instant().minusSeconds(3), "error:1", "device:000000000000", null, "random.com", null),        // additional error, same domain
            new FailedConnectionLogEntry(clock.instant().minusSeconds(2), "error:3", "device:000000000001", null, "another.com", null),       // additional device
            new FailedConnectionLogEntry(clock.instant().minusSeconds(2), "ignored:1", "device:000000000001", null, "another.com", null),     // ignored error
            new FailedConnectionLogEntry(clock.instant().minusSeconds(1), "error:0", "device:000000000002", null, "blog.com", null),          // new error
            new FailedConnectionLogEntry(clock.instant().minusSeconds(10), "error:1", "device:000000000003", null, "blog.com", null)));       // additional error; but recording disabled for this device

        squidWarningService.updatedFailedConnections();

        ArgumentCaptor<FailedConnectionsEntity> captor = ArgumentCaptor.forClass(FailedConnectionsEntity.class);
        Mockito.verify(dataSource).save(captor.capture());

        FailedConnectionsEntity entity = captor.getValue();
        List<FailedConnection> connections = entity.getFailedConnections();
        Assert.assertEquals(5, connections.size());

        Assert.assertEquals(Arrays.asList("etracker.com"), connections.get(0).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000000", "device:000000000001"), connections.get(0).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:2"), connections.get(0).getErrors());
        Assert.assertEquals(clock.instant().minusSeconds(61), connections.get(0).getLastOccurrence());

        Assert.assertEquals(Arrays.asList("eblocker.com"), connections.get(1).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000000"), connections.get(1).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:0"), connections.get(1).getErrors());
        Assert.assertEquals(clock.instant().minusSeconds(4), connections.get(1).getLastOccurrence());

        Assert.assertEquals(Arrays.asList("random.com"), connections.get(2).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000000"), connections.get(2).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:3", "error:4", "error:1"), connections.get(2).getErrors());
        Assert.assertEquals(clock.instant().minusSeconds(3), connections.get(2).getLastOccurrence());

        Assert.assertEquals(Arrays.asList("another.com"), connections.get(3).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000000", "device:000000000001"), connections.get(3).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:3"), connections.get(3).getErrors());
        Assert.assertEquals(clock.instant().minusSeconds(2), connections.get(3).getLastOccurrence());

        Assert.assertEquals(Arrays.asList("blog.com"), connections.get(4).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000002"), connections.get(4).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:0"), connections.get(4).getErrors());
        Assert.assertEquals(clock.instant().minusSeconds(1), connections.get(4).getLastOccurrence());
    }

    @Test
    public void testUpdateNoChanges() {
        Mockito.when(dataSource.get(FailedConnectionsEntity.class)).thenReturn(new FailedConnectionsEntity(Lists.newArrayList(
            new FailedConnection(Lists.newArrayList("device:000000000000"), Lists.newArrayList("eblocker.com"), Lists.newArrayList("error:0"), clock.instant().minusSeconds(64)))));

        Mockito.when(squidCacheLogReader.pollFailedConnections()).thenReturn(Collections.emptyList());

        squidWarningService.updatedFailedConnections();

        Mockito.verify(dataSource, Mockito.never()).save(FailedConnectionsEntity.class);
    }

    @Test
    public void testDomainExtraction() {
        Mockito.when(dataSource.get(FailedConnectionsEntity.class)).thenReturn(new FailedConnectionsEntity(new ArrayList<>()));
        Mockito.when(deviceService.getDeviceById("device:000000000000")).thenReturn(createDevice("device:000000000000", true, true, true));

        Assert.assertNull(runAndNoResult(false, false, false));
        Assert.assertEquals(Arrays.asList("*.eblocker.com", "eblocker.com"), runAndGetResult(false, false, true).getDomains());
        Assert.assertEquals(Arrays.asList("sni.com"), runAndGetResult(false, true, false).getDomains());
        Assert.assertEquals(Arrays.asList("sni.com"), runAndGetResult(false, true, true).getDomains());
        Assert.assertEquals(Arrays.asList("host.com"), runAndGetResult(true, false, false).getDomains());
        Assert.assertEquals(Arrays.asList("*.eblocker.com", "eblocker.com"), runAndGetResult(true, false, true).getDomains());
        Assert.assertEquals(Arrays.asList("sni.com"), runAndGetResult(true, true, false).getDomains());
        Assert.assertEquals(Arrays.asList("sni.com"), runAndGetResult(true, true, true).getDomains());
    }

    private FailedConnection runAndGetResult(boolean host, boolean sni, boolean certificate) {
        run(host, sni, certificate);

        ArgumentCaptor<FailedConnectionsEntity> captor = ArgumentCaptor.forClass(FailedConnectionsEntity.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertEquals(1, captor.getValue().getFailedConnections().size());
        return captor.getValue().getFailedConnections().get(0);
    }

    private FailedConnection runAndNoResult(boolean host, boolean sni, boolean certificate) {
        run(host, sni, certificate);
        Mockito.verify(dataSource, Mockito.never()).save(FailedConnectionsEntity.class);
        return null;
    }

    private void run(boolean host, boolean sni, boolean certificate) {
        Mockito.reset(dataSource);
        Mockito.when(squidCacheLogReader.pollFailedConnections()).thenReturn(Collections.singletonList(new FailedConnectionLogEntry(clock.instant(), "error:0", "device:000000000000",
            host ? "host.com" : null,
            sni ? "sni.com" : null,
            certificate ?
                "-----BEGIN CERTIFICATE-----\nMIIDgDCCAmigAwIBAgIULjOrzH+polOlicnqXEWhjHtyD9gwDQYJKoZIhvcNAQEL\nBQAwLjEsMCoGA1UEAwwjZUJsb2NrZXIgLSBNeSBlQmxvY2tlciAtIDIwMTcvMDcv\nMTgwHhcNMTcwNTE3MDAwMDAwWhcNMTgwNTE3MjM1OTU5WjBcMSEwHwYDVQQLExhE"
                    + "\nb21haW4gQ29udHJvbCBWYWxpZGF0ZWQxHjAcBgNVBAsTFUVzc2VudGlhbFNTTCBX\naWxkY2FyZDEXMBUGA1UEAwwOKi5lYmxvY2tlci5jb20wggEiMA0GCSqGSIb3DQEB\nAQUAA4IBDwAwggEKAoIBAQDdd5HuLu6r9Ms0Umd3ot0ylpnPDyS3P1wialohf+bB"
                    + "\nZjsWDtHqzntT6IDnka84wLv3ZuzMHBklL4lFSJ59VydeioOyF9qBdP15J4AxV04p\nW09OFc2jASSgfFP/w1oHVXakA1IuZ3qyjJ5Jg8XzMvnNVl5BzRR7T5rcD5P09OK9\nZ4HD0wCKX+b2o8zC43zRgIws9cTW08wzp9f28P99MV5zfP0KmfnQOiGE6wBW/930\nrnPVHKB8oF3Q1jZmJVRsAUf/n"
                    + "/UkPcq7FVKlFOVxke1XfhfvU4olIAEPZ+nNgjDY\nV+DOENR/Ez4rKt5nKr99fjLK+u9jocm8+3Z9Rpaa2gLLAgMBAAGjaDBmMCcGA1Ud\nEQQgMB6CDiouZWJsb2NrZXIuY29tggxlYmxvY2tlci5jb20wDgYDVR0PAQH/BAQD"
                    + "\nAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAA\nMA0GCSqGSIb3DQEBCwUAA4IBAQCfr3YhE5mF2cIxH5lTk1X6XyJTJ8hJM/egRZWP\n0/zGXqzfq87CZ4MLs85MubQBjVPDN6mdtI1YGD4SxCwImOf/TE9as3q0kKpg1nIS\nxCcba3Huc7W6o8i/w1"
                    + "/gPwFwHcxBqk8peKtdvIQGkBb8N8hXIll9XYsCi0Ra60M4\nJshS1g+WjCpCUkiN4f0HLmio6ODheHV1n4S9DczBtqRosd4uZ+PvhnVxQjWLdykB\ntqmpvYgCDb4f94IVhBnsyD17NzGrZD0C/qgP/RbhWzNHXkcVzcvqcXQk+aeIA0Xn\nV7g8gnXTURmBY2J76epMOW/KsX3iNPdtENy0znze7rNtgkyA\n"
                    + "-----END CERTIFICATE-----\n" :
                null
        )));
        squidWarningService.updatedFailedConnections();
    }

    private Device createDevice(String id, boolean enabled, boolean sslEnabled, boolean sslRecordErrorsEnabled) {
        Device device = new Device();
        device.setId(id);
        device.setEnabled(enabled);
        device.setSslEnabled(sslEnabled);
        device.setSslRecordErrorsEnabled(sslRecordErrorsEnabled);
        return device;
    }
}


package org.eblocker.server.http.service;

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.squid.Suggestions;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class FailedConnectionSuggestionServiceTest {

    private AppModuleService appModuleService;
    private DeviceService deviceService;
    private SquidWarningService squidWarningService;
    private FailedConnectionSuggestionService failedConnectionSuggestionService;

    private TestClock clock;

    @Before
    public void setup() {
        appModuleService = Mockito.mock(AppModuleService.class);
        deviceService = Mockito.mock(DeviceService.class);
        squidWarningService = Mockito.mock(SquidWarningService.class);

        failedConnectionSuggestionService = new FailedConnectionSuggestionService(squidWarningService, appModuleService, deviceService);

        clock = new TestClock(ZonedDateTime.of(2018, 1, 15, 16, 40, 0, 0, ZoneId.systemDefault()));
    }

    @Test
    public void testSuggestions() {
        Mockito.when(deviceService.getDeviceById("device:000000000000")).thenReturn(createDevice("device:000000000000", false, false, true));
        Mockito.when(deviceService.getDeviceById("device:000000000001")).thenReturn(createDevice("device:000000000001", false, true, true));
        Mockito.when(deviceService.getDeviceById("device:000000000002")).thenReturn(createDevice("device:000000000002", true, false, true));
        Mockito.when(deviceService.getDeviceById("device:000000000003")).thenReturn(createDevice("device:000000000003", true, true, true));
        Mockito.when(deviceService.getDeviceById("device:000000000013")).thenReturn(createDevice("device:000000000013", true, true, true));

        Mockito.when(squidWarningService.update()).thenReturn(new ArrayList<>(Arrays.asList(
            new FailedConnection(Arrays.asList("device:000000000000", "device:000000000001", "device:000000000002", "device:000000000003"), Arrays.asList("test.com"), Arrays.asList("error:0"), ZonedDateTime.of(2018, 1, 14, 1, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("global0.com"), Arrays.asList("error:0"), ZonedDateTime.of(2018, 1, 14, 1, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("global1.com"), Arrays.asList("error:1"), ZonedDateTime.of(2018, 1, 14, 2, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("global2.com"), Arrays.asList("error:2"), ZonedDateTime.of(2018, 1, 14, 3, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("global3.com"), Arrays.asList("error:3"), ZonedDateTime.of(2018, 1, 14, 4, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("global4.com"), Arrays.asList("error:4"), ZonedDateTime.of(2018, 1, 14, 5, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000013"), Arrays.asList("api.global0.com"), Arrays.asList("error:1"), ZonedDateTime.of(2018, 1, 14, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000013"), Arrays.asList("shared-cdn.com", "shared-cdn2.com"), Arrays.asList("error:5"), ZonedDateTime.of(2018, 1, 12, 3, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("test.com"), Arrays.asList("error:10"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000013"), Arrays.asList("fancy-pants.com"), Arrays.asList("error:11"), ZonedDateTime.of(2018, 4, 13, 10, 35, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000013"), Arrays.asList("fancy.com", "pants.com", "fancy-pants.com"), Arrays.asList("error:10"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()),
            new FailedConnection(Arrays.asList("device:000000000003"), Arrays.asList("pants.com"), Arrays.asList("error:12"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant())
        )));

        // note: app modules use "name" as key instead of id :/
        Mockito.when(appModuleService.getAll()).thenReturn(Arrays.asList(
            createAppModule(0, "app0", false, false, "global0.com", "shared-cdn.com"),
            createAppModule(1, "app1", false, true, "global1.com"),
            createAppModule(2, "app2", true, false, "global2.com", "shared-cdn.com"),
            createAppModule(3, "app3", true, true, "global3.com"),
            createAppModule(4, "app4", false, false, "global4.com")));

        clock.setZonedDateTime(ZonedDateTime.of(2018, 1, 15, 0, 0, 0, 0, ZoneId.systemDefault()));
        Suggestions suggestions = failedConnectionSuggestionService.getFailedConnectionsByAppModules();

        Assert.assertEquals(4, suggestions.getDomains().size());
        Assert.assertNotNull(suggestions.getDomains().get("test.com"));
        Assert.assertEquals(Arrays.asList("test.com"), suggestions.getDomains().get("test.com").getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000003"), suggestions.getDomains().get("test.com").getDeviceIds());
        Assert.assertEquals(Sets.newHashSet("error:0", "error:10"), new HashSet<>(suggestions.getDomains().get("test.com").getErrors()));
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("test.com").getLastOccurrence());

        Assert.assertEquals(Arrays.asList("fancy.com"), suggestions.getDomains().get("fancy.com").getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000013"), suggestions.getDomains().get("fancy.com").getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:10"), suggestions.getDomains().get("fancy.com").getErrors());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("fancy.com").getLastOccurrence());

        Assert.assertEquals(Arrays.asList("pants.com"), suggestions.getDomains().get("pants.com").getDomains());
        Assert.assertEquals(Sets.newHashSet("device:000000000003", "device:000000000013"), new HashSet<>(suggestions.getDomains().get("pants.com").getDeviceIds()));
        Assert.assertEquals(Sets.newHashSet("error:10", "error:12"), new HashSet<>(suggestions.getDomains().get("pants.com").getErrors()));
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("pants.com").getLastOccurrence());

        Assert.assertEquals(Arrays.asList("fancy-pants.com"), suggestions.getDomains().get("fancy-pants.com").getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000013"), suggestions.getDomains().get("fancy-pants.com").getDeviceIds());
        Assert.assertEquals(Sets.newHashSet("error:10", "error:11"), new HashSet<>(suggestions.getDomains().get("fancy-pants.com").getErrors()));
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 13, 10, 35, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("fancy-pants.com").getLastOccurrence());

        Assert.assertEquals(2, suggestions.getModules().size());
        Assert.assertNotNull(suggestions.getModules().get(0));
        Assert.assertEquals(Sets.newHashSet("api.global0.com", "global0.com", "shared-cdn.com", "shared-cdn2.com"), Sets.newHashSet(suggestions.getModules().get(0).getDomains()));
        Assert.assertEquals(Sets.newHashSet("device:000000000003", "device:000000000013"), Sets.newHashSet(suggestions.getModules().get(0).getDeviceIds()));
        Assert.assertEquals(Sets.newHashSet("error:0", "error:1", "error:5"), Sets.newHashSet(suggestions.getModules().get(0).getErrors()));
        Assert.assertEquals(ZonedDateTime.of(2018, 1, 14, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getModules().get(0).getLastOccurrence());

        Assert.assertNotNull(suggestions.getModules().get(4));
        Assert.assertEquals(Arrays.asList("global4.com"), suggestions.getModules().get(4).getDomains());
        Assert.assertEquals(Arrays.asList("device:000000000003"), suggestions.getModules().get(4).getDeviceIds());
        Assert.assertEquals(Arrays.asList("error:4"), suggestions.getModules().get(4).getErrors());
        Assert.assertEquals(ZonedDateTime.of(2018, 1, 14, 5, 0, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getModules().get(4).getLastOccurrence());
    }

    private AppWhitelistModule createAppModule(int id, String name, boolean enabled, boolean hidden, String... domains) {
        return new AppWhitelistModule(id, name, null, Arrays.asList(domains), null, null, null, null, enabled, null, null, null, null, hidden);
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

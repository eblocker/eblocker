package org.eblocker.server.http.service;

import com.google.common.collect.Sets;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.http.ssl.Suggestions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

public class FailedConnectionSuggestionServiceTest {

    private AppModuleService appModuleService;
    private DeviceService deviceService;
    private SquidWarningService squidWarningService;
    private FailedConnectionSuggestionService failedConnectionSuggestionService;
    private int autoSSLAppModuleId = 9997;

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
        when(deviceService.getDeviceById("device:000000000000")).thenReturn(createDevice("device:000000000000", false, false, true));
        when(deviceService.getDeviceById("device:000000000001")).thenReturn(createDevice("device:000000000001", false, true, true));
        when(deviceService.getDeviceById("device:000000000002")).thenReturn(createDevice("device:000000000002", true, false, true));
        when(deviceService.getDeviceById("device:000000000003")).thenReturn(createDevice("device:000000000003", true, true, true));
        when(deviceService.getDeviceById("device:000000000013")).thenReturn(createDevice("device:000000000013", true, true, true));

        when(squidWarningService.updatedFailedConnections()).thenReturn(new ArrayList<>(asList(
                new FailedConnection(asList("device:000000000000", "device:000000000001", "device:000000000002", "device:000000000003"), asList("test.com"), asList("error:0"),
                        ZonedDateTime.of(2018, 1, 14, 1, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global0.com"), asList("error:0"), ZonedDateTime.of(2018, 1, 14, 1, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global1.com"), asList("error:1"), ZonedDateTime.of(2018, 1, 14, 2, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global2.com"), asList("error:2"), ZonedDateTime.of(2018, 1, 14, 3, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global3.com"), asList("error:3"), ZonedDateTime.of(2018, 1, 14, 4, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global4.com"), asList("error:4"), ZonedDateTime.of(2018, 1, 14, 5, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000013"), asList("api.global0.com"), asList("error:1"), ZonedDateTime.of(2018, 1, 14, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000013"), asList("shared-cdn.com", "shared-cdn2.com"), asList("error:5"), ZonedDateTime.of(2018, 1, 12, 3, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("test.com"), asList("error:10"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000013"), asList("fancy-pants.com"), asList("error:11"), ZonedDateTime.of(2018, 4, 13, 10, 35, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000013"), asList("fancy.com", "pants.com", "fancy-pants.com"), asList("error:10"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("pants.com"), asList("error:12"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()))));

        // note: app modules use "name" as key instead of id :/
        AppWhitelistModule autoSsl = createAppModule(9997, "Auto-SSL-Test", false, false);
        when(appModuleService.getAll()).thenReturn(asList(
                createAppModule(0, "app0", false, false, "global0.com", "shared-cdn.com"),
                createAppModule(1, "app1", false, true, "global1.com"),
                createAppModule(2, "app2", true, false, "global2.com", "shared-cdn.com"),
                createAppModule(3, "app3", true, true, "global3.com"),
                createAppModule(4, "app4", false, false, "global4.com"),
                autoSsl));
        when(appModuleService.getAutoSslAppModule()).thenReturn(autoSsl);

        clock.setZonedDateTime(ZonedDateTime.of(2018, 1, 15, 0, 0, 0, 0, ZoneId.systemDefault()));
        Suggestions suggestions = failedConnectionSuggestionService.getFailedConnectionsByAppModules();

        assertEquals(4, suggestions.getDomains().size());
        assertNotNull(suggestions.getDomains().get("test.com"));
        assertEquals(asList("test.com"), suggestions.getDomains().get("test.com").getDomains());
        assertEquals(asList("device:000000000003"), suggestions.getDomains().get("test.com").getDeviceIds());
        assertEquals(Sets.newHashSet("error:0", "error:10"), new HashSet<>(suggestions.getDomains().get("test.com").getErrors()));
        assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("test.com").getLastOccurrence());

        assertEquals(asList("fancy.com"), suggestions.getDomains().get("fancy.com").getDomains());
        assertEquals(asList("device:000000000013"), suggestions.getDomains().get("fancy.com").getDeviceIds());
        assertEquals(asList("error:10"), suggestions.getDomains().get("fancy.com").getErrors());
        assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("fancy.com").getLastOccurrence());

        assertEquals(asList("pants.com"), suggestions.getDomains().get("pants.com").getDomains());
        assertEquals(Sets.newHashSet("device:000000000003", "device:000000000013"), new HashSet<>(suggestions.getDomains().get("pants.com").getDeviceIds()));
        assertEquals(Sets.newHashSet("error:10", "error:12"), new HashSet<>(suggestions.getDomains().get("pants.com").getErrors()));
        assertEquals(ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("pants.com").getLastOccurrence());

        assertEquals(asList("fancy-pants.com"), suggestions.getDomains().get("fancy-pants.com").getDomains());
        assertEquals(asList("device:000000000013"), suggestions.getDomains().get("fancy-pants.com").getDeviceIds());
        assertEquals(Sets.newHashSet("error:10", "error:11"), new HashSet<>(suggestions.getDomains().get("fancy-pants.com").getErrors()));
        assertEquals(ZonedDateTime.of(2018, 4, 13, 10, 35, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getDomains().get("fancy-pants.com").getLastOccurrence());

        assertEquals(2, suggestions.getModules().size());
        assertNotNull(suggestions.getModules().get(0));
        assertEquals(Sets.newHashSet("api.global0.com", "global0.com", "shared-cdn.com", "shared-cdn2.com"), Sets.newHashSet(suggestions.getModules().get(0).getDomains()));
        assertEquals(Sets.newHashSet("device:000000000003", "device:000000000013"), Sets.newHashSet(suggestions.getModules().get(0).getDeviceIds()));
        assertEquals(Sets.newHashSet("error:0", "error:1", "error:5"), Sets.newHashSet(suggestions.getModules().get(0).getErrors()));
        assertEquals(ZonedDateTime.of(2018, 1, 14, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getModules().get(0).getLastOccurrence());

        assertNotNull(suggestions.getModules().get(4));
        assertEquals(asList("global4.com"), suggestions.getModules().get(4).getDomains());
        assertEquals(asList("device:000000000003"), suggestions.getModules().get(4).getDeviceIds());
        assertEquals(asList("error:4"), suggestions.getModules().get(4).getErrors());
        assertEquals(ZonedDateTime.of(2018, 1, 14, 5, 0, 0, 0, ZoneId.systemDefault()).toInstant(), suggestions.getModules().get(4).getLastOccurrence());
    }

    @Test
    public void testSuggestionsWithSSLErrorCollectingApp() {
        when(deviceService.getDeviceById("device:000000000003")).thenReturn(createDevice("device:000000000003", true, true, true));

        List<FailedConnection> failedConnections = asList(
                new FailedConnection(asList("device:000000000003"), asList("global0.com"), asList("error:0"), ZonedDateTime.of(2018, 1, 14, 1, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("global4.com"), asList("error:4"), ZonedDateTime.of(2018, 1, 14, 5, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                new FailedConnection(asList("device:000000000003"), asList("test.com"), asList("error:10"), ZonedDateTime.of(2018, 4, 13, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant()));
        when(squidWarningService.updatedFailedConnections()).thenReturn(new ArrayList<>(failedConnections));

        String[] allDomains = failedConnections.stream().map(FailedConnection::getDomains).flatMap(List::stream).toArray(String[]::new);

        // note: app modules use "name" as key instead of id :/
        AppWhitelistModule sslErrorCollectingApp = createAppModule(autoSSLAppModuleId, "SSL collector", false, false,
                allDomains);
        when(appModuleService.getAll()).thenReturn(asList(
                createAppModule(0, "app0", false, false, "global0.com", "shared-cdn.com"),
                createAppModule(4, "app4", false, false, "global4.com"),
                sslErrorCollectingApp));

        when(appModuleService.getAutoSslAppModule()).thenReturn(sslErrorCollectingApp);

        clock.setZonedDateTime(ZonedDateTime.of(2018, 1, 15, 0, 0, 0, 0, ZoneId.systemDefault()));
        Suggestions suggestions = failedConnectionSuggestionService.getFailedConnectionsByAppModules();

        assertEquals(1, suggestions.getDomains().size());
        assertNotNull(suggestions.getDomains().get("test.com"));
        assertEquals(asList("test.com"), suggestions.getDomains().get("test.com").getDomains());

        assertEquals(3, suggestions.getModules().size());
        assertNotNull(suggestions.getModules().get(0));
        assertEquals(Sets.newHashSet("global0.com"), Sets.newHashSet(suggestions.getModules().get(0).getDomains()));

        assertNotNull(suggestions.getModules().get(4));
        assertEquals(asList("global4.com"), suggestions.getModules().get(4).getDomains());

        assertNotNull(suggestions.getModules().get(autoSSLAppModuleId));
        assertEquals(asList(allDomains), suggestions.getModules().get(autoSSLAppModuleId).getDomains());
    }

    private AppWhitelistModule createAppModule(int id, String name, boolean enabled, boolean hidden, String... domains) {
        return new AppWhitelistModule(id, name, null, asList(domains), null, null, null, null, enabled, null, null, null, null, hidden);
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

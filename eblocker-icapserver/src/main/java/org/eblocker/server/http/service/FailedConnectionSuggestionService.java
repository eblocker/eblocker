package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.http.ssl.Suggestions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FailedConnectionSuggestionService {

    private final SquidWarningService squidWarningService;
    private final AppModuleService appModuleService;
    private final DeviceService deviceService;

    @Inject
    FailedConnectionSuggestionService(SquidWarningService squidWarningService,
                                      AppModuleService appModuleService,
                                      DeviceService deviceService) {

        this.squidWarningService = squidWarningService;
        this.appModuleService = appModuleService;
        this.deviceService = deviceService;
    }

    public Suggestions getFailedConnectionsByAppModules() {
        List<FailedConnection> failedConnections = filterDisabledDevices(squidWarningService.updatedFailedConnections());

        Map<AppWhitelistModule, List<FailedConnection>> connectionsByAppModule = createConnectionsByAppModule(failedConnections);

        addSslErrorsCollectingApp(connectionsByAppModule, failedConnections);

        Map<String, FailedConnection> domains = createDomainSuggestions(connectionsByAppModule);
        Map<Integer, FailedConnection> modules = createModuleSuggestions(connectionsByAppModule);

        return new Suggestions(domains, modules);
    }

    private Map<AppWhitelistModule, List<FailedConnection>> createConnectionsByAppModule(List<FailedConnection> failedConnections) {
        AppWhitelistModule autoSslAppModule = appModuleService.getAutoSslAppModule();
        List<AppWhitelistModule> appModules = appModuleService.getAll().stream()
            .filter(appModule -> !appModule.equals(autoSslAppModule))
            .collect(Collectors.toList());
        return failedConnections.stream()
            .map(fc -> new Tuple<>(findAppModules(appModules, fc.getDomains()), fc))
            .flatMap(this::flatModules)
            .collect(Collectors.groupingBy(t -> t.u, Collectors.mapping(t -> t.v, Collectors.toList())))
            .entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().orElse(null), Map.Entry::getValue));
    }

    private Stream<Tuple<Optional<AppWhitelistModule>, FailedConnection>> flatModules(Tuple<List<AppWhitelistModule>, FailedConnection> t) {
        if (t.u.isEmpty()) {
            return Stream.of(new Tuple<>(Optional.empty(), t.v));
        } else {
            return t.u.stream().map(u -> new Tuple<>(Optional.of(u), t.v));
        }
    }

    private void addSslErrorsCollectingApp(Map<AppWhitelistModule, List<FailedConnection>> connectionsByAppModule, List<FailedConnection> failedConnections) {
        AppWhitelistModule sslErrorsCollectingApp = appModuleService.getAutoSslAppModule();

        if (sslErrorsCollectingApp != null && !sslErrorsCollectingApp.isEnabled()) {
            List<String> whitelistedDomains = sslErrorsCollectingApp.getWhitelistedDomains();
            List<FailedConnection> sslErrorCollectingAppFailedCollections = failedConnections.stream()
                .filter(fc -> fc.getDomains().stream().anyMatch(whitelistedDomains::contains))
                .collect(Collectors.toList());
            if (!sslErrorCollectingAppFailedCollections.isEmpty()) {
                connectionsByAppModule.put(sslErrorsCollectingApp, sslErrorCollectingAppFailedCollections);
            }
        }
    }

    private Map<String, FailedConnection> createDomainSuggestions(Map<AppWhitelistModule, List<FailedConnection>> connectionsByAppModule) {
        List<FailedConnection> failedConnections = connectionsByAppModule.get(null);
        if (failedConnections == null) {
            return Collections.emptyMap();
        }

        return failedConnections.stream()
            .flatMap(c -> c.getDomains().stream().map(d -> new Tuple<>(d, c)))
            .map(t -> new Tuple<>(t.u, new FailedConnection(t.v.getDeviceIds(), Collections.singletonList(t.u), t.v.getErrors(), t.v.getLastOccurrence())))
            .collect(Collectors.groupingBy(
                t -> t.u,
                Collectors.mapping(
                    t -> t.v,
                    Collectors.collectingAndThen(Collectors.toList(), this::mergeFailedConnections))));
    }

    private FailedConnection mergeFailedConnections(List<FailedConnection> failedConnections) {
        Instant lastOccurrence = Instant.ofEpochMilli(0);
        Set<String> deviceIds = new TreeSet<>();
        Set<String> domains = new TreeSet<>();
        Set<String> errors = new TreeSet<>();
        for (FailedConnection connection : failedConnections) {
            if (lastOccurrence.isBefore(connection.getLastOccurrence())) {
                lastOccurrence = connection.getLastOccurrence();
            }
            deviceIds.addAll(connection.getDeviceIds());
            domains.addAll(connection.getDomains());
            errors.addAll(connection.getErrors());
        }
        return new FailedConnection(new ArrayList<>(deviceIds), new ArrayList<>(domains), new ArrayList<>(errors), lastOccurrence);
    }

    private Map<Integer, FailedConnection> createModuleSuggestions(Map<AppWhitelistModule, List<FailedConnection>> connectionsByAppModule) {
        return connectionsByAppModule.entrySet().stream()
            .filter(e -> e.getKey() != null)
            .filter(m -> !m.getKey().isEnabled() && !m.getKey().isHidden())
            .collect(Collectors.toMap(m -> m.getKey().getId(), e -> mergeFailedConnections(e.getValue())));
    }

    private List<FailedConnection> filterDisabledDevices(List<FailedConnection> failedConnections) {
        Predicate<String> activeDevice = id -> {
            Device device = deviceService.getDeviceById(id);
            return device != null && device.isEnabled() && device.isSslEnabled() && device.isSslRecordErrorsEnabled();
        };

        return failedConnections.stream()
            .map(c -> {
                List<String> activeDeviceIds = c.getDeviceIds().stream().filter(activeDevice).collect(Collectors.toList());
                return activeDeviceIds.equals(c.getDomains()) ? c : new FailedConnection(activeDeviceIds, c.getDomains(), c.getErrors(), c.getLastOccurrence());
            })
            .filter(c -> !c.getDeviceIds().isEmpty())
            .collect(Collectors.toList());
    }

    private List<AppWhitelistModule> findAppModules(List<AppWhitelistModule> appModules, List<String> domains) {
        Predicate<String> whitelistsAnyDomain = whitelistedDomain -> domains.stream().anyMatch(domain -> UrlUtils.isSameDomain(whitelistedDomain, domain));
        return appModules.stream()
            .filter(module -> module.getWhitelistedDomains().stream().anyMatch(whitelistsAnyDomain))
            .collect(Collectors.toList());
    }

    private static class Tuple<U, V> {
        U u;
        V v;

        public Tuple(U u, V v) {
            this.u = u;
            this.v = v;
        }

        @Override
        public String toString() {
            return "(" + u.toString() + ", " + v.toString() + ")";
        }
    }
}

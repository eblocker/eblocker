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
package org.eblocker.server.icap.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;
import org.eblocker.crypto.json.JSONCryptoHandler;
import org.eblocker.crypto.keys.KeyWrapper;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.icap.filter.csv.CSVLineParser;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.eblocker.server.icap.filter.learning.AsynchronousLearningFilter;
import org.eblocker.server.icap.filter.learning.NotLearningFilter;
import org.eblocker.server.icap.filter.learning.SynchronousLearningFilter;
import org.eblocker.server.icap.filter.url.UrlFilter;
import org.eblocker.server.icap.filter.url.UrlLineParser;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@SubSystemService(SubSystem.ICAP_SERVER)
public class FilterManager {
    private static final Logger log = LoggerFactory.getLogger(FilterManager.class);

    private static final Filter NULL_FILTER = new NullFilter(FilterPriority.LOWEST);

    private final String defaultFilterStoreConfigurations;
    private final Path cacheDirectory;
    private final String fileSuffix;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final KeyWrapper systemKey;
    private final Path tmpDir;

    private List<FilterStoreConfiguration> configurations;
    private volatile Cache cache; //NOSONAR: copy-on-write

    @Inject
    public FilterManager(@Named("filterStore.default.config") String defaultFilterStoreConfigurations,
                         @Named("filterStore.cache.directory") String cacheDirectory,
                         @Named("filterStore.cache.file.suffix") String fileSuffix,
                         DataSource dataSource,
                         ObjectMapper objectMapper,
                         @Named("systemKey") KeyWrapper systemKey,
                         @Named("tmpDir") String tmpDir) {
        this.defaultFilterStoreConfigurations = defaultFilterStoreConfigurations;
        this.cacheDirectory = Paths.get(cacheDirectory);
        this.fileSuffix = fileSuffix;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.systemKey = systemKey;
        this.tmpDir = Paths.get(tmpDir);
    }

    @SubSystemInit
    public void init() {
        configurations = dataSource.getAll(FilterStoreConfiguration.class);
        checkAndApplyDefaultConfigurationUpdates();
        load();
    }

    public Filter getFilter(Category category) {
        if (cache == null) {
            return NULL_FILTER;
        }
        return cache.filterByCategory.getOrDefault(category, NULL_FILTER);
    }

    public FilterStore getFilterStore(int id) {
        if (cache == null) {
            return null;
        }
        return cache.storeById.get(id);
    }

    public List<FilterStoreConfiguration> getFilterConfigurations() {
        return configurations;
    }

    public FilterStoreConfiguration getFilterStoreConfigurationById(int id) {
        return configurations.stream()
                .filter(c -> id == c.getId())
                .findAny()
                .orElse(null);
    }

    public synchronized FilterStoreConfiguration addFilter(FilterStoreConfiguration configuration) {
        FilterStore store = createFilterStore(configuration);

        int id = dataSource.nextId(FilterStoreConfiguration.class);
        FilterStoreConfiguration addedConfiguration = new FilterStoreConfiguration(id,
                configuration.getName(),
                configuration.getCategory(),
                false,
                configuration.getVersion(),
                configuration.getResources(),
                configuration.getLearningMode(),
                configuration.getFormat(),
                configuration.isLearnForAllDomains(),
                configuration.getRuleFilters(),
                configuration.isEnabled());

        List<FilterStoreConfiguration> newConfigurations = new ArrayList<>(configurations);
        newConfigurations.add(addedConfiguration);
        Map<Integer, FilterStore> storeById = new HashMap<>(cache.storeById);
        storeById.put(addedConfiguration.getId(), store);
        updateCache(newConfigurations, c -> storeById.get(c.getId()));

        // make new filter visible (and permanent)
        configurations = newConfigurations;
        dataSource.save(addedConfiguration, addedConfiguration.getId());
        return addedConfiguration;
    }

    /**
     * Updates a filter configuration.
     * <p>
     * !! ONLY SUPPORTS ENABLE/DISABLE CURRENTLY !!
     */
    public synchronized FilterStoreConfiguration updateFilter(FilterStoreConfiguration configuration) {
        int index = indexOfConfiguration(configuration.getId());
        if (index == -1) {
            return null;
        }

        List<FilterStoreConfiguration> updatedConfigurations = new ArrayList<>(configurations);
        FilterStoreConfiguration storedConfiguration = updatedConfigurations.get(index);
        FilterStoreConfiguration updatedConfiguration = new FilterStoreConfiguration(
                storedConfiguration.getId(),
                storedConfiguration.getName(),
                storedConfiguration.getCategory(),
                storedConfiguration.isBuiltin(),
                storedConfiguration.getVersion(),
                storedConfiguration.getResources(),
                storedConfiguration.getLearningMode(),
                storedConfiguration.getFormat(),
                storedConfiguration.isLearnForAllDomains(),
                storedConfiguration.getRuleFilters(),
                configuration.isEnabled());
        updatedConfigurations.set(index, updatedConfiguration);
        updateCache(updatedConfigurations, c -> cache.storeById.get(c.getId()));

        // make update configuration visible
        configurations = updatedConfigurations;
        dataSource.save(updatedConfiguration, updatedConfiguration.getId());
        return updatedConfiguration;
    }

    public synchronized void removeFilter(int id) {
        int i = indexOfConfiguration(id);
        if (i == -1) {
            return;
        }

        FilterStoreConfiguration configuration = configurations.get(i);
        if (configuration.isBuiltin()) {
            throw new IllegalArgumentException("can not delete builtin filter");
        }

        List<FilterStoreConfiguration> newConfigurations = new ArrayList<>(configurations);
        newConfigurations.remove(i);
        updateCache(newConfigurations, c -> cache.storeById.get(c.getId()));

        // make update visible
        configurations = newConfigurations;
        dataSource.delete(FilterStoreConfiguration.class, id);
    }

    private int indexOfConfiguration(int id) {
        for (int i = 0; i < configurations.size(); ++i) {
            if (configurations.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    private synchronized void load() {
        updateCache(configurations, this::loadOrCreate);
    }

    private synchronized void updateCache(List<FilterStoreConfiguration> configurations, Function<FilterStoreConfiguration, FilterStore> storeLoader) {
        Cache cache = new Cache();
        Map<Category, List<Filter>> filtersByCategory = new EnumMap<>(Category.class);
        for (FilterStoreConfiguration configuration : configurations) {
            FilterStore filterStore = storeLoader.apply(configuration);
            cache.storeById.put(configuration.getId(), filterStore);
            if (configuration.isEnabled()) {
                filtersByCategory.computeIfAbsent(configuration.getCategory(), k -> new ArrayList<>()).add(filterStore.getFilter());
            }
        }
        for (Map.Entry<Category, List<Filter>> e : filtersByCategory.entrySet()) {
            if (e.getValue().size() > 1) {
                cache.filterByCategory.put(e.getKey(), new FilterList(e.getValue()));
            } else {
                cache.filterByCategory.put(e.getKey(), e.getValue().get(0));
            }
        }

        // make loaded filter stores atomically available to update threads
        this.cache = cache;
    }

    public synchronized void update() {
        for (FilterStoreConfiguration configuration : configurations) {
            FilterStore filterStore = cache.storeById.get(configuration.getId());
            try {
                update(filterStore, configuration);
            } catch (Exception e) {
                log.error("Cannot update current filter configuration: {} ({})", configuration.getId(), configuration.getName(), e);
            }
        }
    }

    private synchronized void save() {
        for (FilterStoreConfiguration configuration : configurations) {
            FilterStore filterStore = cache.storeById.get(configuration.getId());
            try {
                save(filterStore, configuration);
            } catch (Exception e) {
                log.error("Cannot save current filter configuration: {} ({})", configuration.getId(), configuration.getName(), e);
            }
        }
    }

    private void checkAndApplyDefaultConfigurationUpdates() {
        Map<Integer, FilterStoreConfiguration> defaultConfigurationsById = loadDefaultConfigurations();

        for (ListIterator<FilterStoreConfiguration> i = configurations.listIterator(); i.hasNext(); ) {
            FilterStoreConfiguration configuration = i.next();
            if (configuration.isBuiltin()) {
                int id = configuration.getId();
                FilterStoreConfiguration defaultConfiguration = defaultConfigurationsById.remove(id);
                if (defaultConfiguration == null) {
                    log.debug("removing obsolete configuration id: {} key: {} name: {}", id, configuration.getId(), configuration.getName());
                    dataSource.delete(FilterStoreConfiguration.class, id);
                    i.remove();
                    deleteCachedStore(configuration);
                } else if (configuration.getVersion() < defaultConfiguration.getVersion()) {
                    log.debug("updating default configuration");
                    dataSource.save(defaultConfiguration, id);
                    i.set(defaultConfiguration);
                    deleteCachedStore(configuration);
                }
            }
        }

        for (FilterStoreConfiguration configuration : defaultConfigurationsById.values()) {
            log.debug("inserting new default configuration id: {} key: {} name: {}", configuration.getId(), configuration.getId(), configuration.getName());
            dataSource.save(configuration, configuration.getId());
            configurations.add(configuration);
        }
    }

    private void deleteCachedStore(FilterStoreConfiguration configuration) {
        try {
            Files.deleteIfExists(getPath(configuration));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete filter store", e);
        }
    }

    private Map<Integer, FilterStoreConfiguration> loadDefaultConfigurations() {
        try (InputStream in = ResourceHandler.getInputStream(new SimpleResource(defaultFilterStoreConfigurations))) {
            List<FilterStoreConfiguration> configurations = objectMapper.readValue(in, new TypeReference<List<FilterStoreConfiguration>>() {
            });
            return configurations.stream().collect(Collectors.toMap(FilterStoreConfiguration::getId, Function.identity()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FilterStore loadOrCreate(FilterStoreConfiguration configuration) {
        FilterStore filterStore = load(configuration);
        return filterStore != null ? filterStore : createFilterStore(configuration);
    }

    private FilterStore load(FilterStoreConfiguration configuration) {
        Path path = getPath(configuration);
        if (!path.toFile().exists()) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            CryptoService cryptoService = CryptoServiceFactory.getInstance().setKey(systemKey.get()).build();
            return JSONCryptoHandler.decrypt(FilterStore.class, cryptoService, in);
        } catch (CryptoException | IOException e) {
            log.error("failed to load {}", configuration.getId(), e);
            return null;
        }
    }

    private void save(FilterStore filterStore, FilterStoreConfiguration configuration) {
        Path path = getPath(configuration);
        try {
            Path tempFile = Files.createTempFile(tmpDir, "eblocker-", ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                CryptoService cryptoService = CryptoServiceFactory.getInstance().setKey(systemKey.get()).build();
                JSONCryptoHandler.encrypt(filterStore, cryptoService, out);
            }
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.error("Failed to save id: {} name: {}", configuration.getId(), configuration.getName(), e);
        }
    }

    private Path getPath(FilterStoreConfiguration configuration) {
        return cacheDirectory.resolve(configuration.getId() + fileSuffix);
    }

    private FilterStore createFilterStore(FilterStoreConfiguration configuration) {
        FilterDomainContainer container;
        switch (configuration.getLearningMode()) {
            case ASYNCHRONOUS:
                container = new AsynchronousLearningFilter(configuration.isLearnForAllDomains());
                break;
            case SYNCHRONOUS:
                container = new SynchronousLearningFilter(configuration.isLearnForAllDomains());
                break;
            default:
                container = new NotLearningFilter();
        }
        FilterStore filterStore = new FilterStore(container);
        update(filterStore, configuration);
        save(filterStore, configuration);
        return filterStore;
    }

    private void update(FilterStore filterStore, FilterStoreConfiguration configuration) {
        List<EblockerResource> existingResources = filterResources(configuration.getResources());
        if (existingResources.isEmpty()) {
            log.info("No resources available to update filter {}", configuration.getId());
            return;
        }

        if (!updateNecessary(filterStore.getLastUpdate(), existingResources)) {
            log.info("Newer filter definitions are not yet available for filter {}", configuration.getId());
            return;
        }

        try {
            long start = System.currentTimeMillis();
            FilterParser parser = getParser(configuration.getFormat());
            List<Filter> filters = parseFilterDefinitions(parser, existingResources);
            long splitParse = System.currentTimeMillis();
            filters = filterRules(filters, configuration.getRuleFilters());
            long splitFilter = System.currentTimeMillis();
            filterStore.update(filters);
            long stop = System.currentTimeMillis();
            log.debug("updated filter {} in {}ms (parsing: {}ms, filtering: {}, store: {}ms)", configuration.getId(), stop - start, splitParse - start, splitFilter - splitParse, stop - splitFilter);
        } catch (IOException e) {
            log.error("failed to update filter {}", configuration.getId(), e);
        }
    }

    private List<EblockerResource> filterResources(String[] resources) {
        if (resources == null || resources.length == 0) {
            return Collections.emptyList();
        }
        Map<Boolean, List<EblockerResource>> resourceExists = Stream.of(resources)
                .map(SimpleResource::new)
                .collect(Collectors.partitioningBy(ResourceHandler::exists));
        resourceExists.get(false).forEach(resource -> log.error("missing filter resource: {}", resource));
        return resourceExists.get(true);
    }

    private List<Filter> filterRules(List<Filter> filters, String[] ruleFilters) {
        Predicate<Filter> cspPredicate = fad -> fad instanceof UrlFilter && ((UrlFilter) fad).getContentSecurityPolicies() != null;
        List<Predicate<Filter>> predicates = new ArrayList<>();
        for (String rule : ruleFilters) {
            if ("csp".equals(rule)) {
                predicates.add(cspPredicate);
            } else if ("!csp".equals(rule)) {
                predicates.add(fad -> !cspPredicate.test(fad));
            }
        }

        Predicate<Filter> predicate = predicates.stream().reduce(Predicate::and).orElse(t -> true);
        return filters.stream().filter(predicate).collect(Collectors.toList());
    }

    private boolean updateNecessary(Date lastUpdate, List<EblockerResource> resourcesxs) {
        if (lastUpdate == null) {
            return true;
        }
        return resourcesxs.stream().map(ResourceHandler::getDate).filter(lastUpdate::before).findAny().isPresent();
    }

    private FilterParser getParser(FilterDefinitionFormat format) throws IOException {
        switch (format) {
            case EASYLIST:
                return new FilterParser(EasyListLineParser::new);
            case CSV:
                return new FilterParser(CSVLineParser::new);
            case URL:
                return new FilterParser(UrlLineParser::new);
            default:
                throw new IOException("unknown filter format " + format.name());
        }
    }

    private List<Filter> parseFilterDefinitions(FilterParser parser, List<EblockerResource> resources) throws IOException {
        List<Filter> filters = new ArrayList<>();
        for (EblockerResource resource : resources) {
            try (InputStream inputStream = ResourceHandler.getInputStream(resource)) {
                filters.addAll(parser.parse(inputStream));
            }
        }
        return filters;
    }

    public Runnable getFilterStoreUpdater() {
        return () -> {
            if (cache == null) {
                log.info("Loading filter stores from disk...");
                load();
            } else {
                log.info("Saving current filter configuration to disk ...");
                save();
                log.info("... saving done.");
            }
            log.info("Updating current filter configuration from provided filter lists ...");
            update();
            log.info("... updating done.");
        };
    }

    public Runnable getAsynchronousLearnersUpdater() {
        return () -> {
            if (cache == null) {
                return;
            }
            configurations.stream()
                    .filter(configuration -> FilterLearningMode.ASYNCHRONOUS == configuration.getLearningMode())
                    .map(configuration -> ((Runnable) cache.storeById.get(configuration.getId()).getFilter()))
                    .forEach(Runnable::run);
        };
    }

    private static class Cache {
        Map<Integer, FilterStore> storeById = new HashMap<>();
        Map<Category, Filter> filterByCategory = new EnumMap<>(Category.class);
    }
}

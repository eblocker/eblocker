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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.blacklist.DomainBlacklistService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class ParentalControlFilterListsService {
    private static final Logger log = LoggerFactory.getLogger(ParentalControlFilterListsService.class);

    private final Path filePath;
    private final Path customFiltersPath;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final ParentalControlService parentalControlService;
    private final DomainBlacklistService domainBlacklistService;
    private final BlacklistCompiler blacklistCompiler;

    private Instant lastUpdate = Instant.MIN;

    @Inject
    public ParentalControlFilterListsService(
            @Named("parentalcontrol.filterlists.file.path") String filePath,
            @Named("parentalcontrol.filterlists.file.customercreated.path") String customFiltersPath,
            DataSource dataSource,
            FileSystemWatchService fileSystemWatchService,
            ObjectMapper objectMapper,
            ParentalControlService parentalControlService,
            DomainBlacklistService domainBlacklistService,
            BlacklistCompiler blacklistCompiler) throws IOException {
        this.filePath = Paths.get(filePath);
        this.customFiltersPath = Paths.get(customFiltersPath);
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.parentalControlService = parentalControlService;
        this.domainBlacklistService = domainBlacklistService;
        this.blacklistCompiler = blacklistCompiler;

        update();
        fileSystemWatchService.watch(this.filePath, p -> update());
    }

    // Function to read file and return objects
    public Set<ParentalControlFilterSummaryData> getParentalControlFilterLists() {
        return dataSource.getAll(ParentalControlFilterMetaData.class)
                .stream()
                .map(ParentalControlFilterSummaryData::new)
                .collect(Collectors.toSet());
    }

    public ParentalControlFilterSummaryData getFilterById(int id) {
        return getParentalControlFilterLists().stream().filter(data -> data.getId().equals(id)).findAny().orElse(null);
    }

    public List<ParentalControlFilterMetaData> getParentalControlFilterMetaData() {

        return dataSource.getAll(ParentalControlFilterMetaData.class);
    }

    public ParentalControlFilterMetaData getParentalControlFilterMetaData(int id) {
        return dataSource.get(ParentalControlFilterMetaData.class, id);
    }

    public List<String> getFilterListDomains(int filterListId) {
        try {
            return readCustomerCreatedFilter(filterListId);
        } catch (IOException e) {
            log.error("Domainlist could not be read.", e);
            throw new ConflictException("Domainlist could not be read");
        }
    }

    // Function to update a user-defined fiterlist
    public synchronized ParentalControlFilterSummaryData updateFilterList(ParentalControlFilterSummaryData filterlist, String filterType) {
        try {
            ParentalControlFilterMetaData dbFilterList = dataSource
                    .get(ParentalControlFilterMetaData.class, filterlist.getId());
            if (dbFilterList == null) {
                throw new ConflictException("no filter for id: " + filterlist.getId());
            }

            ParentalControlFilterMetaData savedFilter;
            if (dbFilterList.isBuiltin()) {
                ParentalControlFilterMetaData metadata = sanitizeBuiltinFilterUpdate(filterlist, dbFilterList);
                savedFilter = saveFilter(metadata);
            } else {
                validateCustomFilterUpdate(filterlist, dbFilterList, filterType);
                savedFilter = saveCustomFilter(filterlist);
            }

            return new ParentalControlFilterSummaryData(savedFilter);
        } catch (IOException e) {
            log.error("Domainlist could not be saved.", e);
            throw new ConflictException("Domain list could not be saved");
        }
    }

    private void validateCustomFilterUpdate(ParentalControlFilterSummaryData filterlist, ParentalControlFilterMetaData dbFilterList, String filterType) {
        // No way to edit/change a builtin filter list
        if (filterlist.isBuiltin()) {
            throw new ConflictException("Builtin filter lists cannot be edited or changed");
        }
        // Builtin filter list cannot be changed to user created
        if (dbFilterList.isBuiltin()) {
            throw new ConflictException("Builtin flag cannot be removed");
        }
        // Blacklists cannot be converted to Whitelists
        if (!filterlist.getFilterType().equals(dbFilterList.getFilterType())) {
            throw new ConflictException("Filter type cannot be changed");
        }
        // Name must still be unique
        if (!isUniqueCustomerCreatedName(filterlist.getId(), filterlist.getCustomerCreatedName(), filterType)) {
            throw new ConflictException("Name of filter list must be unique");
        }
    }

    private ParentalControlFilterMetaData sanitizeBuiltinFilterUpdate(ParentalControlFilterSummaryData filterlist, ParentalControlFilterMetaData dbFilterList) {
        return new ParentalControlFilterMetaData(
                dbFilterList.getId(),
                dbFilterList.getName(),
                dbFilterList.getDescription(),
                dbFilterList.getCategory(),
                dbFilterList.getFilenames(),
                dbFilterList.getVersion(),
                dbFilterList.getDate(),
                dbFilterList.getFormat(),
                dbFilterList.getFilterType(),
                dbFilterList.isBuiltin(),
                filterlist.isDisabled(),
                dbFilterList.getQueryTransformations(),
                dbFilterList.getCustomerCreatedName(),
                dbFilterList.getCustomerCreatedDescription());
    }

    // Function to delete a filter list
    public synchronized void deleteFilterList(int filterListId) {
        ParentalControlFilterMetaData dbFilterList = dataSource.get(ParentalControlFilterMetaData.class, filterListId);
        if (dbFilterList.isBuiltin()) {
            throw new BadRequestException("Cannot filter list " + filterListId + ", because it is a built-in filter list.");
        }

        if (!isFilterUsed(filterListId)) {
            // No user profile with active domain filtering and given filter list, can be deleted
            // Delete associated file with list of domains
            tryDelete(getCustomFilterFile(filterListId));
            tryDelete(getCustomFileFilterPath(filterListId));
            tryDelete(getCustomBloomFilterPath(filterListId));
            dataSource.delete(ParentalControlFilterMetaData.class, filterListId);
            domainBlacklistService.setFilters(loadMetaData());
        } else {
            throw new BadRequestException("Filter in use by a user profile, cannot be removed");
        }
    }

    // Function to create a new filter list
    public synchronized ParentalControlFilterSummaryData createFilterList(ParentalControlFilterSummaryData newFilterList, String filterType) {
        if (!isUniqueCustomerCreatedName(newFilterList.getId(), newFilterList.getCustomerCreatedName(), filterType)) {
            throw new ConflictException("Name of filter list must be unique");
        }
        if (newFilterList.getId() != null) {
            throw new BadRequestException("Must not provide ID for new filter list");
        }

        // Save domains
        try {
            return new ParentalControlFilterSummaryData(saveCustomFilter(newFilterList));
        } catch (IOException e) {
            log.error("Domainlist could not be saved.", e);
            throw new ConflictException("Domain list could not be saved");
        }
    }

    public boolean isUniqueCustomerCreatedName(Integer id, String name, String filterType) {
        Predicate<ParentalControlFilterMetaData> isCustomerCreatedListWithSameNameButDifferentId = m -> !m.isBuiltin()
                && m.getCustomerCreatedName() != null && m.getCustomerCreatedName().equals(name)
                && !m.getId().equals(id)
                && m.getFilterType().equals(filterType)
                && m.getCategory().equals(Category.PARENTAL_CONTROL);
        return dataSource.getAll(ParentalControlFilterMetaData.class).stream()
                .noneMatch(isCustomerCreatedListWithSameNameButDifferentId);
    }

    private boolean isFilterUsed(int id) {
        return dataSource.getAll(UserProfileModule.class).stream()
                .filter(UserProfileModule::isControlmodeUrls)
                .anyMatch(p -> p.getAccessibleSitesPackages().contains(id) || p.getInaccessibleSitesPackages().contains(id));
    }

    private ParentalControlFilterMetaData saveCustomFilter(ParentalControlFilterSummaryData filterlist) throws IOException {
        // generate id if necessary
        if (filterlist.getId() == null) {
            filterlist.setId(dataSource.nextId(ParentalControlFilterMetaData.class));
        }

        Path fileFilterPath = getCustomFileFilterPath(filterlist.getId());
        Path bloomFilterPath = getCustomBloomFilterPath(filterlist.getId());

        ParentalControlFilterMetaData metaData = new ParentalControlFilterMetaData();
        metaData.setId(filterlist.getId());
        metaData.setFormat("domainblacklist/string");
        metaData.setBuiltin(false);
        metaData.setCategory(filterlist.getCategory() != null ? filterlist.getCategory() : Category.PARENTAL_CONTROL);
        metaData.setCustomerCreatedName(filterlist.getCustomerCreatedName());
        metaData.setCustomerCreatedDescription(filterlist.getCustomerCreatedDescription());
        metaData.setFilterType(filterlist.getFilterType());
        metaData.setFilenames(Arrays.asList(fileFilterPath.toString(), bloomFilterPath.toString()));
        metaData.setDate(filterlist.getLastUpdate());
        metaData.setDisabled(filterlist.isDisabled());

        // save domain list and compile filter if changed
        if (filterlist.getDomains() != null) {
            saveCustomerCreatedFilter(filterlist.getId(), filterlist.getDomains());
            blacklistCompiler.compile(filterlist.getId(), filterlist.getCustomerCreatedName(),
                    filterlist.getDomains()
                            .stream()
                            .map(this::prependDot)
                            .distinct()
                            .collect(Collectors.toList()),
                    fileFilterPath.toString(), bloomFilterPath.toString());
            metaData.setDate(new Date());
        } else if (filterlist.getDomainsStreamSupplier() != null) {
            blacklistCompiler.compile(filterlist.getId(),
                    filterlist.getCustomerCreatedName(),
                    () -> filterlist.getDomainsStreamSupplier()
                            .get()
                            .map(this::prependDot),
                    fileFilterPath.toString(),
                    bloomFilterPath.toString());
            metaData.setDate(new Date());
        }

        return saveFilter(metaData);
    }

    private ParentalControlFilterMetaData saveFilter(ParentalControlFilterMetaData metadata) {
        ParentalControlFilterMetaData savedMetadata = dataSource.save(metadata, metadata.getId());
        domainBlacklistService.setFilters(loadMetaData());
        return savedMetadata;
    }

    private String prependDot(String value) {
        return value.startsWith(".") ? value : "." + value;
    }

    private void update() {
        try {
            Instant fileInstant = Files.getLastModifiedTime(filePath).toInstant();
            if (lastUpdate.isBefore(fileInstant)) {

                // import new metadata
                Set<ParentalControlFilterMetaData> builtinMetaData = readMetaDataFile();
                replaceBuiltinMetaData(builtinMetaData);

                // update all filters and profiles to use new metadata
                Collection<ParentalControlFilterMetaData> metaData = loadMetaData();
                updateFilters(metaData);
                updateProfiles(metaData);

                lastUpdate = fileInstant;
            } else {
                log.debug("parental control filter lists are up-to-date");
            }
        } catch (IOException e) {
            log.error("Failed to update metadata", e);
        }
    }

    private Collection<ParentalControlFilterMetaData> loadMetaData() {
        return dataSource.getAll(ParentalControlFilterMetaData.class);
    }

    /**
     * Replaces all builtin filter metadata with new ones.
     */
    private void replaceBuiltinMetaData(Set<ParentalControlFilterMetaData> metaData) {
        metaData.stream().forEach(this::upgradeMetaData);

        // remove all built-in metadata which won't be updated
        Set<Integer> metaDataIds = metaData.stream().map(ParentalControlFilterMetaData::getId).collect(Collectors.toSet());
        Collection<ParentalControlFilterMetaData> currentMetaData = loadMetaData();

        preserveDisabledState(currentMetaData, metaData);

        currentMetaData.stream()
                .filter(ParentalControlFilterMetaData::isBuiltin)
                .map(ParentalControlFilterMetaData::getId)
                .filter(id -> !metaDataIds.contains(id))
                .forEach(id -> dataSource.delete(ParentalControlFilterMetaData.class, id));

        // save new metadata
        metaData.forEach(m -> dataSource.save(m, m.getId()));
    }

    private void preserveDisabledState(Collection<ParentalControlFilterMetaData> currentMetaData, Set<ParentalControlFilterMetaData> loadedMetaData) {
        currentMetaData.forEach(currentFilter -> {
            loadedMetaData.stream()
                    .filter(md -> md.getId().equals(currentFilter.getId()))
                    .findFirst()
                    .ifPresent(md -> md.setDisabled(currentFilter.isDisabled()));
        });
    }

    private Set<ParentalControlFilterMetaData> readMetaDataFile() throws IOException {
        return objectMapper.readValue(filePath.toFile(), new TypeReference<Set<ParentalControlFilterMetaData>>() {
        });
    }

    private void updateFilters(Collection<ParentalControlFilterMetaData> metaData) {
        domainBlacklistService.setFilters(metaData);
    }

    private void updateProfiles(Collection<ParentalControlFilterMetaData> metaData) {
        parentalControlService.updateFilters(metaData);
    }

    private List<String> readCustomerCreatedFilter(int id) throws IOException {
        return objectMapper.readValue(getCustomFilterFile(id).toFile(), new TypeReference<List<String>>() {
        });
    }

    private void saveCustomerCreatedFilter(int id, List<String> filter) throws IOException {
        Path path = getCustomFilterFile(id);
        objectMapper.writeValue(path.toFile(), filter);
    }

    private Path getCustomFilterFile(int id) {
        return customFiltersPath.resolve(Integer.toString(id));
    }

    private Path getCustomFileFilterPath(int id) {
        return customFiltersPath.resolve(id + ".filter");
    }

    private Path getCustomBloomFilterPath(int id) {
        return customFiltersPath.resolve(id + ".bloom");
    }

    /**
     * Fills in builtin-flag and date for metadata generated with older eblocker-lists version.
     */
    private void upgradeMetaData(ParentalControlFilterMetaData metaData) {
        // ensure builtin-flag is set
        metaData.setBuiltin(true);

        // create date based on version if no date is available
        if (metaData.getDate() == null) {
            try {
                Instant instant = LocalDate.parse(metaData.getVersion(), DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay().toInstant(ZoneOffset.UTC);
                metaData.setDate(Date.from(instant));
            } catch (Exception e) {
                log.error("expected version to be set and in correct format for old metadata, metadata may be corrupt.",
                        e);
                metaData.setDate(new Date());
            }
        }
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("failed to delete: {}", path);
        }
    }
}

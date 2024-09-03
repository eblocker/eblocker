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
package org.eblocker.server.common.blacklist;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.QueryTransformation;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.util.FilterModeUtils;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.DeviceService.DeviceChangeListener;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, initPriority = 10)
public class DomainBlockingService {
    private static final Logger logger = LoggerFactory.getLogger(DomainBlockingService.class);

    private static final String ATTRIBUTE_ADD_PROFILE_ID = "ADD_PROFILE_ID";
    private static final String ATTRIBUTE_TARGET = "TARGET";

    @Nonnull
    private final String accessDeniedIp;
    private final boolean redirectDespiteDisabledSSL;
    @Nonnull
    private final DeviceService deviceService;
    @Nonnull
    private final DomainBlacklistService domainBlacklistService;
    @Nonnull
    private final EblockerDnsServer eblockerDnsServer;
    @Nonnull
    private final ParentalControlService parentalControlService;
    @Nonnull
    private final ParentalControlFilterListsService filterListsService;
    @Nonnull
    private final ProductInfoService productInfoService;
    @Nonnull
    private final SquidConfigController squidConfigController;
    @Nonnull
    private final SslService sslService;
    @Nonnull
    private final UserService userService;

    @Nonnull
    private final Cache<FilterConfig, DomainFilter<String>> filtersByConfig;
    @Nonnull
    private final Cache<String, Filter> filterByDeviceId;
    private boolean sslServiceInitialized;

    @Inject
    public DomainBlockingService(@Named("parentalControl.redirect.ip") @Nonnull String accessDeniedIp,
                                 @Named("parentalControl.redirect.despite_disabled_ssl") boolean redirectDespiteDisabledSSL,
                                 @Nonnull DeviceService deviceService,
                                 @Nonnull DomainBlacklistService domainBlacklistService,
                                 @Nonnull EblockerDnsServer eblockerDnsServer,
                                 @Nonnull ParentalControlService parentalControlService,
                                 @Nonnull ParentalControlFilterListsService filterListsService,
                                 @Nonnull ProductInfoService productInfoService,
                                 @Nonnull SquidConfigController squidConfigController,
                                 @Nonnull SslService sslService,
                                 @Nonnull UserService userService) {
        this.accessDeniedIp = accessDeniedIp;
        this.redirectDespiteDisabledSSL = redirectDespiteDisabledSSL;

        this.deviceService = deviceService;
        this.domainBlacklistService = domainBlacklistService;
        this.eblockerDnsServer = eblockerDnsServer;
        this.parentalControlService = parentalControlService;
        this.filterListsService = filterListsService;
        this.productInfoService = productInfoService;
        this.squidConfigController = squidConfigController;
        this.sslService = sslService;
        this.userService = userService;

        filtersByConfig = CacheBuilder.newBuilder().weakValues().concurrencyLevel(4).build();
        filterByDeviceId = CacheBuilder.newBuilder().concurrencyLevel(4).build();
    }

    @SubSystemInit
    public void init() {
        deviceService.addListener(new DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                updateFilteredDevices();
                checkCachedFilter(device);
            }

            @Override
            public void onDelete(Device device) {
                updateFilteredDevices();
                filterByDeviceId.invalidate(device.getId());
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });

        domainBlacklistService.addListener(() -> {
            logger.info("dropping all filters due to filter changes");
            filtersByConfig.invalidateAll();
            filterByDeviceId.invalidateAll();
        });

        parentalControlService.addListener(o -> {
            logger.info("dropping all filters due to profile changes");
            checkCachedFilters();
            updateFilteredDevices();
        });

        userService.addListener(o -> {
            logger.info("checking all filters due to user changes");
            checkCachedFilters();
            updateFilteredDevices();
        });

        eblockerDnsServer.addListener(b -> {
            logger.info("dropping all filters due to dns being enabled or disabled");
            filtersByConfig.invalidateAll();
            filterByDeviceId.invalidateAll();
            updateFilteredDevices();
        });

        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                logger.info("checking all filters due ssl initialization finished");
                sslServiceInitialized = true;
                onChange();
            }

            @Override
            public void onEnable() {
                logger.info("checking all filters due ssl being enabled");
                onChange();
            }

            @Override
            public void onDisable() {
                logger.info("checking all filters due ssl being disabled");
                onChange();
            }

            private void onChange() {
                checkCachedFilters();
                updateFilteredDevices();
            }
        });

        updateFilteredDevices();
    }

    @Nonnull
    public Decision isBlocked(@Nonnull Device device, String domain) {
        try {
            DomainFilter<String> filter = filterByDeviceId.get(device.getId(), () -> createDeviceFilter(device));
            logger.debug("using filter: {}", filter.getName());
            return (Decision) filter.isBlocked(domain);
        } catch (ExecutionException e) {
            logger.error("failed to create filter: ", e);
            return new Decision(false, domain, null, null, device.getOperatingUser(), null);
        }
    }

    /**
     * Decide whether this domain is blocked by the device-independent malware/ads/tracker filters
     * <p>
     * Do not use this method for actual blocking as it ignores device-specific settings!
     */
    @Nonnull
    public Decision isDomainBlockedByMalwareAdsTrackersFilters(String domain) {
        try {
            DomainFilter<String> filter = createDomainBlockedByMalwareAdsTrackersFilter();
            logger.debug("using filter: {}", filter.getName());
            return (Decision) filter.isBlocked(domain);
        } catch (ExecutionException e) {
            logger.error("failed to create filter: ", e);
            return new Decision(false, domain, null, null, 0, null);
        }
    }

    @Nonnull
    private Filter createDeviceFilter(Device device) throws ExecutionException {
        DeviceConfig deviceConfig = new DeviceConfig(device);
        FilterConfig filterConfig = new FilterConfig(deviceConfig, isSslEnabledGlobally());
        DomainFilter<String> filter = filtersByConfig.get(filterConfig, () -> createFilter(filterConfig));
        return new Filter(filter, filterConfig, deviceConfig.getUser().getId(), deviceConfig.getProfile().getId());
    }

    @Nonnull
    private Filter createDomainBlockedByMalwareAdsTrackersFilter() throws ExecutionException {
        FilterConfig filterConfig = new FilterConfig();

        DomainFilter<String> filter = filtersByConfig.get(filterConfig, () -> createFilter(filterConfig));
        return new Filter(filter, filterConfig, -1, -1);
    }

    @Nonnull
    private DomainFilter<String> createFilter(@Nonnull FilterConfig filterConfig) {
        Map<Integer, ParentalControlFilterMetaData> metadataById = getEnabledFilterMetadataById();
        DomainFilter<String> malwareFilter = createMalwareFilter(filterConfig, metadataById.values());
        DomainFilter<String> parentalControlFilter = createParentalControlFilter(filterConfig, metadataById);
        DomainFilter<String> adsTrackersFilter = createAdsTrackersFilter(filterConfig, metadataById);
        DomainFilter<String> orFilter = Filters.or(malwareFilter, parentalControlFilter, adsTrackersFilter);
        return Filters.cache(8192, CachingFilter.CacheMode.ALL, orFilter);
    }

    @Nonnull
    private Map<Integer, ParentalControlFilterMetaData> getEnabledFilterMetadataById() {
        return filterListsService
                .getParentalControlFilterMetaData()
                .stream()
                .filter(m -> !m.isDisabled())
                .collect(Collectors.toMap(ParentalControlFilterMetaData::getId, Function.identity()));
    }

    @Nonnull
    private DomainFilter<String> createMalwareFilter(@Nonnull FilterConfig config, @Nonnull Collection<ParentalControlFilterMetaData> metaData) {
        if (!productInfoService.hasFeature(ProductFeature.PRO)) {
            return Filters.staticFalse();
        }

        if (!config.isFilterMalware()) {
            return Filters.staticFalse();
        }

        Set<Integer> ids = filterByCategory(metaData, EnumSet.of(Category.MALWARE));
        DomainFilter<String> filter = Filters.hostname(createFilter(ids));
        return attributeFilter(filter, Collections.singletonMap(ATTRIBUTE_TARGET, accessDeniedIp));
    }

    @Nonnull
    private DomainFilter<String> createParentalControlFilter(@Nonnull FilterConfig config, @Nonnull Map<Integer, ParentalControlFilterMetaData> metaDataById) {
        if (!productInfoService.hasFeature(ProductFeature.FAM)) {
            return Filters.staticFalse();
        }

        if (!config.isParentalControlUrlControlModeEnabled()) {
            return Filters.staticFalse();
        }

        BloomFilter<String> topLevelBloomDomainFilter = getTopLevelBloomFilter(metaDataById.values(), Category.PARENTAL_CONTROL_BLOOM_FILTER);
        DomainFilter<String> permittedFilter = createHostnameFilters(config.getParentalControlPermittedIds(), metaDataById, topLevelBloomDomainFilter);
        DomainFilter<String> prohibitedFilter = createHostnameFilters(config.getParentalControlProhibitedIds(), metaDataById, topLevelBloomDomainFilter);

        DomainFilter<String> parentalFilter = config.isParentalControlDefaultDeny()
                ? Filters.or(Filters.not(permittedFilter), prohibitedFilter)
                : Filters.and(Filters.not(permittedFilter), prohibitedFilter);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_ADD_PROFILE_ID, Boolean.TRUE);
        attributes.put(ATTRIBUTE_TARGET, accessDeniedIp);
        return attributeFilter(parentalFilter, attributes);
    }

    @Nullable
    private BloomFilter<String> getTopLevelBloomFilter(@Nonnull Collection<ParentalControlFilterMetaData> metaData, @Nonnull Category category) {
        Set<Integer> ids = filterByCategory(metaData, EnumSet.of(category));

        if (ids.isEmpty()) {
            logger.warn("no top-level bloom domain filter available - this may decrease performance");
            return null;
        }

        if (ids.size() != 1) {
            logger.warn("more than one top-level bloom filter available, will not use any - this may decrease performance");
            return null;
        }

        DomainFilter<?> filter = domainBlacklistService.getFilter(ids.iterator().next());
        if (filter == null) {
            logger.error("filter has not been loaded - this may decrease performance");
            return null;
        }

        if (!(filter instanceof BloomDomainFilter)) {
            logger.error("expected a bloom filter but got: {}", filter.getClass().getName());
            return null;
        }

        @SuppressWarnings("unchecked")
        BloomDomainFilter<String> bloomFilter = (BloomDomainFilter<String>) filter;
        return bloomFilter.getBloomFilter();
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private DomainFilter<String> createHostnameFilters(@Nonnull Set<Integer> ids, @Nonnull Map<Integer, ParentalControlFilterMetaData> metaDataById, @Nullable BloomFilter<String> topLevelBloomFilter) {
        List<DomainFilter<String>> filters = new ArrayList<>();
        Map<QueryTransformation, Set<Integer>> queryTransformationsById = getIdsByQueryTransformations(ids, metaDataById);
        for (Map.Entry<QueryTransformation, Set<Integer>> e : queryTransformationsById.entrySet()) {
            DomainFilter<String> filter = combineFilters(e.getValue(), metaDataById, topLevelBloomFilter);
            if (e.getKey() != null) {
                filter = Filters.replace(e.getKey().getRegex(), e.getKey().getReplacement(), filter);
            }
            filters.add(filter);
        }
        return Filters.hostname(Filters.or(filters.toArray(new DomainFilter[0])));
    }

    @Nonnull
    private DomainFilter<String> combineFilters(@Nonnull Set<Integer> ids, @Nonnull Map<Integer, ParentalControlFilterMetaData> metaDataById, @Nullable BloomFilter<String> topLevelBloomFilter) {
        Map<String, List<Integer>> metaDataByFormat = ids.stream()
                .map(metaDataById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ParentalControlFilterMetaData::getFormat,
                        Collectors.mapping(
                                ParentalControlFilterMetaData::getId,
                                Collectors.toList())));

        List<Integer> fileBasedFilterIds = metaDataByFormat.getOrDefault("domainblacklist/string", Collections.emptyList());
        Map<Boolean, List<Integer>> fileBasedFilterIdsByBuiltin = fileBasedFilterIds.stream()
                .collect(Collectors.groupingBy(id -> metaDataById.get(id).isBuiltin()));

        DomainFilter<String> fileBasedFiltersBuiltIn = createFilter(fileBasedFilterIdsByBuiltin.getOrDefault(true, Collections.emptyList()));
        DomainFilter<String> fileBasedFiltersNotBuiltIn = createFilter(fileBasedFilterIdsByBuiltin.getOrDefault(false, Collections.emptyList()));

        DomainFilter<byte[]> md5BasedFilters = createFilter(metaDataByFormat.getOrDefault("domainblacklist/hash-md5", Collections.emptyList()));
        DomainFilter<byte[]> sha1BasedFilters = createFilter(metaDataByFormat.getOrDefault("domainblacklist/hash-sha1", Collections.emptyList()));

        return Filters.or(
                wrapBloomFilter(topLevelBloomFilter, fileBasedFiltersBuiltIn),
                fileBasedFiltersNotBuiltIn,
                Filters.hashing(Hashing.md5(), md5BasedFilters),
                Filters.hashing(Hashing.sha1(), sha1BasedFilters));
    }

    @Nonnull
    private Map<QueryTransformation, Set<Integer>> getIdsByQueryTransformations(@Nonnull Set<Integer> ids, @Nonnull Map<Integer, ParentalControlFilterMetaData> metaDataById) {
        Map<QueryTransformation, Set<Integer>> transformationsById = new HashMap<>();
        ids.stream().map(metaDataById::get).filter(Objects::nonNull).forEach(
                metadata -> {
                    List<QueryTransformation> transformations = metadata.getQueryTransformations();
                    if (transformations == null) {
                        Set<Integer> associatedIds = transformationsById.computeIfAbsent(null, k -> new HashSet<>());
                        associatedIds.add(metadata.getId());
                    } else {
                        for (QueryTransformation transformation : transformations) {
                            Set<Integer> associatedIds = transformationsById.computeIfAbsent(transformation, k -> new HashSet<>());
                            associatedIds.add(metadata.getId());
                        }
                    }
                }
        );
        return transformationsById;
    }

    @Nonnull
    private DomainFilter<String> createAdsTrackersFilter(@Nonnull FilterConfig config, @Nonnull Map<Integer, ParentalControlFilterMetaData> metadataById) {
        if (!productInfoService.hasFeature(ProductFeature.PRO)) {
            return Filters.staticFalse();
        }

        EnumSet<Category> categories = EnumSet.noneOf(Category.class);
        if (eblockerDnsServer.isEnabled() && config.getFilterMode() == FilterMode.PLUG_AND_PLAY) {
            if (config.isFilterAds()) {
                categories.add(Category.ADS);
            }

            if (config.isFilterTrackers()) {
                categories.add(Category.TRACKERS);
            }
        }

        BloomFilter<String> topLevelBloomFilter = getTopLevelBloomFilter(metadataById.values(), Category.ADS_TRACKERS_BLOOM_FILTER);
        DomainFilter<String> blacklistFilter = createHostnameFilters(toSet(config.getCustomBlacklistId()), metadataById, null);
        DomainFilter<String> whitelistFilter = createHostnameFilters(toSet(config.getCustomWhitelistId()), metadataById, null);
        DomainFilter<String> adsTrackerFilter = createHostnameFilters(filterByCategory(metadataById.values(), categories), metadataById, topLevelBloomFilter);
        DomainFilter<String> filter = Filters.and(Filters.not(whitelistFilter), Filters.or(blacklistFilter, adsTrackerFilter));

        String target = redirectDespiteDisabledSSL || config.isSslEnabled() ? accessDeniedIp : null;
        return attributeFilter(filter, Collections.singletonMap(ATTRIBUTE_TARGET, target));
    }

    @Nonnull
    private Set<Integer> toSet(@Nullable Integer id) {
        if (id == null) {
            return Collections.emptySet();
        }

        return Collections.singleton(id);
    }

    @Nonnull
    private DomainFilter<String> wrapBloomFilter(@Nullable BloomFilter<String> topLevelBloomFilter, @Nonnull DomainFilter<String> filter) {
        if (filter instanceof BloomDomainFilter && filter.getChildFilters().get(0) instanceof SingleFileFilter) {
            return filter;
        }
        return topLevelBloomFilter != null ? Filters.bloom(topLevelBloomFilter, filter) : filter;
    }

    @Nonnull
    private List<DomainFilter> getFilters(@Nonnull Collection<Integer> ids) {
        return ids.stream().map(domainBlacklistService::getFilter).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private <T> DomainFilter<T> createFilter(@Nonnull Collection<Integer> ids) {
        DomainFilter<T>[] filters = getFilters(ids).toArray(new DomainFilter[0]);
        return Filters.or(filters);
    }

    @Nonnull
    private Set<Integer> filterByCategory(@Nonnull Collection<ParentalControlFilterMetaData> metaData, @Nonnull EnumSet<Category> categories) {
        return metaData.stream()
                .filter(m -> categories.contains(m.getCategory()))
                .map(ParentalControlFilterMetaData::getId)
                .collect(Collectors.toSet());
    }

    private synchronized void updateFilteredDevices() {
        logger.debug("updating dns / squid's list of filtered devices");

        Map<Device, FilterConfig> configByEnabledDevice = deviceService.getDevices(false).stream()
                .filter(Device::isEnabled)
                .collect(Collectors.toMap(Function.identity(), device -> new FilterConfig(new DeviceConfig(device), isSslEnabledGlobally())));

        Set<Device> domainFilteredDevices = filterDomainFilteredDevices(configByEnabledDevice);
        Set<Device> parentalControlledDevices = filterParentalControlFilteredDevices(configByEnabledDevice);

        if (eblockerDnsServer.isEnabled()) {
            eblockerDnsServer.setFilteredPeers(ipAddresses(parentalControlledDevices), ipAddresses(domainFilteredDevices));
            squidConfigController.updateDomainFilteredDevices(Collections.emptySet());
        } else {
            eblockerDnsServer.setFilteredPeers(Collections.emptySet(), Collections.emptySet());
            squidConfigController.updateDomainFilteredDevices(Sets.union(domainFilteredDevices, parentalControlledDevices));
        }
    }

    @Nonnull
    private Set<Device> filterParentalControlFilteredDevices(@Nonnull Map<Device, FilterConfig> configByEnabledDevice) {
        if (!productInfoService.hasFeature(ProductFeature.FAM)) {
            return Collections.emptySet();
        }

        return configByEnabledDevice.entrySet().stream()
                .filter(e -> e.getValue().parentalControlUrlControlModeEnabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Nonnull
    private Set<Device> filterDomainFilteredDevices(@Nonnull Map<Device, FilterConfig> configByEnabledDevice) {
        if (!productInfoService.hasFeature(ProductFeature.PRO)) {
            return Collections.emptySet();
        }

        boolean hasFamilyFeature = productInfoService.hasFeature(ProductFeature.FAM);
        return configByEnabledDevice.entrySet().stream()
                .filter(e -> !e.getValue().parentalControlUrlControlModeEnabled || !hasFamilyFeature)
                .filter(e -> e.getValue().getCustomBlacklistId() != null
                        || e.getValue().getCustomWhitelistId() != null
                        || e.getValue().isDomainRecordingEnabled()
                        || e.getValue().getFilterMode() == FilterMode.PLUG_AND_PLAY && (e.getValue().isFilterAds() || e.getValue().isFilterTrackers()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Nonnull
    private Set<IpAddress> ipAddresses(@Nonnull Collection<Device> device) {
        return device.stream().flatMap(d -> d.getIpAddresses().stream()).collect(Collectors.toSet());
    }

    private synchronized void checkCachedFilter(@Nonnull Device device) {
        logger.debug("checking cached filter for {}", device.getId());

        Filter filter = filterByDeviceId.getIfPresent(device.getId());
        if (filter == null) {
            logger.debug("no cached filter for device {}", device.getId());
            return;
        }

        FilterConfig currentConfig = new FilterConfig(new DeviceConfig(device), isSslEnabledGlobally());
        if (currentConfig.equals(filter.getConfig())) {
            logger.debug("configuration unchanged");
            return;
        }

        logger.debug("configuration changed, dropping filter");
        filterByDeviceId.invalidate(device.getId());
    }

    private void checkCachedFilters() {
        filterByDeviceId.asMap().keySet().stream().map(deviceService::getDeviceById).forEach(this::checkCachedFilter);
    }

    private Stream<CachingFilter> getCachingFilters() {
        return getFilters().stream().filter(f -> f instanceof CachingFilter).map(f -> (CachingFilter) f);
    }

    @Nonnull
    private Set<DomainFilter<?>> getFilters() {
        Queue<Filter> filtersToVisit = new LinkedList<>(filterByDeviceId.asMap().values());

        Set<DomainFilter<?>> filters = new HashSet<>();
        Filter current;
        while ((current = filtersToVisit.poll()) != null) {
            if (!filters.contains(current)) {
                filters.add(current);
                filters.addAll(current.getChildFilters());
            }
        }
        return filters;
    }

    private boolean isSslEnabledGlobally() {
        if (sslServiceInitialized) {
            return sslService.isSslEnabled();
        }
        return false;
    }

    @Nonnull
    private <T> DomainFilter<T> attributeFilter(@Nonnull DomainFilter<T> filter, Map<String, Object> attributes) {
        if (filter instanceof StaticFilter) {
            return filter;
        }
        return new AttributeFilter<>(filter, attributes);
    }

    public static class Decision extends FilterDecision<String> {
        private final boolean blocked;
        private final String domain;
        private final Integer profileId;
        private final Integer listId;
        private final int userId;
        private final String target;

        public Decision(boolean blocked, String domain, Integer profileId, @Nullable Integer listId, int userId, String target) {
            super(domain, blocked, null);
            this.blocked = blocked;
            this.domain = domain;
            this.profileId = profileId;
            this.listId = listId;
            this.userId = userId;
            this.target = target;
        }

        @Override
        public boolean isBlocked() {
            return blocked;
        }

        @Override
        public String getDomain() {
            return domain;
        }

        public Integer getProfileId() {
            return profileId;
        }

        @Nullable
        public Integer getListId() {
            return listId;
        }

        public int getUserId() {
            return userId;
        }

        public String getTarget() {
            return target;
        }
    }

    private static class AttributeDecision<T> extends FilterDecision<T> {
        private final Map<String, Object> attributes;

        AttributeDecision(T domain, boolean blocked, @Nullable DomainFilter<?> filter, Map<String, Object> attributes) {
            super(domain, blocked, filter);
            this.attributes = attributes;
        }

        Map<String, Object> getAttributes() {
            return attributes;
        }
    }

    private static class AttributeFilter<T> implements DomainFilter<T> {

        @Nonnull
        private final DomainFilter<T> filter;
        @Nonnull
        private final Map<String, Object> attributes;

        private AttributeFilter(@Nonnull DomainFilter<T> filter, @Nonnull Map<String, Object> attributes) {
            this.filter = filter;
            this.attributes = attributes;
        }

        @Nullable
        @Override
        public Integer getListId() {
            return filter.getListId();
        }

        @Nonnull
        @Override
        public String getName() {
            return "(attribute-filter " + filter.getName() + ")";
        }

        @Override
        public int getSize() {
            return filter.getSize();
        }

        @Nonnull
        @Override
        public Stream<T> getDomains() {
            return filter.getDomains();
        }

        @Nonnull
        @Override
        public FilterDecision<T> isBlocked(T domain) {
            FilterDecision<T> decision = filter.isBlocked(domain);
            Map<String, Object> decisionAttributes = new HashMap<>();
            if (decision instanceof AttributeDecision) {
                decisionAttributes.putAll(((AttributeDecision<T>) decision).getAttributes());
            }
            decisionAttributes.putAll(attributes);
            return new AttributeDecision<>(decision.getDomain(), decision.isBlocked(), decision.getFilter(), decisionAttributes);
        }

        @Nonnull
        @Override
        public List<DomainFilter<?>> getChildFilters() {
            return filter.getChildFilters();
        }
    }

    private static class Filter implements DomainFilter<String> {
        private final DomainFilter<String> delegate;
        private final FilterConfig config;
        private final int userId;
        private final Integer profileId;

        Filter(@Nonnull DomainFilter<String> delegate, FilterConfig config, int userId, Integer profileId) {
            this.delegate = delegate;
            this.config = config;
            this.userId = userId;
            this.profileId = profileId;
        }

        @Nullable
        @Override
        public Integer getListId() {
            return null;
        }

        @Nonnull
        @Override
        public String getName() {
            return "(domain-blocking-filter-" + userId + "-" + profileId + " " + delegate.getName() + ")";
        }

        @Override
        public int getSize() {
            return delegate.getSize();
        }

        @Nonnull
        @Override
        public Stream<String> getDomains() {
            return delegate.getDomains();
        }

        @Nonnull
        @Override
        public Decision isBlocked(String domain) {
            FilterDecision<String> delegateDecision = delegate.isBlocked(domain);
            Map<String, Object> attributes = delegateDecision instanceof AttributeDecision ? ((AttributeDecision<String>) delegateDecision).getAttributes() : Collections.emptyMap();
            DomainFilter<?> filter = delegateDecision.getFilter();
            Integer listId = null;
            if (filter != null) {
                listId = filter.getListId();
            }
            return new Decision(
                    delegateDecision.isBlocked(),
                    delegateDecision.getDomain(),
                    attributes.containsKey(ATTRIBUTE_ADD_PROFILE_ID) ? profileId : null,
                    listId,
                    userId,
                    (String) attributes.get(ATTRIBUTE_TARGET));
        }

        @Nonnull
        @Override
        public List<DomainFilter<?>> getChildFilters() {
            return Collections.singletonList(delegate);
        }

        FilterConfig getConfig() {
            return config;
        }
    }

    private class DeviceConfig {
        @Nonnull
        private final Device device;
        @Nonnull
        private final UserModule user;
        private final UserProfileModule profile;

        DeviceConfig(@Nonnull Device device) {
            this.device = device;
            user = userService.getUserById(device.getOperatingUser());
            profile = parentalControlService.getProfile(user.getAssociatedProfileId());
        }

        @Nonnull
        Device getDevice() {
            return device;
        }

        @Nonnull
        UserModule getUser() {
            return user;
        }

        UserProfileModule getProfile() {
            return profile;
        }
    }

    private static class FilterConfig {
        private final FilterMode filterMode;
        private final boolean sslEnabled;

        private final boolean filterAds;
        private final boolean filterTrackers;
        private final boolean filterMalware;
        private final boolean domainRecordingEnabled;
        private final Integer customBlacklistId;
        private final Integer customWhitelistId;

        private final boolean parentalControlUrlControlModeEnabled;
        private final boolean parentalControlDefaultDeny;
        private final Set<Integer> parentalControlPermittedIds;
        private final Set<Integer> parentalControlProhibitedIds;

        FilterConfig(DeviceConfig deviceConfig, boolean sslEnabledGlobally) {
            Device device = deviceConfig.getDevice();
            filterMode = FilterModeUtils.getEffectiveFilterMode(sslEnabledGlobally, device);
            sslEnabled = device.isSslEnabled();
            filterAds = device.isFilterAdsEnabled();
            filterTrackers = device.isFilterTrackersEnabled();
            filterMalware = device.isMalwareFilterEnabled();
            domainRecordingEnabled = device.isDomainRecordingEnabled();

            UserModule user = deviceConfig.getUser();
            customBlacklistId = user.getCustomBlacklistId();
            customWhitelistId = user.getCustomWhitelistId();

            UserProfileModule profile = deviceConfig.getProfile();
            parentalControlUrlControlModeEnabled = profile.isControlmodeUrls();
            parentalControlDefaultDeny = UserProfileModule.InternetAccessRestrictionMode.WHITELIST == profile.getInternetAccessRestrictionMode();
            parentalControlPermittedIds = profile.getAccessibleSitesPackages();
            parentalControlProhibitedIds = profile.getInaccessibleSitesPackages();
        }

        FilterConfig() {
            filterMode = FilterMode.PLUG_AND_PLAY;
            sslEnabled = true;
            filterAds = true;
            filterTrackers = true;
            filterMalware = true;

            customBlacklistId = null;
            customWhitelistId = null;

            parentalControlUrlControlModeEnabled = false;
            parentalControlDefaultDeny = false;
            parentalControlPermittedIds = Collections.emptySet();
            parentalControlProhibitedIds = Collections.emptySet();
            domainRecordingEnabled = false;
        }

        FilterMode getFilterMode() {
            return filterMode;
        }

        boolean isSslEnabled() {
            return sslEnabled;
        }

        boolean isFilterAds() {
            return filterAds;
        }

        boolean isFilterTrackers() {
            return filterTrackers;
        }

        boolean isFilterMalware() {
            return filterMalware;
        }

        Integer getCustomBlacklistId() {
            return customBlacklistId;
        }

        Integer getCustomWhitelistId() {
            return customWhitelistId;
        }

        boolean isDomainRecordingEnabled() {
            return domainRecordingEnabled;
        }

        boolean isParentalControlUrlControlModeEnabled() {
            return parentalControlUrlControlModeEnabled;
        }

        boolean isParentalControlDefaultDeny() {
            return parentalControlDefaultDeny;
        }

        Set<Integer> getParentalControlPermittedIds() {
            return parentalControlPermittedIds;
        }

        Set<Integer> getParentalControlProhibitedIds() {
            return parentalControlProhibitedIds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            FilterConfig that = (FilterConfig) o;
            return sslEnabled == that.sslEnabled &&
                    filterAds == that.filterAds &&
                    filterTrackers == that.filterTrackers &&
                    filterMalware == that.filterMalware &&
                    parentalControlUrlControlModeEnabled == that.parentalControlUrlControlModeEnabled &&
                    parentalControlDefaultDeny == that.parentalControlDefaultDeny &&
                    filterMode == that.filterMode &&
                    Objects.equals(customBlacklistId, that.customBlacklistId) &&
                    Objects.equals(customWhitelistId, that.customWhitelistId) &&
                    Objects.equals(parentalControlPermittedIds, that.parentalControlPermittedIds) &&
                    Objects.equals(parentalControlProhibitedIds, that.parentalControlProhibitedIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filterMode, sslEnabled, filterAds, filterTrackers, customBlacklistId, customWhitelistId,
                    parentalControlUrlControlModeEnabled, parentalControlDefaultDeny, parentalControlPermittedIds,
                    parentalControlProhibitedIds);
        }
    }
}

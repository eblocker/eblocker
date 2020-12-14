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

    private final String accessDeniedIp;
    private final boolean redirectDespiteDisabledSSL;

    private final DeviceService deviceService;
    private final DomainBlacklistService domainBlacklistService;
    private final EblockerDnsServer eblockerDnsServer;
    private final ParentalControlService parentalControlService;
    private final ParentalControlFilterListsService filterListsService;
    private final ProductInfoService productInfoService;
    private final SquidConfigController squidConfigController;
    private final SslService sslService;
    private final UserService userService;

    private final Cache<FilterConfig, DomainFilter<String>> filtersByConfig;
    private final Cache<String, Filter> filterByDeviceId;
    private boolean sslServiceInitialized;

    @Inject
    public DomainBlockingService(@Named("parentalControl.redirect.ip") String accessDeniedIp,
                                 @Named("parentalControl.redirect.despite_disabled_ssl") boolean redirectDespiteDisabledSSL,
                                 DeviceService deviceService,
                                 DomainBlacklistService domainBlacklistService,
                                 EblockerDnsServer eblockerDnsServer,
                                 ParentalControlService parentalControlService,
                                 ParentalControlFilterListsService filterListsService,
                                 ProductInfoService productInfoService,
                                 SquidConfigController squidConfigController,
                                 SslService sslService,
                                 UserService userService) {
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

    public Decision isBlocked(Device device, String domain) {
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

    public List<CachingFilter.Stats> getCacheStats(boolean includeDomains) {
        return getCachingFilters()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(a.getName().length(), b.getName().length());
                    if (cmp != 0) {
                        return cmp;
                    }
                    return a.getName().compareTo(b.getName());
                })
                .map(f -> f.getStats(includeDomains))
                .collect(Collectors.toList());
    }

    public void clearCaches() {
        getCachingFilters().forEach(CachingFilter::clear);
    }

    private Filter createDeviceFilter(Device device) throws ExecutionException {
        DeviceConfig deviceConfig = new DeviceConfig(device);
        FilterConfig filterConfig = FilterConfig.createFilterConfigForDevice(deviceConfig, isSslEnabledGlobally());
        DomainFilter<String> filter = filtersByConfig.get(filterConfig, () -> createFilter(filterConfig));
        return new Filter(filter, filterConfig, deviceConfig.getUser().getId(), deviceConfig.getProfile().getId());
    }

    private Filter createDomainBlockedByMalwareAdsTrackersFilter() throws ExecutionException {
        FilterConfig filterConfig = FilterConfig.createFilterConfigForDomainFiltering();

        DomainFilter<String> filter = filtersByConfig.get(filterConfig, () -> createFilter(filterConfig));
        return new Filter(filter, filterConfig, -1, -1);
    }

    private DomainFilter<String> createFilter(FilterConfig filterConfig) {
        Map<Integer, ParentalControlFilterMetaData> metadataById = getEnabledFilterMetadataById();
        DomainFilter<String> malwareFilter = createMalwareFilter(filterConfig, metadataById.values());
        DomainFilter<String> parentalControlFilter = createParentalControlFilter(filterConfig, metadataById);
        DomainFilter<String> adsTrackersFilter = createAdsTrackersFilter(filterConfig, metadataById);
        DomainFilter<String> orFilter = Filters.or(malwareFilter, parentalControlFilter, adsTrackersFilter);
        return Filters.cache(8192, CachingFilter.CacheMode.ALL, orFilter);
    }

    private Map<Integer, ParentalControlFilterMetaData> getEnabledFilterMetadataById() {
        return filterListsService
                .getParentalControlFilterMetaData()
                .stream()
                .filter(m -> !m.isDisabled())
                .collect(Collectors.toMap(ParentalControlFilterMetaData::getId, Function.identity()));
    }

    private DomainFilter<String> createMalwareFilter(FilterConfig config, Collection<ParentalControlFilterMetaData> metaData) {
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

    private DomainFilter<String> createParentalControlFilter(FilterConfig config, Map<Integer, ParentalControlFilterMetaData> metaDataById) {
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

    private BloomFilter<String> getTopLevelBloomFilter(Collection<ParentalControlFilterMetaData> metaData, Category category) {
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

    @SuppressWarnings("unchecked")
    private DomainFilter<String> createHostnameFilters(Set<Integer> ids, Map<Integer, ParentalControlFilterMetaData> metaDataById, BloomFilter<String> topLevelBloomFilter) {
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

    private DomainFilter<String> combineFilters(Set<Integer> ids, Map<Integer, ParentalControlFilterMetaData> metaDataById, BloomFilter<String> topLevelBloomFilter) {
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

    private Map<QueryTransformation, Set<Integer>> getIdsByQueryTransformations(Set<Integer> ids, Map<Integer, ParentalControlFilterMetaData> metaDataById) {
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

    private DomainFilter<String> createAdsTrackersFilter(FilterConfig config, Map<Integer, ParentalControlFilterMetaData> metadataById) {
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

    private Set<Integer> toSet(Integer id) {
        if (id == null) {
            return Collections.emptySet();
        }

        return Collections.singleton(id);
    }

    private DomainFilter<String> wrapBloomFilter(BloomFilter<String> topLevelBloomFilter, DomainFilter<String> filter) {
        if (filter instanceof BloomDomainFilter && filter.getChildFilters().get(0) instanceof SingleFileFilter) {
            return filter;
        }
        return topLevelBloomFilter != null ? Filters.bloom(topLevelBloomFilter, filter) : filter;
    }

    private List<DomainFilter> getFilters(Collection<Integer> ids) {
        return ids.stream().map(domainBlacklistService::getFilter).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T> DomainFilter<T> createFilter(Collection<Integer> ids) {
        DomainFilter[] filters = getFilters(ids).toArray(new DomainFilter[0]);
        return Filters.or(filters);
    }

    private Set<Integer> filterByCategory(Collection<ParentalControlFilterMetaData> metaData, EnumSet<Category> categories) {
        return metaData.stream()
                .filter(m -> categories.contains(m.getCategory()))
                .map(ParentalControlFilterMetaData::getId)
                .collect(Collectors.toSet());
    }

    private synchronized void updateFilteredDevices() {
        logger.debug("updating dns / squid's list of filtered devices");

        Map<Device, FilterConfig> configByEnabledDevice = deviceService.getDevices(false).stream()
                .filter(Device::isEnabled)
                .collect(Collectors.toMap(Function.identity(), device -> FilterConfig.createFilterConfigForDevice(new DeviceConfig(device), isSslEnabledGlobally())));

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

    private Set<Device> filterParentalControlFilteredDevices(Map<Device, FilterConfig> configByEnabledDevice) {
        if (!productInfoService.hasFeature(ProductFeature.FAM)) {
            return Collections.emptySet();
        }

        return configByEnabledDevice.entrySet().stream()
                .filter(e -> e.getValue().parentalControlUrlControlModeEnabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<Device> filterDomainFilteredDevices(Map<Device, FilterConfig> configByEnabledDevice) {
        if (!productInfoService.hasFeature(ProductFeature.PRO)) {
            return Collections.emptySet();
        }

        boolean hasFamilyFeature = productInfoService.hasFeature(ProductFeature.FAM);
        return configByEnabledDevice.entrySet().stream()
                .filter(e -> !e.getValue().parentalControlUrlControlModeEnabled || !hasFamilyFeature)
                .filter(e -> e.getValue().getCustomBlacklistId() != null
                        || e.getValue().getCustomWhitelistId() != null
                        || e.getValue().getFilterMode() == FilterMode.PLUG_AND_PLAY && (e.getValue().isFilterAds() || e.getValue().isFilterTrackers()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<IpAddress> ipAddresses(Collection<Device> device) {
        return device.stream().flatMap(d -> d.getIpAddresses().stream()).collect(Collectors.toSet());
    }

    private synchronized void checkCachedFilter(Device device) {
        logger.debug("checking cached filter for {}", device.getId());

        Filter filter = filterByDeviceId.getIfPresent(device.getId());
        if (filter == null) {
            logger.debug("no cached filter for device {}", device.getId());
            return;
        }

        FilterConfig currentConfig = FilterConfig.createFilterConfigForDevice(new DeviceConfig(device), isSslEnabledGlobally());
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

    private Set<DomainFilter> getFilters() {
        Queue<DomainFilter> filtersToVisit = new LinkedList<>(filterByDeviceId.asMap().values());

        Set<DomainFilter> filters = new HashSet<>();
        DomainFilter<?> current;
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

    private <T> DomainFilter<T> attributeFilter(DomainFilter<T> filter, Map<String, Object> attributes) {
        if (filter instanceof StaticFilter) {
            return filter;
        }
        return new AttributeFilter<>(filter, attributes);
    }

    public class Decision extends FilterDecision<String> {
        private final boolean blocked;
        private final String domain;
        private final Integer profileId;
        private final Integer listId;
        private final int userId;
        private final String target;

        public Decision(boolean blocked, String domain, Integer profileId, Integer listId, int userId, String target) {
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

    private class AttributeDecision<T> extends FilterDecision<T> {
        private final Map<String, Object> attributes;

        AttributeDecision(T domain, boolean blocked, DomainFilter<?> filter, Map<String, Object> attributes) {
            super(domain, blocked, filter);
            this.attributes = attributes;
        }

        Map<String, Object> getAttributes() {
            return attributes;
        }
    }

    private class AttributeFilter<T> implements DomainFilter<T> {

        private final DomainFilter<T> filter;
        private final Map<String, Object> attributes;

        AttributeFilter(DomainFilter<T> filter, Map<String, Object> attributes) {
            this.filter = filter;
            this.attributes = attributes;
        }

        @Override
        public Integer getListId() {
            return filter.getListId();
        }

        @Override
        public String getName() {
            return "(attribute-filter " + filter.getName() + ")";
        }

        @Override
        public int getSize() {
            return filter.getSize();
        }

        @Override
        public Stream<T> getDomains() {
            return filter.getDomains();
        }

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

        @Override
        public List<DomainFilter<?>> getChildFilters() {
            return filter.getChildFilters();
        }
    }

    private class Filter implements DomainFilter<String> {
        private final DomainFilter<String> delegate;
        private final FilterConfig config;
        private final int userId;
        private final Integer profileId;

        public Filter(DomainFilter<String> delegate, FilterConfig config, int userId, Integer profileId) {
            this.delegate = delegate;
            this.config = config;
            this.userId = userId;
            this.profileId = profileId;
        }

        @Override
        public Integer getListId() {
            return null;
        }

        @Override
        public String getName() {
            return "(domain-blocking-filter-" + userId + "-" + profileId + " " + delegate.getName() + ")";
        }

        @Override
        public int getSize() {
            return delegate.getSize();
        }

        @Override
        public Stream<String> getDomains() {
            return delegate.getDomains();
        }

        @Override
        public Decision isBlocked(String domain) {
            FilterDecision<String> delegateDecision = delegate.isBlocked(domain);
            Map<String, Object> attributes = delegateDecision instanceof AttributeDecision ? ((AttributeDecision<String>) delegateDecision).getAttributes() : Collections.emptyMap();
            return new Decision(
                    delegateDecision.isBlocked(),
                    delegateDecision.getDomain(),
                    attributes.containsKey(ATTRIBUTE_ADD_PROFILE_ID) ? profileId : null,
                    delegateDecision.getFilter().getListId(),
                    userId,
                    (String) attributes.get(ATTRIBUTE_TARGET));
        }

        @Override
        public List<DomainFilter<?>> getChildFilters() {
            return Collections.singletonList(delegate);
        }

        FilterConfig getConfig() {
            return config;
        }
    }

    private class DeviceConfig {
        private final Device device;
        private final UserModule user;
        private final UserProfileModule profile;

        public DeviceConfig(Device device) {
            this.device = device;
            user = userService.getUserById(device.getOperatingUser());
            profile = parentalControlService.getProfile(user.getAssociatedProfileId());
        }

        public Device getDevice() {
            return device;
        }

        public UserModule getUser() {
            return user;
        }

        public UserProfileModule getProfile() {
            return profile;
        }
    }

    private static class FilterConfig {
        private FilterMode filterMode;
        private boolean sslEnabled;

        private boolean filterAds;
        private boolean filterTrackers;
        private boolean filterMalware;
        private Integer customBlacklistId;
        private Integer customWhitelistId;

        private boolean parentalControlUrlControlModeEnabled;
        private boolean parentalControlDefaultDeny;
        private Set<Integer> parentalControlPermittedIds;
        private Set<Integer> parentalControlProhibitedIds;

        public static FilterConfig createFilterConfigForDevice(DeviceConfig deviceConfig, boolean sslEnabledGlobally) {
            FilterConfig config = new FilterConfig();
            Device device = deviceConfig.getDevice();
            config.filterMode = FilterModeUtils.getEffectiveFilterMode(sslEnabledGlobally, device);
            config.sslEnabled = device.isSslEnabled();
            config.filterAds = device.isFilterPlugAndPlayAdsEnabled();
            config.filterTrackers = device.isFilterPlugAndPlayTrackersEnabled();
            config.filterMalware = device.isMalwareFilterEnabled();

            UserModule user = deviceConfig.getUser();
            config.customBlacklistId = user.getCustomBlacklistId();
            config.customWhitelistId = user.getCustomWhitelistId();

            UserProfileModule profile = deviceConfig.getProfile();
            config.parentalControlUrlControlModeEnabled = profile.isControlmodeUrls();
            config.parentalControlDefaultDeny = UserProfileModule.InternetAccessRestrictionMode.WHITELIST == profile.getInternetAccessRestrictionMode();
            config.parentalControlPermittedIds = profile.getAccessibleSitesPackages();
            config.parentalControlProhibitedIds = profile.getInaccessibleSitesPackages();
            return config;
        }

        public static FilterConfig createFilterConfigForDomainFiltering() {
            FilterConfig config = new FilterConfig();
            config.filterMode = FilterMode.PLUG_AND_PLAY;
            config.sslEnabled = true;
            config.filterAds = true;
            config.filterTrackers = true;
            config.filterMalware = true;

            config.customBlacklistId = null;
            config.customWhitelistId = null;

            config.parentalControlUrlControlModeEnabled = false;
            config.parentalControlDefaultDeny = false;
            config.parentalControlPermittedIds = Collections.emptySet();
            config.parentalControlProhibitedIds = Collections.emptySet();
            return config;
        }

        public FilterMode getFilterMode() {
            return filterMode;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public boolean isFilterAds() {
            return filterAds;
        }

        public boolean isFilterTrackers() {
            return filterTrackers;
        }

        public boolean isFilterMalware() {
            return filterMalware;
        }

        public Integer getCustomBlacklistId() {
            return customBlacklistId;
        }

        public Integer getCustomWhitelistId() {
            return customWhitelistId;
        }

        public boolean isParentalControlUrlControlModeEnabled() {
            return parentalControlUrlControlModeEnabled;
        }

        public boolean isParentalControlDefaultDeny() {
            return parentalControlDefaultDeny;
        }

        public Set<Integer> getParentalControlPermittedIds() {
            return parentalControlPermittedIds;
        }

        public Set<Integer> getParentalControlProhibitedIds() {
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

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

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.QueryTransformation;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.util.ByteArrays;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainBlockingServiceTest {

    private final String ACCESS_DENIED_IP = "192.168.254.254";

    private DeviceService deviceService;
    private DomainBlacklistService domainBlacklistService;
    private EblockerDnsServer eblockerDnsServer;
    private ParentalControlService parentalControlService;
    private ParentalControlFilterListsService parentalControlFilterListsService;
    private ProductInfoService productInfoService;
    private SquidConfigController squidConfigController;
    private SslService sslService;
    private UserService userService;

    private DomainBlockingService service;

    private UserProfileModule[] profiles;
    private Device disabledDevice;
    private Device disabledCustomDevice;
    private Device enabledDevice;
    private Device parentalControlDevice;
    private Device enabledParentalControlDevice;
    private Device automaticDevice;
    private Device missingListsDevice;

    @Before
    public void setup() {
        // setup simple mocks
        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);
        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        squidConfigController = Mockito.mock(SquidConfigController.class);

        // setup ssl mock
        sslService = Mockito.mock(SslService.class);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        // setup devices
        disabledDevice = createMockDevice("device:192168003007", 2, Arrays.asList("192.168.3.7"), FilterMode.NONE, false);
        disabledCustomDevice = createMockDevice("device:19216800320", 0, Arrays.asList("192.168.3.20"), FilterMode.NONE, false);
        enabledDevice = createMockDevice("device:192168003004", 0, Arrays.asList("192.168.3.4"), FilterMode.PLUG_AND_PLAY, true);
        parentalControlDevice = createMockDevice("device:192168003005", 3, Arrays.asList("192.168.3.5", "192.168.3.6"), FilterMode.NONE, true);
        enabledParentalControlDevice = createMockDevice("device:192168003010", 1, Arrays.asList("192.168.3.10"), FilterMode.PLUG_AND_PLAY, true);
        automaticDevice = createMockDevice("device:192168003020", 2, Arrays.asList("192.168.3.20"), FilterMode.AUTOMATIC, true);
        missingListsDevice = createMockDevice("device:192168003030", 4, Arrays.asList("192.168.3.30"), FilterMode.PLUG_AND_PLAY, true);

        // setup device service
        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceById(disabledDevice.getId())).thenReturn(disabledDevice);
        Mockito.when(deviceService.getDeviceById(disabledCustomDevice.getId())).thenReturn(disabledCustomDevice);
        Mockito.when(deviceService.getDeviceById(enabledDevice.getId())).thenReturn(enabledDevice);
        Mockito.when(deviceService.getDeviceById(parentalControlDevice.getId())).thenReturn(parentalControlDevice);
        Mockito.when(deviceService.getDeviceById(enabledParentalControlDevice.getId())).thenReturn(enabledParentalControlDevice);
        Mockito.when(deviceService.getDeviceById(automaticDevice.getId())).thenReturn(automaticDevice);
        Mockito.when(deviceService.getDeviceById(missingListsDevice.getId())).thenReturn(missingListsDevice);
        Mockito.when(deviceService.getDevices(false)).then(m -> Arrays.asList(enabledDevice, disabledDevice, disabledCustomDevice, parentalControlDevice, enabledParentalControlDevice, automaticDevice, missingListsDevice));

        // setup users
        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(0)).thenReturn(createMockUser(0, 0, 3, 2));
        Mockito.when(userService.getUserById(1)).thenReturn(createMockUser(1, 1, 3, 2));
        Mockito.when(userService.getUserById(2)).thenReturn(createMockUser(2, 0, null, null));
        Mockito.when(userService.getUserById(3)).thenReturn(createMockUser(3, 1, null, null));
        Mockito.when(userService.getUserById(4)).thenReturn(createMockUser(4, 2, 300, 400));

        // setup parental control profiles
        profiles = new UserProfileModule[]{
            new UserProfileModule(0, null, null, null, null, null, null, Collections.emptySet(), Collections.emptySet(), UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, null, null, null, false, null),
            new UserProfileModule(1, null, null, null, null, null, null, Collections.singleton(11), Sets.newHashSet(10, 12, 13, 16, 17), UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, null, null, null, false, null),
            new UserProfileModule(2, null, null, null, null, null, null, Sets.newHashSet(11, 100), Sets.newHashSet(10, 200, 16, 17), UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, null, null, null, false, null)
        };
        profiles[1].setControlmodeUrls(true);
        profiles[2].setControlmodeUrls(true);

        // setup parental control service
        parentalControlService = Mockito.mock(ParentalControlService.class);
        Mockito.when(parentalControlService.getProfile(0)).thenReturn(profiles[0]);
        Mockito.when(parentalControlService.getProfile(1)).thenReturn(profiles[1]);
        Mockito.when(parentalControlService.getProfile(2)).thenReturn(profiles[2]);

        // setup parental control filter lists service
        parentalControlFilterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        Mockito.when(parentalControlFilterListsService.getParentalControlFilterMetaData()).thenReturn(Arrays.asList(
            new ParentalControlFilterMetaData(0, null, null, Category.ADS, null, null, null, "domainblacklist/string", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(1, null, null, Category.TRACKERS, null, null, null, "domainblacklist/string", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(2, null, null, Category.CUSTOM, null, null, null, "domainblacklist/string", null, false, false, null, null, null),
            new ParentalControlFilterMetaData(3, null, null, Category.CUSTOM, null, null, null, "domainblacklist/string", null, false, false, null, null, null),
            new ParentalControlFilterMetaData(10, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/string", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(11, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/string", null, false, false, null, null, null),
            new ParentalControlFilterMetaData(12, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/hash-md5", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(13, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/hash-sha1", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(14, null, null, Category.ADS_TRACKERS_BLOOM_FILTER, null, null, null, "domainblacklist/bloom", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(15, null, null, Category.PARENTAL_CONTROL_BLOOM_FILTER, null, null, null, "domainblacklist/bloom", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(16, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/string", null, false, false,
                Arrays.asList(new QueryTransformation("^\\.?", "md5.prefix."), new QueryTransformation("$", ".md5.suffix")), null, null),
            new ParentalControlFilterMetaData(17, null, null, Category.PARENTAL_CONTROL, null, null, null, "domainblacklist/hash-md5", null, false, false,
                Arrays.asList(new QueryTransformation("^\\.?", "md5.prefix."), new QueryTransformation("$", ".md5.suffix")), null, null),
            new ParentalControlFilterMetaData(18, null, null, Category.MALWARE, null, null, null, "domainblacklist/string", null, true, false, null, null, null),
            new ParentalControlFilterMetaData(19, null, null, Category.ADS, null, null, null, "domainblacklist/string", null, false, false, null, null, null),
            new ParentalControlFilterMetaData(20, null, null, Category.ADS, null, null, null, "domainblacklist/string", null, false, true, null, null, null)
                                                                                                                   ));

        // setup licensed features
        productInfoService = Mockito.mock(ProductInfoService.class);
        Mockito.when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(true);
        Mockito.when(productInfoService.hasFeature(ProductFeature.PRO)).thenReturn(true);

        // setup filter
        DomainFilter<String> adsFilter = new CollectionFilter<>(0, Arrays.asList(".ads.com"));
        DomainFilter<String> customEnabledAdsFilter = new CollectionFilter<>(19, Arrays.asList(".custom-enabled-ads.com"));
        DomainFilter<String> customDisabledAdsFilter = new CollectionFilter<>(19, Arrays.asList(".custom-disabled-ads.com"));
        DomainFilter<String> trackerFilter = new CollectionFilter<>(1, Arrays.asList(".trackers.com"));
        DomainFilter<String> customWhitelistFilter = new CollectionFilter<>(2, Arrays.asList("whitelist.ads.com", "whitelist.trackers.com", "whitelist.custom-enabled-ads.com"));
        DomainFilter<String> customBlacklistFilter = new CollectionFilter<>(3, Arrays.asList("blacklist-ads.com", "blacklist-trackers.com"));
        DomainFilter<String> parentalControlFilter = new CollectionFilter<>(10, Arrays.asList(".parentalcontrol.com"));
        DomainFilter<String> parentalControlWhitelistFilter = new CollectionFilter<>(11, Arrays.asList("whitelist.parentalcontrol.com"));
        DomainFilter<byte[]> parentalControlMd5Filter = new CollectionFilter<>(12, newTreeSet(ByteArrays::compare, hash("md5.parentalcontrol.com", Hashing.md5())));
        DomainFilter<byte[]> parentalControlSha1Filter = new CollectionFilter<>(13, newTreeSet(ByteArrays::compare, hash("sha1.parentalcontrol.com", Hashing.sha1())));
        DomainFilter<String> parentalControlPlainReplaceFilter = new CollectionFilter<>(16, Arrays.asList("plain.prefix.replace.com", "replace2.com.plain.suffix"));
        DomainFilter<byte[]> parentalControlHashReplaceFilter = new CollectionFilter<>(17, newTreeSet(ByteArrays::compare, hash("md5.prefix.replace.com", Hashing.md5()), hash("replace2.com.md5.suffix", Hashing.md5())));
        DomainFilter<String> malwareFilter = new CollectionFilter<>(18, Collections.singleton("malware.com"));

        // create top-level bloom filters
        BloomFilter<CharSequence> adsTrackerBloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 100, 0.01);
        adsFilter.getDomains().forEach(adsTrackerBloomFilter::put);
        trackerFilter.getDomains().forEach(adsTrackerBloomFilter::put);

        BloomFilter<CharSequence> parentalControlBloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 100, 0.01);
        parentalControlFilter.getDomains().forEach(parentalControlBloomFilter::put);

        // setup blacklist service
        domainBlacklistService = Mockito.mock(DomainBlacklistService.class);
        Mockito.when(domainBlacklistService.getFilter(0)).thenReturn(adsFilter);
        Mockito.when(domainBlacklistService.getFilter(1)).thenReturn(trackerFilter);
        Mockito.when(domainBlacklistService.getFilter(2)).thenReturn(customWhitelistFilter);
        Mockito.when(domainBlacklistService.getFilter(3)).thenReturn(customBlacklistFilter);
        Mockito.when(domainBlacklistService.getFilter(10)).thenReturn(parentalControlFilter);
        Mockito.when(domainBlacklistService.getFilter(11)).thenReturn(parentalControlWhitelistFilter);
        Mockito.when(domainBlacklistService.getFilter(12)).thenReturn(parentalControlMd5Filter);
        Mockito.when(domainBlacklistService.getFilter(13)).thenReturn(parentalControlSha1Filter);
        Mockito.when(domainBlacklistService.getFilter(14)).thenReturn(new BloomDomainFilter<>(adsTrackerBloomFilter, null));
        Mockito.when(domainBlacklistService.getFilter(15)).thenReturn(new BloomDomainFilter<>(parentalControlBloomFilter, null));
        Mockito.when(domainBlacklistService.getFilter(16)).thenReturn(parentalControlPlainReplaceFilter);
        Mockito.when(domainBlacklistService.getFilter(17)).thenReturn(parentalControlHashReplaceFilter);
        Mockito.when(domainBlacklistService.getFilter(18)).thenReturn(malwareFilter);
        Mockito.when(domainBlacklistService.getFilter(19)).thenReturn(customEnabledAdsFilter);
        Mockito.when(domainBlacklistService.getFilter(20)).thenReturn(customDisabledAdsFilter);

        service = new DomainBlockingService(ACCESS_DENIED_IP, false, deviceService, domainBlacklistService, eblockerDnsServer, parentalControlService, parentalControlFilterListsService, productInfoService, squidConfigController, sslService, userService);
    }

    @Test
    public void testRegistrations() {
        service.init();

        Mockito.verify(deviceService).addListener(Mockito.any(DeviceService.DeviceChangeListener.class));
        Mockito.verify(parentalControlService).addListener(Mockito.any(ParentalControlService.ParentalControlProfileChangeListener.class));
        Mockito.verify(userService).addListener(Mockito.any(UserService.UserChangeListener.class));
        Mockito.verify(eblockerDnsServer).addListener(Mockito.any(EblockerDnsServer.Listener.class));
        Mockito.verify(sslService).addListener(Mockito.any(SslService.SslStateListener.class));
    }

    @Test
    public void testInit() {
        service.init();

        Mockito.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(enabledDevice, disabledCustomDevice));
        Mockito.verify(squidConfigController).updateDomainFilteredDevices(Collections.emptySet());
    }

    @Test
    public void testUpdateFilteredDevices() {
        service.init();
        Mockito.reset(eblockerDnsServer);
        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        Mockito.reset(squidConfigController);

        // extract listener to enable updates
        ArgumentCaptor<DeviceService.DeviceChangeListener> captor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(captor.capture());
        DeviceService.DeviceChangeListener listener = captor.getValue();

        // first update: change filter mode to none, expecting no change in filtered devices
        enabledDevice.setFilterMode(FilterMode.NONE);
        listener.onChange(enabledDevice);

        InOrder squidInOrder = Mockito.inOrder(squidConfigController);
        squidInOrder.verify(squidConfigController).updateDomainFilteredDevices(Collections.emptySet());

        InOrder dnsInOrder = Mockito.inOrder(eblockerDnsServer);
        dnsInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(enabledDevice, disabledCustomDevice, automaticDevice));

        // second update: change user to one without custom blacklist / whitelist, expecting removal of device from filtered ones
        enabledDevice.setOperatingUser(2);
        listener.onChange(enabledDevice);

        dnsInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(disabledCustomDevice, automaticDevice));
        squidInOrder.verify(squidConfigController).updateDomainFilteredDevices(Collections.emptySet());

        // third: disable domain filtering in parental control profile, expecting one device moved to domain filtered list and second to disappear
        profiles[1].setControlmodeUrls(false);
        listener.onChange(parentalControlDevice);

        dnsInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(missingListsDevice), ipAddresses(enabledParentalControlDevice, disabledCustomDevice, automaticDevice));
        squidInOrder.verify(squidConfigController).updateDomainFilteredDevices(Collections.emptySet());
    }

    @Test
    public void testFilterDisabledDeviceNoParentalControl() {
        service.init();

        Assert.assertFalse(service.isBlocked(disabledDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "replace2.com").isBlocked());
        Assert.assertFalse(service.isBlocked(disabledDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterEnabledDeviceNoParentalControl() {
        service.init();

        Assert.assertFalse(service.isBlocked(enabledDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "blacklist-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterDisabledDeviceParentalControl() {
        service.init();

        Assert.assertFalse(service.isBlocked(parentalControlDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(parentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(parentalControlDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterEnabledDeviceParentalControl() {
        service.init();

        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterAutomaticDeviceSslDisabledGlobalSslEnabled() {
        service.init();

        Assert.assertFalse(service.isBlocked(automaticDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "malware.com").isBlocked());
    }

    @Test
    public void testAutomaticDeviceSslEnabledGlobalSslEnabled() {
        automaticDevice.setSslEnabled(true);
        service.init();
        ArgumentCaptor<SslService.SslStateListener> captor = ArgumentCaptor.forClass(SslService.SslStateListener.class);
        Mockito.verify(sslService).addListener(captor.capture());
        captor.getValue().onInit(true);

        Assert.assertFalse(service.isBlocked(automaticDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterAutomaticDeviceSslEnabledGlobalSslDisabled() {
        automaticDevice.setSslEnabled(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        service.init();
        ArgumentCaptor<SslService.SslStateListener> captor = ArgumentCaptor.forClass(SslService.SslStateListener.class);
        Mockito.verify(sslService).addListener(captor.capture());
        captor.getValue().onInit(false);

        Assert.assertFalse(service.isBlocked(automaticDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "random.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(automaticDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(automaticDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterMissingListsDevice() {
        service.init();

        Assert.assertFalse(service.isBlocked(missingListsDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(missingListsDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "random.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "whitelist.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(missingListsDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(missingListsDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(missingListsDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "replace2.com").isBlocked());
        Assert.assertTrue(service.isBlocked(missingListsDevice, "malware.com").isBlocked());
    }

    @Test
    public void testFilterUpdate() {
        service.init();

        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "parentalcontrol2.com").isBlocked());

        ArgumentCaptor<DomainBlacklistService.Listener> listenerCaptor =
            ArgumentCaptor.forClass(DomainBlacklistService.Listener.class);
        Mockito.verify(domainBlacklistService).addListener(listenerCaptor.capture());

        DomainFilter<String> parentalControlFilter = new CollectionFilter<>(10, Arrays.asList(".parentalcontrol2.com"));
        BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 100, 0.01);
        parentalControlFilter.getDomains().forEach(bloomFilter::put);

        Mockito.when(domainBlacklistService.getFilter(10)).thenReturn(parentalControlFilter);
        Mockito.when(domainBlacklistService.getFilter(15)).thenReturn(new BloomDomainFilter<>(bloomFilter, null));
        listenerCaptor.getValue().onUpdate();

        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol2.com").isBlocked());
    }

    @Test
    public void testUserProfileChange() {
        service.init();

        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());

        ArgumentCaptor<ParentalControlService.ParentalControlProfileChangeListener> listenerCaptor =
            ArgumentCaptor.forClass(ParentalControlService.ParentalControlProfileChangeListener.class);
        Mockito.verify(parentalControlService).addListener(listenerCaptor.capture());

        profiles[1].setAccessibleSitesPackages(Collections.emptySet());
        listenerCaptor.getValue().onChange(profiles[1]);

        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
    }

    @Test
    public void testUserChange() {
        service.init();

        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());

        ArgumentCaptor<UserService.UserChangeListener> listenerCaptor =
            ArgumentCaptor.forClass(UserService.UserChangeListener.class);
        Mockito.verify(userService).addListener(listenerCaptor.capture());

        UserModule user = createMockUser(1, 0, null, null);
        Mockito.when(userService.getUserById(1)).thenReturn(user);
        listenerCaptor.getValue().onChange(user);

        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
    }

    @Test
    public void testDnsDisableEnable() {
        service.init();
        Mockito.reset(squidConfigController);

        ArgumentCaptor<EblockerDnsServer.Listener> listenerCaptor =
            ArgumentCaptor.forClass(EblockerDnsServer.Listener.class);
        Mockito.verify(eblockerDnsServer).addListener(listenerCaptor.capture());

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(false);
        listenerCaptor.getValue().onEnable(false);

        InOrder squidInOrder = Mockito.inOrder(squidConfigController);
        InOrder dnsInOrder = Mockito.inOrder(eblockerDnsServer);
        squidInOrder.verify(squidConfigController).updateDomainFilteredDevices(Sets.newHashSet(enabledParentalControlDevice, parentalControlDevice, enabledDevice, disabledCustomDevice, automaticDevice, missingListsDevice));
        dnsInOrder.verify(eblockerDnsServer).setFilteredPeers(Collections.emptySet(), Collections.emptySet());

        // check ads/trackers are not filtered any more
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());

        // re-enable eblocker-dns
        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        listenerCaptor.getValue().onEnable(true);

        squidInOrder.verify(squidConfigController).updateDomainFilteredDevices(Collections.emptySet());
        dnsInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(enabledDevice, disabledCustomDevice));

        // check ads/trackers are filtered again
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());
    }

    @Test
    public void testSslDisable() {
        automaticDevice.setSslEnabled(true);
        service.init();
        ArgumentCaptor<SslService.SslStateListener> captor = ArgumentCaptor.forClass(SslService.SslStateListener.class);
        Mockito.verify(sslService).addListener(captor.capture());
        captor.getValue().onInit(true);

        InOrder eblockerDnsServerInOrder = Mockito.inOrder(eblockerDnsServer);

        Assert.assertFalse(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        eblockerDnsServerInOrder.verify(eblockerDnsServer).setFilteredPeers(
            ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice),
            ipAddresses(enabledDevice, disabledCustomDevice));
        Mockito.verify(squidConfigController, Mockito.times(2)).updateDomainFilteredDevices(Collections.emptySet());

        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        captor.getValue().onDisable();

        Assert.assertTrue(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        eblockerDnsServerInOrder.verify(eblockerDnsServer).setFilteredPeers(
            ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice),
            ipAddresses(enabledDevice, disabledCustomDevice, automaticDevice));
        Mockito.verify(squidConfigController, Mockito.times(3)).updateDomainFilteredDevices(Collections.emptySet());
    }

    @Test
    public void testSslEnable() {
        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        service.init();
        ArgumentCaptor<SslService.SslStateListener> captor = ArgumentCaptor.forClass(SslService.SslStateListener.class);
        Mockito.verify(sslService).addListener(captor.capture());
        captor.getValue().onInit(false);

        InOrder eblockerDnsServerInOrder = Mockito.inOrder(eblockerDnsServer);

        Assert.assertTrue(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        eblockerDnsServerInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(enabledDevice, disabledCustomDevice, automaticDevice));
        Mockito.verify(squidConfigController, Mockito.times(2)).updateDomainFilteredDevices(Collections.emptySet());

        captor.getValue().onEnable();

        Assert.assertTrue(service.isBlocked(automaticDevice, "random.ads.com").isBlocked());
        eblockerDnsServerInOrder.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), ipAddresses(enabledDevice, disabledCustomDevice));
        Mockito.verify(squidConfigController, Mockito.times(3)).updateDomainFilteredDevices(Collections.emptySet());
    }

    @Test
    public void testAccessDeniedIpSslDisabled() {
        service.init();

        DomainBlockingService.Decision decision = service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com");
        Assert.assertTrue(decision.isBlocked());
        Assert.assertEquals(ACCESS_DENIED_IP, decision.getTarget());

        decision = service.isBlocked(enabledParentalControlDevice, "random.ads.com");
        Assert.assertTrue(decision.isBlocked());
        Assert.assertNull(decision.getTarget());
    }

    @Test
    public void testAccessDeniedIpSslEnabled() {
        enabledParentalControlDevice.setSslEnabled(true);
        service.init();

        DomainBlockingService.Decision decision = service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com");
        Assert.assertTrue(decision.isBlocked());
        Assert.assertEquals(ACCESS_DENIED_IP, decision.getTarget());

        decision = service.isBlocked(enabledParentalControlDevice, "random.ads.com");
        Assert.assertTrue(decision.isBlocked());
        Assert.assertEquals(ACCESS_DENIED_IP, decision.getTarget());
    }

    @Test
    public void testProductFeatureProOnly() {
        Mockito.when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(false);
        Mockito.when(productInfoService.hasFeature(ProductFeature.PRO)).thenReturn(true);

        service.init();

        Mockito.verify(eblockerDnsServer).setFilteredPeers(Collections.emptySet(), ipAddresses(enabledDevice, enabledParentalControlDevice, automaticDevice, missingListsDevice));
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());
    }

    @Test
    public void testProductFeatureFamOnly() {
        Mockito.when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(true);
        Mockito.when(productInfoService.hasFeature(ProductFeature.PRO)).thenReturn(false);

        service.init();

        Mockito.verify(eblockerDnsServer).setFilteredPeers(ipAddresses(parentalControlDevice, enabledParentalControlDevice, missingListsDevice), Collections.emptySet());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertTrue(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());
    }

    @Test
    public void testProductFeatureNeitherFamNorPro() {
        Mockito.when(productInfoService.hasFeature(ProductFeature.FAM)).thenReturn(false);
        Mockito.when(productInfoService.hasFeature(ProductFeature.PRO)).thenReturn(false);

        service.init();

        Mockito.verify(eblockerDnsServer).setFilteredPeers(Collections.emptySet(), Collections.emptySet());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "test.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.custom-disabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "random.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "replace.com").isBlocked());
        Assert.assertFalse(service.isBlocked(enabledParentalControlDevice, "replace2.com").isBlocked());
    }

    @Test
    public void testIsBlockedForAllDevices() {
        service.init();

        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("test.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("random.ads.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("random.custom-enabled-ads.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("random.custom-disabled-ads.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("random.trackers.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("whitelist.ads.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("whitelist.custom-enabled-ads.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("whitelist.trackers.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("blacklist-ads.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("blacklist-trackers.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("whitelist.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("md5.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("sha1.parentalcontrol.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("replace.com").isBlocked());
        Assert.assertFalse(service.isDomainBlockedByMalwareAdsTrackersFilters("replace2.com").isBlocked());
        Assert.assertTrue(service.isDomainBlockedByMalwareAdsTrackersFilters("malware.com").isBlocked());
    }

    private Device createMockDevice(String id, int userId, List<String> ipAddresses, FilterMode filterMode, boolean malwareFilterEnabled) {
        Device device = new Device();
        device.setId(id);
        device.setOperatingUser(userId);
        device.setIpAddresses(ipAddresses.stream().map(IpAddress::parse).collect(Collectors.toList()));
        device.setFilterMode(filterMode);
        device.setMalwareFilterEnabled(malwareFilterEnabled);
        return device;
    }

    private UserModule createMockUser(Integer id, Integer associatedProfileId, Integer blacklistId, Integer whitelistId) {
        return new UserModule(id, associatedProfileId, "user-" + id, null, null, null, false, null, Collections.emptyMap(), null, blacklistId, whitelistId);
    }

    private byte[] hash(String value, HashFunction hashFunction) {
        return hashFunction.hashString(value, Charsets.UTF_8).asBytes();
    }

    private <T> TreeSet<T> newTreeSet(Comparator<T> comparator, T... values) {
        TreeSet<T> treeSet = new TreeSet<>(comparator);
        for (T value : values) {
            treeSet.add(value);
        }
        return treeSet;
    }

    private Set<IpAddress> ipAddresses(Device... devices) {
        return Stream.of(devices).flatMap(device -> device.getIpAddresses().stream()).collect(Collectors.toSet());
    }
}

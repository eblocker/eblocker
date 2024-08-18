/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.DoctorDiagnosisResult;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.ProblematicRouterDetection;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.http.security.SecurityService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.EVERYONE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.GOOD;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.RECOMMENDATION_NOT_FOLLOWED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ATA_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DEVICES_BLOCKING_TEST_DOMAIN;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.STANDARD_PATTERN_FILTER_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.TEST_DOMAIN_HTTPS_WHITELISTED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {
    @Mock
    private ProblematicRouterDetection problematicRouterDetection;
    @Mock
    private NetworkServices networkServices;
    @Mock
    private SslService sslService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceFactory deviceFactory;
    @Mock
    private ProductInfoService productInfoService;
    @Mock
    private SystemUpdater systemUpdater;
    @Mock
    private ParentalControlService parentalControlService;
    @Mock
    private DnsService dnsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AppModuleService appModuleService;
    @Mock
    private FilterManager filterManager;
    @Mock
    private DomainBlockingService domainBlockingService;

    @InjectMocks
    private DoctorService doctorService;

    private NetworkConfiguration networkConfiguration = new NetworkConfiguration();
    private DnsResolvers dnsResolvers = new DnsResolvers();
    private final AppWhitelistModule ataAppModule = createAppModule(9997);

    private final Device enabledDevice = TestDeviceFactory.createDevice("111111111111", "192.168.1.111", "My enabled device", true);
    private final Device disabledDevice = TestDeviceFactory.createDevice("222222222222", "192.168.1.222", "My disabled device", false);

    private final DoctorDiagnosisResult enabledDeviceBlocksTestDomain = new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, EVERYONE, DEVICES_BLOCKING_TEST_DOMAIN, enabledDevice.getName());
    private final DoctorDiagnosisResult testDomainHttpsWhitelisted = new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, EVERYONE, TEST_DOMAIN_HTTPS_WHITELISTED, "");

    @BeforeEach
    void setUp() {
        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfiguration);
        Mockito.when(dnsService.getDnsResolvers()).thenReturn(dnsResolvers);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        Mockito.when(appModuleService.getAutoSslAppModule()).thenReturn(ataAppModule);
        IntStream.range(0, 7).forEach(id ->
                Mockito.when(filterManager.getFilterStoreConfigurationById(id)).thenReturn(createFilterStoreConfiguration(id)));
        Mockito.when(deviceService.getDevices(true)).thenReturn(List.of(enabledDevice, disabledDevice));
        Mockito.when(domainBlockingService.isBlocked(Mockito.any(Device.class), Mockito.eq("eblocker.org"))).thenReturn(new DomainBlockingService.Decision(false, "eblocker.org", null, null, 0, null));
    }

    @Test
    void testRunDiagnosis() {
        List<DoctorDiagnosisResult> results = doctorService.runDiagnosis();

        IntStream.range(0, 5).forEach(id ->
                assertTrue(results.contains(new DoctorDiagnosisResult(GOOD, EVERYONE, STANDARD_PATTERN_FILTER_ENABLED, "FilterStoreConfiguration #" + id))));
        assertTrue(results.contains(new DoctorDiagnosisResult(GOOD, EVERYONE, ATA_ENABLED, "")));
        assertFalse(results.contains(enabledDeviceBlocksTestDomain));
        assertFalse(results.contains(testDomainHttpsWhitelisted));
    }

    @Test
    void testTestDomainBlocked() {
        Mockito.when(domainBlockingService.isBlocked(enabledDevice, "eblocker.org")).thenReturn(new DomainBlockingService.Decision(true, "eblocker.org", null, null, 0, null));

        List<DoctorDiagnosisResult> results = doctorService.runDiagnosis();

        assertTrue(results.contains(enabledDeviceBlocksTestDomain));
    }

    @Test
    void testTestDomainHttpsWhitelisted() {
        Mockito.when(appModuleService.getAllUrlsFromEnabledModules()).thenReturn(List.of("eblocker.org"));

        List<DoctorDiagnosisResult> results = doctorService.runDiagnosis();

        assertTrue(results.contains(testDomainHttpsWhitelisted));
    }

    private static FilterStoreConfiguration createFilterStoreConfiguration(int id) {
        return new FilterStoreConfiguration(id, "FilterStoreConfiguration #" + id, null, true, 1, null, null, null, true, null, true);
    }

    private static AppWhitelistModule createAppModule(int id) {
        return new AppWhitelistModule(id, "AppModule #" + id, null, null, null, null, null, null, true, null, null, null, null, null);
    }
}
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

import org.eblocker.registration.ProductFeature;
import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationInfo;
import org.eblocker.server.common.registration.LicenseExpirationService;
import org.eblocker.server.common.registration.LicenseExpirationService.LicenseExpirationListener;
import org.eblocker.server.common.ssl.SslService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProductMigrationServiceTest {
    private ProductMigrationService service;
    private DeviceService deviceService;
    private UserService userService;
    private ParentalControlService parentalControlService;
    private LicenseExpirationService licenseExpirationService;
    private ProductInfoService productInfoService;
    private Device deviceAlpha;
    private TorController torController;
    private EblockerDnsServer dnsServer;
    private SslService sslService;
    private Set<Device> devices;
    private AnonymousService anonymousService;
    private DeviceRegistrationClient deviceRegistrationClient;

    private final static ProductInfo DEMO = new ProductInfo(
            "product-id-demo",
            "product-name-demo",
            new String[]{ "EVL_BAS", "EVL_PRO", "EVL_FAM", "BAS", "PRO", "FAM" }
    );

    private final static ProductInfo FAM = new ProductInfo(
            "product-id-demo",
            "product-name-demo",
            new String[]{ "BAS", "PRO", "FAM" }
    );

    @Before
    public void setUp() {
        sslService = Mockito.mock(SslService.class);
        torController = Mockito.mock(TorController.class);

        dnsServer = Mockito.mock(EblockerDnsServer.class);
        when(dnsServer.getDnsResolvers()).thenReturn(new DnsResolvers());

        deviceRegistrationClient = Mockito.mock(DeviceRegistrationClient.class);
        anonymousService = Mockito.mock(AnonymousService.class);
        deviceService = Mockito.mock(DeviceService.class);
        userService = Mockito.mock(UserService.class);
        parentalControlService = Mockito.mock(ParentalControlService.class);

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.get(ProductInfo.class, ProductInfoService.KEY)).thenReturn(DEMO);
        productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();

        licenseExpirationService = Mockito.mock(LicenseExpirationService.class);
        service = new ProductMigrationService(deviceService,
                sslService,
                torController,
                dnsServer,
                deviceRegistrationClient,
                anonymousService,
                userService,
                parentalControlService,
                licenseExpirationService,
                productInfoService);

        // Devices
        deviceAlpha = new Device();
        deviceAlpha.setName("alpha");
        deviceAlpha.setAssignedUser(47);
        deviceAlpha.setOperatingUser(11);

        Device deviceBravo = new Device();
        deviceBravo.setName("bravo");
        deviceBravo.setAssignedUser(1);
        deviceBravo.setOperatingUser(23);

        Device deviceCharlie = new Device();
        deviceCharlie.setName("charlie");
        deviceCharlie.setAssignedUser(42);
        deviceCharlie.setOperatingUser(1);

        devices = new HashSet<>();
        devices.add(deviceAlpha);
        devices.add(deviceBravo);
        devices.add(deviceCharlie);

        Mockito.when(deviceService.getDevices(anyBoolean())).thenReturn(devices);
    }

    @Test
    public void testDowngradeALL() {
        UserModule userModule = mock(UserModule.class);
        when(userService.restoreDefaultSystemUser(any(), anyInt())).thenReturn(userModule);
        when(userModule.getId()).thenReturn(1);

        ProductInfo oldProdInfo = new ProductInfo("prod-id-old", "prod-name-old", new String[]{ "FAM", "BAS", "PRO" });
        DeviceRegistrationInfo oldLicense = Mockito.mock(DeviceRegistrationInfo.class);
        Mockito.when(oldLicense.getProductInfo()).thenReturn(oldProdInfo);
        ProductInfo newProdInfo = new ProductInfo("prod-id-new", "prod-name-new", new String[]{});
        DeviceRegistrationInfo newLicense = Mockito.mock(DeviceRegistrationInfo.class);
        Mockito.when(newLicense.getProductInfo()).thenReturn(newProdInfo);

        service.changeProduct(
                deviceRegistrationInfo(new String[]{ "FAM", "BAS", "PRO" }),
                deviceRegistrationInfo(new String[]{}));

        for (Device device : devices) {
            assertBasDisabled(device);

            assertProDisabled();

            assertFamDisabled(device);
        }
    }

    @Test
    public void testDowngradeFAM() {
        UserModule userModule = mock(UserModule.class);
        when(userService.restoreDefaultSystemUser(any(), anyInt())).thenReturn(userModule);
        when(userModule.getId()).thenReturn(1);

        service.changeProduct(
                deviceRegistrationInfo(new String[]{ "FAM", "BAS", "PRO" }),
                deviceRegistrationInfo(new String[]{ "BAS", "PRO" }));

        verify(parentalControlService).createDefaultProfile();
        verify(userService).restoreDefaultSystemUser(any(), anyInt());

        for (Device device : devices) {
            assertFamDisabled(device);
        }
    }

    @Test
    public void testDowngradePRO() {
        service.changeProduct(
                deviceRegistrationInfo(new String[]{ "FAM", "BAS", "PRO" }),
                deviceRegistrationInfo(new String[]{ "FAM", "BAS" }));

        Mockito.verify(deviceService, Mockito.never()).updateDevice(any());
        assertProDisabled();
    }

    @Test
    public void testDowngradeBAS() {
        service.changeProduct(
                deviceRegistrationInfo(new String[]{ "BAS" }),
                deviceRegistrationInfo(new String[]{}));

        for (Device device : devices) {
            assertBasDisabled(device);
        }
    }

    @Test
    public void testNotDowngrade() {
        DeviceRegistrationInfo oldLicense = deviceRegistrationInfo(new String[]{ "OLD" });

        service.changeProduct(oldLicense, deviceRegistrationInfo(new String[]{ "FAM" }));

        assertTrue(productInfoService.hasFeature(ProductFeature.BAS));

        DeviceRegistrationInfo newLicense = Mockito.mock(DeviceRegistrationInfo.class);
        Mockito.when(newLicense.getProductInfo()).thenReturn(null);

        service.changeProduct(oldLicense, newLicense);
    }

    @Test
    public void testExpirationForDemo() {
        UserModule userModule = mock(UserModule.class);
        when(userService.restoreDefaultSystemUser(any(), anyInt())).thenReturn(userModule);
        when(userModule.getId()).thenReturn(1);

        ArgumentCaptor<LicenseExpirationListener> captor = ArgumentCaptor.forClass(LicenseExpirationListener.class);

        verify(licenseExpirationService).addListener(captor.capture());

        captor.getValue().onChange();

        assertFalse(productInfoService.hasFeature(ProductFeature.BAS));
        assertFalse(productInfoService.hasFeature(ProductFeature.PRO));
        assertFalse(productInfoService.hasFeature(ProductFeature.FAM));

        assertProDisabled();

        for (Device device : devices) {
            assertBasDisabled(device);
            assertFamDisabled(device);
        }
    }

    private void assertProDisabled() {
        verify(sslService).disableSsl();
    }

    @Test
    public void testDoNotExpireNonDemoLicense() {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.get(ProductInfo.class, ProductInfoService.KEY)).thenReturn(FAM);
        ProductInfoService productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();
        licenseExpirationService = Mockito.mock(LicenseExpirationService.class);

        service = new ProductMigrationService(deviceService,
                sslService,
                torController,
                dnsServer,
                deviceRegistrationClient,
                anonymousService,
                userService,
                parentalControlService,
                licenseExpirationService,
                productInfoService);

        ArgumentCaptor<LicenseExpirationListener> captor = ArgumentCaptor.forClass(LicenseExpirationListener.class);

        verify(licenseExpirationService).addListener(captor.capture());

        captor.getValue().onChange();

        assertEquals(productInfoService.get(), FAM);
    }

    private void assertBasDisabled(Device device) {
        assertFalse(device.isRoutedThroughTor());
        assertFalse(device.isVpnClient());
        assertFalse(device.isUseAnonymizationService());
        assertTrue(device.isMessageShowAlert());
        assertTrue(device.isMessageShowInfo());

        verify(torController).setAllowedExitNodesCountries(any());
    }

    private void assertFamDisabled(Device device) {
        verify(parentalControlService).createDefaultProfile();
        verify(userService).restoreDefaultSystemUser(any(), anyInt());

        assertEquals(1, device.getAssignedUser());
        assertEquals(1, device.getOperatingUser());
    }

    private DeviceRegistrationInfo deviceRegistrationInfo(String[] features) {
        ProductInfo prodInfo = new ProductInfo("prod-id-old", "prod-name-old", features);
        DeviceRegistrationInfo dri = Mockito.mock(DeviceRegistrationInfo.class);
        Mockito.when(dri.getProductInfo()).thenReturn(prodInfo);

        return dri;
    }
}

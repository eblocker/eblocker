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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationInfo;
import org.eblocker.server.common.registration.LicenseExpirationService;
import org.eblocker.server.common.ssl.SslService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.registration.ProductFeature;
import org.eblocker.registration.UpsellInfoWrapper;
import com.google.inject.Inject;

@Singleton
public class ProductMigrationService {
    private static final Logger log = LoggerFactory.getLogger(ProductMigrationService.class);
    private final DeviceRegistrationClient deviceRegistrationClient;

    private Set<ProductFeature> relevantFeatures = new HashSet<>(Arrays.asList(
        ProductFeature.FAM, ProductFeature.PRO, ProductFeature.BAS
    ));

    private DeviceService deviceService;
    private SslService sslService;
    private TorController torController;
    private EblockerDnsServer dnsServer;
    private AnonymousService anonymousService;
    private UserService userService;
    private ParentalControlService parentalControlService;
    private ProductInfoService productInfoService;

    @Inject
    public ProductMigrationService(
        DeviceService deviceService,
        SslService sslService,
        TorController torController,
        EblockerDnsServer dnsServer,
        DeviceRegistrationClient deviceRegistrationClient,
        AnonymousService anonymousService,
        UserService userService,
        ParentalControlService parentalControlService,
        LicenseExpirationService licenseExpirationService,
        ProductInfoService productInfoService)
    {
        this.deviceService = deviceService;
        this.sslService = sslService;
        this.torController = torController;
        this.dnsServer = dnsServer;
        this.deviceRegistrationClient = deviceRegistrationClient;
        this.anonymousService = anonymousService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.productInfoService = productInfoService;

        licenseExpirationService.addListener(this::licenseExpired);
    }

    private boolean isDowngrade(DeviceRegistrationInfo oldLicense, DeviceRegistrationInfo newLicense) {
        Set<String> oldFeatures = new HashSet<>(Arrays.asList(oldLicense.getProductInfo().getProductFeatures()));
        // Can be null right after resetting
        Set<String> newFeatures;
        if (newLicense.getProductInfo() == null) {
            newFeatures = Collections.emptySet();
        } else {
            newFeatures = new HashSet<>(Arrays.asList(newLicense.getProductInfo().getProductFeatures()));
        }

        // If any relevant feature...
        for (ProductFeature feature : relevantFeatures) {
            // ...that was available...
            if (oldFeatures.contains(feature.name())
                    // ...and is not available anymore...
                    && !newFeatures.contains(feature.name())) {
                // ...it is a downgrade
                return true;
            }
        }
        // No relevant feature removed, no downgrade
        return false;
    }

    private Set<ProductFeature> getRemovedFeatures(DeviceRegistrationInfo oldLicense, DeviceRegistrationInfo newLicense) {
        Set<ProductFeature> result = new HashSet<>();

        Set<String> oldFeatures = new HashSet<>(Arrays.asList(oldLicense.getProductInfo().getProductFeatures()));
        // Can be null right after resetting
        Set<String> newFeatures;
        if (newLicense.getProductInfo() == null) {
            newFeatures = Collections.emptySet();
        } else {
            newFeatures = new HashSet<>(Arrays.asList(newLicense.getProductInfo().getProductFeatures()));
        }

        // If any relevant feature...
        for (ProductFeature feature : relevantFeatures) {
            // ...that was available...
            if (oldFeatures.contains(feature.name())
                    // ...and is not available anymore...
                    && !newFeatures.contains(feature.name())) {
                // ...it is a removed feature
                result.add(feature);
            }
        }
        return result;
    }

    public void changeProduct(DeviceRegistrationInfo oldLicense, DeviceRegistrationInfo newLicense) {
        if (isDowngrade(oldLicense, newLicense)) {
            Set<ProductFeature> removedFeatures = getRemovedFeatures(oldLicense, newLicense);
            if (removedFeatures.contains(ProductFeature.FAM)) {
                changeProductDeactivateFamilyFeatures();
            }
            if (removedFeatures.contains(ProductFeature.PRO)) {
                changeProductDeactivateProFeatures();
            }
            if (removedFeatures.contains(ProductFeature.BAS)) {
                changeProductDeactivateBaseFeatures();
            }
            // FUTURE: more checks for other downgrade paths.
        }
        // FUTURE: also check for upgrades.
    }

    private void changeProductDeactivateFamilyFeatures() {
        // Restore default profile
        parentalControlService.createDefaultProfile();

        // For all devices, set default profile and default system user
        for (Device device : deviceService.getDevices(true)) {

            // Make sure default system user still references default profile
            // (by restoring default values)
            UserModule defaultSystemUser = userService.restoreDefaultSystemUser(device.getId(), device.getDefaultSystemUser());
            device.setDefaultSystemUser(defaultSystemUser.getId());
            device.setOperatingUser(defaultSystemUser.getId());
            device.setAssignedUser(defaultSystemUser.getId());
            deviceService.updateDevice(device);
        }
    }

    public UpsellInfoWrapper getUpsellInfo(String feature) {
        return deviceRegistrationClient.getUpsellInfo(feature);
    }

    private void changeProductDeactivateProFeatures() {
        // Deactivate SSL
        sslService.disableSsl();
    }

    private void changeProductDeactivateBaseFeatures() {
        // Adjustments to devices
        for (Device device : deviceService.getDevices(true)) {
            // Assigning device to no specific user already done when
            // downgrading from FAM feature
            // Disable TOR/VPN
            anonymousService.disableTor(device);
            anonymousService.disableVpn(device);
            // Every device can display messages
            device.setMessageShowAlert(true);
            device.setMessageShowInfo(true);

            // Store changes made
            deviceService.updateDevice(device);
        }

        // Reset TOR (no countries allowed means all are allowed)
        try {
            torController.setAllowedExitNodesCountries(Collections.emptySet());
        } catch (Exception e) {
            log.warn("Setting allowed tor exit nodes failed.", e);
        }

        // DNS stays active, settings of external servers are preserved, mode is
        // reset to default, i.e. "use standard settings from local network".
        DnsResolvers dnsConfig = dnsServer.getDnsResolvers();
        dnsConfig.setDefaultResolver("dhcp");
        dnsConfig.setCustomResolverMode("default");
        dnsServer.setDnsResolvers(dnsConfig);
    }

    private synchronized void licenseExpired() {
        log.info("licenseExpirationChange event occured");

        try {
            if (productInfoService.hasFeature(ProductFeature.BAS) && productInfoService.hasFeature(ProductFeature.EVL_BAS)) {
                productInfoService.removeFeature(ProductFeature.BAS);
                changeProductDeactivateBaseFeatures();
            }

            if (productInfoService.hasFeature(ProductFeature.PRO) && productInfoService.hasFeature(ProductFeature.EVL_PRO)) {
                productInfoService.removeFeature(ProductFeature.PRO);
                changeProductDeactivateProFeatures();
            }

            if (productInfoService.hasFeature(ProductFeature.FAM) && productInfoService.hasFeature(ProductFeature.EVL_FAM)) {
                productInfoService.removeFeature(ProductFeature.FAM);
                changeProductDeactivateFamilyFeatures();
            }
        }
        catch (Exception e) {
            log.error("Downgrading expired license failed.", e);
        }
    }
}

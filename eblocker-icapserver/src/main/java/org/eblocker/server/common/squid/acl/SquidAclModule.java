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
package org.eblocker.server.common.squid.acl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import org.eblocker.server.common.util.Ip4Utils;
import org.eblocker.server.http.service.DeviceService;

public class SquidAclModule extends AbstractModule {

    @Provides
    @Named("squid.acl.disabled.clients")
    public SquidAcl disabledClientsAcl(@Named("squid.disabled.acl.file.path") String path,
                                       DeviceService deviceService) {
        return new DevicePredicateFilterAcl(path, deviceService, device -> !device.isEnabled());
    }

    @Provides
    @Named("squid.acl.mobile.clients")
    public SquidAcl mobileClientsAcl(@Named("squid.mobile.acl.file.path") String path,
                                     DeviceService deviceService,
                                     @Named("network.vpn.subnet.ip") String vpnSubnet,
                                     @Named("network.vpn.subnet.netmask") String vpnNetmask) {
        return new DevicePredicateFilterAcl(
                path, deviceService,
                device -> device.isEnabled() && device.isVpnClient(),
                ip -> ip.isIpv4() && Ip4Utils.isInSubnet(ip.toString(), vpnSubnet, vpnNetmask));
    }

    @Provides
    @Named("squid.acl.mobile.clients.private.network.access")
    public SquidAcl mobileClientsPrivateNetworkAccessAcl(@Named("squid.mobile.private.network.access.acl.file.path") String path,
                                                         DeviceService deviceService,
                                                         @Named("network.vpn.subnet.ip") String vpnSubnet,
                                                         @Named("network.vpn.subnet.netmask") String vpnNetmask) {
        return new DevicePredicateFilterAcl(
                path, deviceService,
                device -> device.isEnabled() && device.isVpnClient() && device.isMobilePrivateNetworkAccess(),
                ip -> ip.isIpv4() && Ip4Utils.isInSubnet(ip.toString(), vpnSubnet, vpnNetmask));
    }

    @Provides
    @Named("squid.acl.ssl.clients")
    public SquidAcl sslClientsAcl(@Named("squid.ssl.acl.file.path") String path,
                                  DeviceService deviceService) {
        return new DevicePredicateFilterAcl(path, deviceService, device -> device.isEnabled() && device.isSslEnabled());
    }

    @Provides
    @Named("squid.acl.tor.clients")
    public SquidAcl torClientsAcl(@Named("squid.tor.acl.file.path") String path,
                                  DeviceService deviceService) {
        return new DevicePredicateFilterAcl(path, deviceService, device -> device.isEnabled() && device.isUseAnonymizationService() && device.isRoutedThroughTor());
    }

    @Provides
    @Named("squid.acl.filtered.clients")
    public ConfigurableDeviceFilterAcl filteredClientsAcl(@Named("parentalcontrol.filtered.devices.file.path") String path,
                                                          DeviceService deviceService) {
        return new ConfigurableDeviceFilterAcl(path, deviceService);
    }

    @Override
    protected void configure() {
        install(new FactoryModuleBuilder().build(ConfigurableDeviceFilterAclFactory.class));
    }
}

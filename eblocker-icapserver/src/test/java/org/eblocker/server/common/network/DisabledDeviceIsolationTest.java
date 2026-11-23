/*
 * Copyright 2026
 */
package org.eblocker.server.common.network;

import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisementFactory;
import org.eblocker.server.common.network.unix.firewall.Action;
import org.eblocker.server.common.network.unix.firewall.IpAddressFilter;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorIp6;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorTestBase;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * End-to-end verification of IPv6 disabled-device isolation across IPv6 firewall rules,
 * unicast Router Advertisements, Router Solicitation filtering, and dashboard/DNS reachability.
 */
public class DisabledDeviceIsolationTest extends TableGeneratorTestBase {

    @Test
    public void testIp6DisabledDeviceAndPrivacyAddressesAreIsolatedFromDnsProxyAndMasquerade() {
        TableGeneratorIp6 gen = new TableGeneratorIp6(
                standardInterface, mobileVpnInterface, httpPort, httpsPort,
                proxyPort, proxyHTTPSPort, localDnsPort, "malware6", torPort, torDnsPort, torMark);

        Device enabledPc = createLanDevice("001122334455", true, false, false, "192.168.1.42", "fe80::aa:bb:cc:dd", "2a00:1111::ea:b1ed");
        Device disabledPc = createLanDevice("66778899aabb", false, false, false, "192.168.1.41", "fe80::11:22:33:44", "2a00:1111::d15a:b1ed");

        ParentalControlAccessRestrictionsService restrictions = Mockito.mock(ParentalControlAccessRestrictionsService.class);
        Mockito.when(restrictions.isAccessPermitted(Mockito.any())).thenReturn(true);
        deviceIpFilter = new IpAddressFilter(Set.of(enabledPc, disabledPc), IpAddress::isIpv6, restrictions);
        anonVpnClients = Set.of();

        gen.setOwnIpAddress("fe80::eb10");
        gen.setDnsEnabled(true);
        gen.setSslEnabled(true);
        gen.setPrefixes(Set.of("2a00:1111::/64"));
        createTablesAndSimulators(gen);
        natPre.setInput(standardInterface);
        natPost.setOutput(standardInterface);

        // Known disabled device IPv6: no external DNS redirect, no Squid HTTP redirect, no masquerading
        Assert.assertEquals(Action.returnFromChain(), natPre.udpPacket("2a00:1111::d15a:b1ed", "2a00:eeee::4321", 53));
        Assert.assertEquals(Action.returnFromChain(), natPre.tcpPacket("2a00:1111::d15a:b1ed", "2a00:eeee::4321", 80));
        Assert.assertEquals(Action.returnFromChain(), natPost.tcpPacket("2a00:1111::d15a:b1ed", "2a00:eeee::4321", 1234));

        // Untracked temporary RFC 8981 privacy IPv6 from disabled device: no external DNS redirect, no Squid HTTP redirect, no masquerading
        String untrackedPrivacyIp = "2a00:1111::dead:beef";
        Assert.assertEquals(Action.returnFromChain(), natPre.udpPacket(untrackedPrivacyIp, "2a00:eeee::4321", 53));
        Assert.assertEquals(Action.returnFromChain(), natPre.tcpPacket(untrackedPrivacyIp, "2a00:eeee::4321", 80));
        Assert.assertEquals(Action.returnFromChain(), natPost.tcpPacket(untrackedPrivacyIp, "2a00:eeee::4321", 1234));

        // Explicit DNS/HTTP/HTTPS directly to eBlocker IPv6 link-local still works
        Assert.assertEquals(Action.redirectTo("fe80::eb10", localDnsPort), natPre.udpPacket("2a00:1111::d15a:b1ed", "fe80::eb10", 53));
        Assert.assertEquals(Action.redirectTo("fe80::eb10", httpPort), natPre.tcpPacket("2a00:1111::d15a:b1ed", "fe80::eb10", 80));

        // Enabled device IPv6 is still protected and masqueraded
        Assert.assertEquals(Action.redirectTo(localDnsPort), natPre.udpPacket("2a00:1111::ea:b1ed", "2a00:eeee::4321", 53));
        Assert.assertEquals(Action.redirectTo("fe80::eb10", proxyPort), natPre.tcpPacket("2a00:1111::ea:b1ed", "2a00:eeee::4321", 80));
        Assert.assertEquals(Action.masquerade(), natPost.tcpPacket("2a00:1111::ea:b1ed", "2a00:eeee::4321", 1234));
    }

    @Test
    public void testIp6RouterAdvertiserAndNeighborDiscoveryIsolateDisabledDevices() {
        FeatureToggleRouter featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        Mockito.when(featureToggleRouter.shouldSendRouterAdvertisements()).thenReturn(true);

        NetworkInterfaceWrapper networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.isUp()).thenReturn(true);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[]{0, 1, 2, 3, 4, 5});
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::1:2:3:4:5"));
        Mockito.when(networkInterface.getMtu()).thenReturn(1500);
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(
                Ip6Address.parse("fe80::1:2:3:4:5"),
                Ip4Address.parse("10.12.34.5"),
                Ip6Address.parse("2000::1:2:3:4:5")));

        Device enabledPc = createLanDevice("001122334455", true, false, false, "10.12.34.42", "fe80::aa:bb:cc:dd", "2000::42");
        Device disabledPc = createLanDevice("66778899aabb", false, false, false, "10.12.34.41", "fe80::11:22:33:44", "2000::41");
        Device gateway = createLanDevice("102030405060", true, true, false, "10.12.34.1", "fe80::1", "2000::1");
        Device eblocker = createLanDevice("000102030405", true, false, true, "10.12.34.5", "fe80::1:2:3:4:5", "2000::1:2:3:4:5");

        DeviceService deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDevices(false)).thenReturn(Arrays.asList(enabledPc, disabledPc, gateway, eblocker));
        Mockito.when(deviceService.getDeviceById(enabledPc.getId())).thenReturn(enabledPc);
        Mockito.when(deviceService.getDeviceById(disabledPc.getId())).thenReturn(disabledPc);

        List<String> published = new ArrayList<>();
        final Subscriber[] ndSubscriber = new Subscriber[1];
        PubSubService pubSub = new PubSubService() {
            @Override public void subscribeAndLoop(String channel, Subscriber subscriber) { ndSubscriber[0] = subscriber; }
            @Override public void publish(String channel, String message) { published.add(message); }
            @Override public void unsubscribe(Subscriber subscriber) {}
        };

        RouterAdvertisementCache cache = Mockito.mock(RouterAdvertisementCache.class);
        RouterAdvertisementFactory factory = new RouterAdvertisementFactory(
                RouterAdvertisement.RouterPreference.HIGH, 120, 120, cache, networkInterface);

        // 1. Periodic RAs must be unicast only to enabled non-gateway non-eBlocker devices
        Ip6RouterAdvertiser advertiser = new Ip6RouterAdvertiser(featureToggleRouter, networkInterface, pubSub, factory, deviceService);
        advertiser.advertise();

        Assert.assertEquals(1, published.size());
        Assert.assertTrue(published.get(0).contains("/001122334455/fe8000000000000000aa00bb00cc00dd/"));
        Assert.assertFalse(published.get(0).contains("333300000001"));
        Assert.assertFalse(published.get(0).contains("66778899aabb"));

        // 2. Deprecation on disable sends zero routerLifetime and zero RDNSS lifetime to disabled device only
        published.clear();
        advertiser.deprecate(disabledPc);
        Assert.assertEquals(1, published.size());
        Assert.assertEquals("000102030405/fe800000000000010002000300040005/66778899aabb/fe800000000000000011002200330044/icmp6/134/255/0/0/0/1/0/0/0/1/000102030405/25/0/1/fe800000000000010002000300040005/5/1500",
                published.get(0));

        // 3. Router Solicitation from disabled device must not be answered
        published.clear();
        NeighborDiscoveryListener listener = new NeighborDiscoveryListener(
                new IpResponseTable(), new TestClock(ZonedDateTime.now()), Mockito.mock(DeviceIpUpdater.class),
                featureToggleRouter, networkInterface, pubSub, cache, Mockito.mock(Ip6AddressDelayedValidator.class),
                factory, deviceService);
        listener.run();
        ndSubscriber[0].process("66778899aabb/fe800000000000000011002200330044/333300000002/ff020000000000000000000000000002/icmp6/133");
        Assert.assertEquals(0, published.size());

        // 4. Router Solicitation from enabled device IS answered
        ndSubscriber[0].process("001122334455/fe8000000000000000aa00bb00cc00dd/333300000002/ff020000000000000000000000000002/icmp6/133");
        Assert.assertEquals(1, published.size());
        Assert.assertTrue(published.get(0).contains("/001122334455/fe8000000000000000aa00bb00cc00dd/"));
    }

    private Device createLanDevice(String hwHex, boolean enabled, boolean gateway, boolean eblocker,
                                 String ip4, String linkLocalIp6, String globalIp6) {
        Device device = new Device();
        device.setId(Device.ID_PREFIX + hwHex);
        device.setEnabled(enabled);
        device.setIsGateway(gateway);
        device.setIsEblocker(eblocker);
        device.setIpAddresses(Arrays.asList(
                Ip4Address.parse(ip4),
                Ip6Address.parse(linkLocalIp6),
                Ip6Address.parse(globalIp6)));
        return device;
    }
}

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
package org.eblocker.server.common.network.unix;

import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.MockDataSource;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.mockito.Mockito.when;

public class FirewallConfigurationTest {
	private FirewallConfiguration configuration;
	private MockDataSource dataSource;
	private ParentalControlAccessRestrictionsService restrictionsService;
	private String malwareIpSet;
	private Environment environment;
	private File configFullFile;
	private File configDeltaFile;
	private NetworkServices networkServices;

	@Before
	public void setUp() throws Exception {
		configFullFile = File.createTempFile("firewall", ".conf");
		configFullFile.deleteOnExit();

		configDeltaFile = File.createTempFile("firewall", ".conf.delta");
		configDeltaFile.deleteOnExit();

		dataSource = new MockDataSource();
		dataSource.setGateway("192.168.0.1");
		environment = Mockito.mock(Environment.class);
		Mockito.when(environment.isServer()).thenReturn(false);

		malwareIpSet = "unit-test";

		networkServices = createNetworkServicesMock("10.8.0.1");
		restrictionsService = createAccessRestrictionsMock();
		configuration = new FirewallConfiguration(
				configFullFile.toString(),
				configDeltaFile.toString(),
				"eth2",
				"tun33",
				"10.8.0.0",
				"255.255.255.0",
				1234,
				3333,
				12345,
				"169.254.7.53/32",
				"10.0.0.0/8",
				"172.16.0.0/12",
				"192.168.0.0/16",
                "169.254.0.0/16",
                13,
                3000,
                3443,
                "169.254.93.109",
                3003,
                3004,
                "139.59.206.208",
				malwareIpSet,
				1194,
				networkServices,
				dataSource,
				restrictionsService,
				environment
		);
	}

	private NetworkServices createNetworkServicesMock(String vpnIpAddress) {
		NetworkConfiguration networkConfiguration = Mockito.mock(NetworkConfiguration.class);
		when(networkConfiguration.getIpAddress()).thenReturn("192.168.0.10");
		when(networkConfiguration.getNetworkMask()).thenReturn("255.255.255.0");
		when(networkConfiguration.getVpnIpAddress()).thenReturn(vpnIpAddress);
		NetworkServices networkServices = Mockito.mock(NetworkServices.class);
		when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfiguration);
		return networkServices;
	}

	private ParentalControlAccessRestrictionsService createAccessRestrictionsMock() {
		ParentalControlAccessRestrictionsService enforcerService = Mockito.mock(ParentalControlAccessRestrictionsService.class);
		when(enforcerService.isAccessPermitted(Mockito.any())).thenReturn(true);
		return enforcerService;
	}

	@Test
	public void testEnable() throws IOException {
		configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> true);

		assertEqualContent("test-data/firewall.conf", configFullFile);
	}

	@Test
	public void testMasquerade() throws IOException {
		configuration.enable(new HashSet<>(), new HashSet<>(), true, true, false, true, true, () -> true);

        assertEqualContent("test-data/firewall-masquerade.conf", configFullFile);
	}

	@Test
	public void testDiff() throws IOException {
		configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall.conf", configFullFile);
		configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall-no-changes.conf", configDeltaFile);
	}

    @Test
    public void testApplyFailure() throws IOException {
        configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> false);
        assertEqualContent("test-data/firewall.conf", configFullFile);

        configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall.conf", configFullFile);

        configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> false);
        assertEqualContent("test-data/firewall-no-changes.conf", configDeltaFile);

        configuration.enable(new HashSet<>(), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall-no-changes.conf", configDeltaFile);
    }

	@Test
	public void testDiffSsl() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa25e78b8602", "192.168.0.22", false);
        Device b = TestDeviceFactory.createDevice("bb1122334455", "192.168.0.23", false);
        Device c = TestDeviceFactory.createDevice("cc11223344ff", "192.168.0.24",true);
        Device d = TestDeviceFactory.createDevice("dd11223344ff", Arrays.asList("192.168.0.25", "10.8.0.5"), true);

        //activate SSL for the devices c and d
        c.setSslEnabled(true);
        d.setSslEnabled(true);

        Set<Device> allDevices = new TreeSet<>(Comparator.comparing(Device::getHardwareAddress));
        allDevices.addAll(Arrays.asList(a,b,c,d));
        configuration.enable(allDevices, new HashSet<>(), false, true, false, true, true, () -> true);

		// create initial fw-config
		configuration.enable(allDevices, new HashSet<>(), false, true, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-excluded-devices.conf", configFullFile);

        // disable ssl and check delta
		configuration.enable(allDevices, new HashSet<>(), false, false, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-diff-ssl-disabled.conf", configDeltaFile);

        // re-enable ssl and check delta
		configuration.enable(allDevices, new HashSet<>(), false, true, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-diff-ssl-enabled.conf", configDeltaFile);

        // switch device a <-> c and  b <-> d and check delta
        a.setSslEnabled(true);
        a.setEnabled(true);
        b.setSslEnabled(true);
        b.setEnabled(true);
        c.setSslEnabled(false);
        c.setEnabled(false);
        d.setSslEnabled(false);
        d.setEnabled(false);
        configuration.enable(allDevices, new HashSet<>(), false, true, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-diff-ssl-switch.conf", configDeltaFile);
	}

	@Test
	public void testEnableWithExcludedDevices() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa25e78b8602", "192.168.0.22", false);
		Device b = TestDeviceFactory.createDevice("bb1122334455", "192.168.0.23", false);
		Device c = TestDeviceFactory.createDevice("cc11223344ff", "192.168.0.24", true);
		Device d = TestDeviceFactory.createDevice("dd11223344ff", Arrays.asList("192.168.0.25", "10.8.0.5"), true);

        //activate SSL for the devices c and d
		c.setSslEnabled(true);
		d.setSslEnabled(true);

        Set<Device> allDevices = new TreeSet<>(Comparator.comparing(Device::getHardwareAddress));
        allDevices.addAll(Arrays.asList(a,b,c,d));
		configuration.enable(allDevices, new HashSet<>(), false, true, false, true, true, () -> true);

		assertEqualContent("test-data/firewall-excluded-devices.conf", configFullFile);
	}

	@Test
    public void testWithDevicesWithoutIpAddresses() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa25e78b8602", (String) null, true);
        Device b = TestDeviceFactory.createDevice("bb1122334455", (String) null, false);
        Device c = TestDeviceFactory.createDevice("cc11223344ff", (String) null, true);
        Device d = TestDeviceFactory.createDevice("dd11223344ff", (String) null, false);

        //activate SSL for the devices c and d
        c.setSslEnabled(true);
        d.setSslEnabled(true);

        Set<Device> allDevices = new TreeSet<>(Comparator.comparing(Device::getHardwareAddress));
        allDevices.addAll(Arrays.asList(a,b,c,d));
        configuration.enable(allDevices, new HashSet<>(), false, true, false, false, true, () -> true);

        assertEqualContent("test-data/firewall.conf", configFullFile);
    }

	@Test
	public void testEnabledVPNProfile() throws IOException{
		VpnProfile profile = new OpenVpnProfile(1,"OpenVPN profile 1");
        dataSource.addVPNProfile(profile);

        OpenVpnClientState client = new OpenVpnClientState();
        client.setId(1);
        client.setState(OpenVpnClientState.State.ACTIVE);
        client.setVirtualInterfaceName("tun0");
        client.setLinkLocalIpAddress("169.254.8.1");
		client.setRoute(1);

		configuration.enable(new HashSet<>(), Collections.singleton(client), false, true, false, false, true, () -> true);

        assertEqualContent("test-data/firewall-active-vpnprofile-noclients.conf", configFullFile);

		dataSource.removeAllVPNProfiles();
	}

	@Test
	public void testActiveVPNProfileWithOneClient() throws IOException{
        VpnProfile profile = new OpenVpnProfile(1,"OpenVPN profile 1");
        dataSource.addVPNProfile(profile);

        OpenVpnClientState client = new OpenVpnClientState();
        client.setId(1);
        client.setState(OpenVpnClientState.State.ACTIVE);
        client.setVirtualInterfaceName("tun0");
        client.setLinkLocalIpAddress("169.254.8.1");
		client.setRoute(1);


		Device device = TestDeviceFactory.createDevice("aa25e78b8602", "192.168.0.22", true);
		client.setDevices(Collections.singleton(device.getId()));

		configuration.enable(Collections.singleton(device), Collections.singleton(client), false, true, false, false, true, () -> true);

        assertEqualContent("test-data/firewall-active-vpnprofile-oneclient.conf", configFullFile);

        dataSource.removeAllVPNProfiles();
	}

    @Test
    public void testActiveVPNProfileWithOneClientWithoutIp() throws IOException{
        VpnProfile profile = new OpenVpnProfile(1,"OpenVPN profile 1");
        dataSource.addVPNProfile(profile);

        OpenVpnClientState client = new OpenVpnClientState();
        client.setId(1);
        client.setState(OpenVpnClientState.State.ACTIVE);
        client.setVirtualInterfaceName("tun0");
        client.setLinkLocalIpAddress("169.254.8.1");
        client.setRoute(1);

        Device device = TestDeviceFactory.createDevice("aa25e78b8602", (String) null, true);
        client.setDevices(Collections.singleton(device.getId()));

        configuration.enable(Collections.singleton(device), Collections.singleton(client), false, true, false, false, true, () -> true);

        assertEqualContent("test-data/firewall-active-vpnprofile-oneclient-no-ip.conf", configFullFile);

        dataSource.removeAllVPNProfiles();
    }

    @Test
    public void testActiveVPNProfileWhileRestart() throws IOException{
        VpnProfile profile = new OpenVpnProfile(1,"OpenVPN profile 1");
        dataSource.addVPNProfile(profile);

        OpenVpnClientState client = new OpenVpnClientState();
        client.setId(1);
        client.setState(OpenVpnClientState.State.PENDING_RESTART);
        client.setVirtualInterfaceName("tun0");
        client.setLinkLocalIpAddress("169.254.8.1");
        client.setRoute(1);

        Device device = TestDeviceFactory.createDevice("aa25e78b8602", "192.168.0.22", true);
        client.setDevices(Collections.singleton(device.getId()));

        configuration.enable(Collections.singleton(device), Collections.singleton(client), false, true, false, false, true, () -> true);

        assertEqualContent("test-data/firewall-active-vpnprofile-restart.conf", configFullFile);

        dataSource.removeAllVPNProfiles();
    }

	@Test
	public void testStableIterationOrder() throws IOException {
	    // create some devices
        Device a = TestDeviceFactory.createDevice("aa25e78b8602", "192.168.0.22", true);
        Device b = TestDeviceFactory.createDevice("bb1122334455", "192.168.0.23", true);
        Device c = TestDeviceFactory.createDevice("cc11223344ff", "192.168.0.24",true);
        Device d = TestDeviceFactory.createDevice("dd11223344ff", "192.168.0.25",true);
        List<Device> devices = Arrays.asList(a, b, c, d);

        // create initial config and store it for later assertions
        configuration.enable(new LinkedHashSet<>(devices), Collections.emptyList(), false, true, false, false, true, () -> true);
        String expected =  FileUtils.readFileToString(configFullFile);

        // create all device permutations and check they all generate exact the same rules
        Collection<List<Device>> permutations = Collections2.permutations(devices);
        for(List<Device> permutation : permutations) {
            configuration.enable(new LinkedHashSet<>(permutation), Collections.emptyList(), false, true, false, false, true, () -> true);
            Assert.assertEquals(expected, FileUtils.readFileToString(configFullFile));
            assertEqualContent("test-data/firewall-no-changes.conf", configDeltaFile);
        }
    }

    @Test
    public void testEblockerMobileServerDisabled() throws EblockerException, IOException {
        List<Device> devices = setUpEblockerMobileTest();
        configuration.enable(new HashSet<>(devices), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall-server-openvpn-server-disabled.conf", configFullFile);
    }

	@Test
	public void testEblockerMobileServerEnabledButServerNotReadyYet_EB1_2251() throws EblockerException, IOException {
	    networkServices = createNetworkServicesMock(null);
	    List<Device> devices = setUpEblockerMobileTest();
	    configuration.enable(new LinkedHashSet<>(devices), new HashSet<>(), false, true, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-server-openvpn-server-disabled.conf", configFullFile);
	}

    @Test
    public void testEblockerMobileServerEnabled() throws EblockerException, IOException {
        List<Device> devices = setUpEblockerMobileTest();
        configuration.enable(new LinkedHashSet<>(devices), new HashSet<>(), false, true, false, true, true, () -> true);
        assertEqualContent("test-data/firewall-server-openvpn-server-enabled.conf", configFullFile);
    }

    private List<Device> setUpEblockerMobileTest() {
        Mockito.when(environment.isServer()).thenReturn(true);

        configuration = new FirewallConfiguration(
            configFullFile.toString(),
            configDeltaFile.toString(),
            "eth2",
            "tun33",
            "10.8.0.0",
            "255.255.255.0",
            1234,
            3333,
            12345,
            "169.254.7.53/32",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            13,
            3000,
            3443,
            "169.254.93.109",
            3003,
            3004,
            "139.59.206.208",
            malwareIpSet,
            1194,
            networkServices,
            dataSource,
            restrictionsService,
            environment
        );

        Device a = TestDeviceFactory.createDevice("aa11223344ff", "192.168.0.21", true);
        Device b = TestDeviceFactory.createDevice("bb11223344ff", "192.168.0.22", false);
        Device c = TestDeviceFactory.createDevice("cc11223344ff", "10.8.0.2", true);
        Device d = TestDeviceFactory.createDevice("cc11223344ff", "10.8.0.3", false);

        c.setIsVpnClient(true);
        d.setIsVpnClient(true);

        return Arrays.asList(a, b, c);
    }

	@Test
    public void testTorClientsDnsEnabled() throws IOException {
        Device a = createTorDevice("aa25e78b8602", "192.168.0.22", true, true, true);
        Device b = createTorDevice("bb1122334455", "192.168.0.23", true, true, false);
        Device c = createTorDevice("cc11223344ff", "192.168.0.25", false, true, true);
        List<Device> devices = Arrays.asList(a, b, c);

        configuration.enable(new LinkedHashSet<>(devices), new HashSet<>(), false, false, true, false, false, () -> true);

        assertEqualContent("test-data/firewall-tor-dns-enabled.conf", configFullFile);
    }

    @Test
    public void testTorClientsDnsDisabled() throws IOException {
        Device a = createTorDevice("aa25e78b8602", "192.168.0.22", true, true, true);
        Device b = createTorDevice("bb1122334455", "192.168.0.23", true, true, false);
        Device c = createTorDevice("cc11223344ff", "192.168.0.25", false, true, true);
        List<Device> devices = Arrays.asList(a, b, c);

        configuration.enable(new LinkedHashSet<>(devices), new HashSet<>(), false, false, false, false, false, () -> true);

        assertEqualContent("test-data/firewall-tor-dns-disabled.conf", configFullFile);
    }

    @Test
    public void testTorMobileClients() throws IOException {
        Device a = createTorDevice("aa25e78b8602", Arrays.asList("192.168.0.22", "10.8.0.22"), true, true, true);
        Device b = createTorDevice("bb1122334455", Arrays.asList("192.168.0.23", "10.8.0.23"), true, true, false);
        Device c = createTorDevice("cc11223344ff", Arrays.asList("192.168.0.25", "10.8.0.25"), false, true, true);
        List<Device> devices = Arrays.asList(a, b, c);
        devices.forEach(d -> d.setIsVpnClient(true));

        configuration.enable(new LinkedHashSet<>(devices), new HashSet<>(), false, false, true, true, false, () -> true);

        assertEqualContent("test-data/firewall-tor-mobile.conf", configFullFile);
    }

    @Test
    public void testMalwareSetEnabled() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa11223344ff", "192.168.0.21", true);
        Device b = TestDeviceFactory.createDevice("bb11223344ff", "192.168.0.22", true);
        a.setMalwareFilterEnabled(true);
        b.setMalwareFilterEnabled(false);
        configuration.enable(Sets.newHashSet(a, b), new HashSet<>(), false, true, false, false, true, () -> true);
        assertEqualContent("test-data/firewall-malware-set-enabled.conf", configFullFile);
    }

    @Test
    public void testMalwareSetDisabled() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa11223344ff", "192.168.0.21", true);
        Device b = TestDeviceFactory.createDevice("bb11223344ff", "192.168.0.22", true);
        a.setMalwareFilterEnabled(true);
        b.setMalwareFilterEnabled(false);
        configuration.enable(Sets.newHashSet(a, b), new HashSet<>(), false, true, false, false, false, () -> true);
        assertEqualContent("test-data/firewall-malware-set-disabled.conf", configFullFile);
    }

    @Test
    public void testMobileClientsPrivateNetworkAccess() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa11223344ff", "192.168.0.21", true);
        Device b = TestDeviceFactory.createDevice("bb11223344ff", "192.168.0.22", true);
        a.setMobilePrivateNetworkAccess(false);
        a.setIsVpnClient(true);
        a.setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.21"), IpAddress.parse("10.8.0.2")));
        b.setMobilePrivateNetworkAccess(true);
        b.setIsVpnClient(true);
        b.setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.21"), IpAddress.parse("10.8.0.3")));
        configuration.enable(Sets.newHashSet(a, b), Collections.emptySet(), false, true, true, true, false, () -> true);
        assertEqualContent("test-data/firewall-mobile-private-network.conf", configFullFile);
    }

    @Test
    public void testAccessRestrictedDevices() throws IOException {
        Device a = TestDeviceFactory.createDevice("aa11223344ff", "192.168.0.21", true);
        Device b = TestDeviceFactory.createDevice("bb11223344ff", "192.168.0.22", false);
        Mockito.when(restrictionsService.isAccessPermitted(a)).thenReturn(false);
        Mockito.when(restrictionsService.isAccessPermitted(b)).thenReturn(false);
        configuration.enable(Sets.newHashSet(a, b), Collections.emptySet(), false, false, false, false, false, () -> true);
        assertEqualContent("test-data/firewall-access-restrictions.conf", configFullFile);
    }

	private void assertEqualContent(String expectedClassPathResource, File actualFile) throws IOException {
		String expected = IOUtils.toString(ClassLoader.getSystemResource(expectedClassPathResource));
		String actual = FileUtils.readFileToString(actualFile);
		Assert.assertEquals(expected, actual);
	}

    private Device createTorDevice(String hwAddress, String ipAddress, boolean enabled, boolean useAnonymizationService, boolean routeThroughTor) {
        return createTorDevice(hwAddress, Collections.singletonList(ipAddress), enabled, useAnonymizationService, routeThroughTor);
    }

    private Device createTorDevice(String hwAddress, List<String> ipAddresses, boolean enabled, boolean useAnonymizationService, boolean routeThroughTor) {
        Device device = TestDeviceFactory.createDevice(hwAddress, ipAddresses, enabled);
        device.setUseAnonymizationService(useAnonymizationService);
        device.setRouteThroughTor(routeThroughTor);
        return device;
    }
}

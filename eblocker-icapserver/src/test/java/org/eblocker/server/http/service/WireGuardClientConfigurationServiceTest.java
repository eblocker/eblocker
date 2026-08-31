package org.eblocker.server.http.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointType;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardClientConfigurationServiceTest {

    private static final String PRIVATE_KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String PEER_PUBLIC_KEY =
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=";

    private static final String PRESHARED_KEY =
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=";

    private static final String SERVER_PUBLIC_KEY =
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=";

    private DataSource dataSource;
    private DynDnsService dynDnsService;
    private WireGuardServerControlService controlService;

    private WireGuardClientConfigurationService service;

    @Before
    public void setUp() {
        dataSource =
                Mockito.mock(DataSource.class);

        dynDnsService =
                Mockito.mock(DynDnsService.class);

        controlService =
                Mockito.mock(
                        WireGuardServerControlService.class
                );

        service =
                new WireGuardClientConfigurationService(
                        dataSource,
                        dynDnsService,
                        controlService
                );

        Mockito.when(
                controlService.getPublicKey()
        ).thenReturn(SERVER_PUBLIC_KEY);
    }

    @Test
    public void rendersIpv4FullTunnelConfigWithoutDnsOrIpv6() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        7
                )
        ).thenReturn(peer(
                7,
                "10.13.13.7/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.FIXED_IP,
                        "203.0.113.42"
                )
        );

        String config =
                service.renderClientConfig(7);

        assertTrue(
                config.contains(
                        "PrivateKey = " + PRIVATE_KEY
                )
        );

        assertTrue(
                config.contains(
                        "Address = 10.13.13.7/32"
                )
        );

        assertTrue(
                config.contains(
                        "PublicKey = " + SERVER_PUBLIC_KEY
                )
        );

        assertTrue(
                config.contains(
                        "PresharedKey = " + PRESHARED_KEY
                )
        );

        assertTrue(
                config.contains(
                        "Endpoint = 203.0.113.42:51820"
                )
        );

        assertTrue(
                config.contains(
                        "AllowedIPs = 0.0.0.0/0"
                )
        );

        assertTrue(
                config.contains(
                        "PersistentKeepalive = 25"
                )
        );

        assertFalse(
                config.contains("DNS =")
        );

        assertFalse(
                config.contains("::/0")
        );

        assertFalse(
                config.contains("YOUR_DDNS_OR_IP")
        );
    }

    @Test
    public void qrPngContainsExactCanonicalClientConfig()
            throws Exception {

        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        7
                )
        ).thenReturn(peer(
                7,
                "10.13.13.7/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.FIXED_IP,
                        "203.0.113.42"
                )
        );

        String expected =
                service.renderClientConfig(7);

        byte[] png =
                service.renderClientConfigQrPng(7);

        assertTrue(png.length > 8);

        assertEquals(
                (byte) 0x89,
                png[0]
        );

        assertEquals(
                (byte) 0x50,
                png[1]
        );

        assertEquals(
                (byte) 0x4e,
                png[2]
        );

        assertEquals(
                (byte) 0x47,
                png[3]
        );

        BufferedImage image =
                ImageIO.read(
                        new ByteArrayInputStream(png)
                );

        assertTrue(image != null);

        BinaryBitmap bitmap =
                new BinaryBitmap(
                        new HybridBinarizer(
                                new BufferedImageLuminanceSource(
                                        image
                                )
                        )
                );

        Result decoded =
                new MultiFormatReader().decode(bitmap);

        assertEquals(
                expected,
                decoded.getText()
        );

        assertFalse(
                decoded.getText().contains("DNS =")
        );

        assertFalse(
                decoded.getText().contains("::/0")
        );
    }

    @Test
    public void rendersConfiguredHostnameEndpoint() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        2
                )
        ).thenReturn(peer(
                2,
                "10.13.13.2/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.DYN_DNS,
                        "vpn.example.org"
                )
        );

        String config =
                service.renderClientConfig(2);

        assertTrue(
                config.contains(
                        "Endpoint = vpn.example.org:51820"
                )
        );
    }

    @Test
    public void usesEnabledEblockerDynDns() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        3
                )
        ).thenReturn(peer(
                3,
                "10.13.13.3/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.EBLOCKER_DYN_DNS,
                        null
                )
        );

        Mockito.when(
                dynDnsService.isEnabled()
        ).thenReturn(true);

        Mockito.when(
                dynDnsService.getHostname()
        ).thenReturn(
                "device.dyndns.eblocker.com"
        );

        String config =
                service.renderClientConfig(3);

        assertTrue(
                config.contains(
                        "Endpoint = "
                                + "device.dyndns.eblocker.com"
                                + ":51820"
                )
        );
    }

    @Test
    public void failsWhenEblockerDynDnsDisabled() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        3
                )
        ).thenReturn(peer(
                3,
                "10.13.13.3/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.EBLOCKER_DYN_DNS,
                        null
                )
        );

        Mockito.when(
                dynDnsService.isEnabled()
        ).thenReturn(false);

        expectIllegalState(
                () -> service.renderClientConfig(3)
        );
    }

    @Test
    public void failsWhenEndpointConfigMissing() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        4
                )
        ).thenReturn(peer(
                4,
                "10.13.13.4/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(null);

        expectIllegalState(
                () -> service.renderClientConfig(4)
        );
    }

    @Test
    public void failsWhenConfiguredEndpointHostInvalid() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        5
                )
        ).thenReturn(peer(
                5,
                "10.13.13.5/32"
        ));

        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.DYN_DNS,
                        "https://vpn.example.org"
                )
        );

        expectIllegalState(
                () -> service.renderClientConfig(5)
        );
    }

    @Test
    public void rejectsIpv6Endpoint() {
        expectIllegalArgument(
                () -> service.setEndpointConfig(
                        new WireGuardEndpointConfig(
                                WireGuardEndpointType.FIXED_IP,
                                "2001:db8::1"
                        )
                )
        );
    }

    @Test
    public void rejectsEndpointContainingPort() {
        expectIllegalArgument(
                () -> service.setEndpointConfig(
                        new WireGuardEndpointConfig(
                                WireGuardEndpointType.DYN_DNS,
                                "vpn.example.org:51820"
                        )
                )
        );
    }

    @Test
    public void failsWhenPeerMissing() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        9
                )
        ).thenReturn(null);

        expectIllegalArgument(
                () -> service.renderClientConfig(9)
        );
    }

    @Test
    public void failsWhenPeerPrivateKeyInvalid() {
        WireGuardPeer peer =
                peer(
                        10,
                        "10.13.13.10/32"
                );

        peer.setPrivateKey("invalid");

        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        10
                )
        ).thenReturn(peer);

        expectIllegalState(
                () -> service.renderClientConfig(10)
        );
    }

    @Test
    public void failsWhenPeerAddressIsNotCanonical() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        11
                )
        ).thenReturn(peer(
                11,
                "10.13.13.011/32"
        ));

        expectIllegalState(
                () -> service.renderClientConfig(11)
        );
    }

    @Test
    public void savesFixedEndpointConfiguration() {
        WireGuardEndpointConfig result =
                service.setEndpointConfig(
                        new WireGuardEndpointConfig(
                                WireGuardEndpointType.FIXED_IP,
                                "198.51.100.23"
                        )
                );

        assertEquals(
                WireGuardEndpointType.FIXED_IP,
                result.getType()
        );

        assertEquals(
                "198.51.100.23",
                result.getHost()
        );

        ArgumentCaptor<WireGuardEndpointConfig> captor =
                ArgumentCaptor.forClass(
                        WireGuardEndpointConfig.class
                );

        Mockito.verify(
                dataSource
        ).save(captor.capture());

        assertEquals(
                WireGuardEndpointType.FIXED_IP,
                captor.getValue().getType()
        );

        assertEquals(
                "198.51.100.23",
                captor.getValue().getHost()
        );
    }

    @Test
    public void savesEblockerDynDnsWithoutSeparateHost() {
        WireGuardEndpointConfig result =
                service.setEndpointConfig(
                        new WireGuardEndpointConfig(
                                WireGuardEndpointType.EBLOCKER_DYN_DNS,
                                "must-not-be-persisted.example.org"
                        )
                );

        assertEquals(
                WireGuardEndpointType.EBLOCKER_DYN_DNS,
                result.getType()
        );

        assertNull(
                result.getHost()
        );

        ArgumentCaptor<WireGuardEndpointConfig> captor =
                ArgumentCaptor.forClass(
                        WireGuardEndpointConfig.class
                );

        Mockito.verify(
                dataSource
        ).save(captor.capture());

        assertNull(
                captor.getValue().getHost()
        );
    }

    @Test
    public void getEndpointConfigReturnsEmptyModelWhenUnset() {
        Mockito.when(
                dataSource.get(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(null);

        WireGuardEndpointConfig result =
                service.getEndpointConfig();

        assertNull(result.getType());
        assertNull(result.getHost());
    }

    private WireGuardPeer peer(
            int id,
            String allowedIp) {

        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setId(id);
        peer.setName("peer-" + id);
        peer.setPrivateKey(PRIVATE_KEY);
        peer.setPublicKey(PEER_PUBLIC_KEY);
        peer.setPresharedKey(PRESHARED_KEY);
        peer.setAllowedIp(allowedIp);
        peer.setAllowLanAccess(false);

        return peer;
    }

    private void expectIllegalState(
            Runnable action) {

        try {
            action.run();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private void expectIllegalArgument(
            Runnable action) {

        try {
            action.run();
            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}

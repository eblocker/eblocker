package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WireGuardDeviceIpResolverTest {

    @Test
    public void boundPeerResolvesDevice() {
        assertEquals(
                "device:28c21f2044a4",
                WireGuardDeviceIpResolver.resolveDeviceId(
                        IpAddress.parse("10.13.13.4"),
                        Collections.singletonList(
                                peer(
                                        "10.13.13.4/32",
                                        "device:28c21f2044a4"
                                )
                        )
                )
        );
    }

    @Test
    public void unboundPeerIsIgnored() {
        assertNull(
                WireGuardDeviceIpResolver.resolveDeviceId(
                        IpAddress.parse("10.13.13.3"),
                        Collections.singletonList(
                                peer("10.13.13.3/32", null)
                        )
                )
        );
    }

    @Test
    public void malformedOrNonHostRoutesAreIgnored() {
        assertNull(
                WireGuardDeviceIpResolver.resolveDeviceId(
                        IpAddress.parse("10.13.13.4"),
                        Arrays.asList(
                                peer(
                                        "not-an-address/32",
                                        "device:111111111111"
                                ),
                                peer(
                                        "10.13.13.4/24",
                                        "device:222222222222"
                                )
                        )
                )
        );
    }

    @Test
    public void differentPeerAddressIsIgnored() {
        assertNull(
                WireGuardDeviceIpResolver.resolveDeviceId(
                        IpAddress.parse("10.13.13.4"),
                        Collections.singletonList(
                                peer(
                                        "10.13.13.5/32",
                                        "device:333333333333"
                                )
                        )
                )
        );
    }

    @Test
    public void duplicateSourceMappingsFailClosed() {
        assertNull(
                WireGuardDeviceIpResolver.resolveDeviceId(
                        IpAddress.parse("10.13.13.4"),
                        Arrays.asList(
                                peer(
                                        "10.13.13.4/32",
                                        "device:111111111111"
                                ),
                                peer(
                                        "10.13.13.4/32",
                                        "device:222222222222"
                                )
                        )
                )
        );
    }

    @Test
    public void dataSourceOverloadUsesPersistedPeers() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Collections.singletonList(
                        peer(
                                "10.13.13.4/32",
                                "device:28c21f2044a4"
                        )
                )
        );

        assertEquals(
                "device:28c21f2044a4",
                WireGuardDeviceIpResolver.resolveDeviceId(
                        dataSource,
                        IpAddress.parse("10.13.13.4")
                )
        );
    }

    private WireGuardPeer peer(
            String allowedIp,
            String deviceId) {

        WireGuardPeer peer = new WireGuardPeer();
        peer.setAllowedIp(allowedIp);
        peer.setDeviceId(deviceId);
        return peer;
    }
}

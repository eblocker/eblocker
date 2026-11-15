package org.eblocker.server.common.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class Ipv4CidrTest {

    @Test
    public void canonicalizesHostBits() {
        assertEquals(
                "192.168.50.0/24",
                Ipv4Cidr.parse(
                        "192.168.50.77/24"
                ).toString()
        );
    }

    @Test
    public void canonicalizesWhitespaceAndLeadingZeros() {
        assertEquals(
                "192.168.1.0/24",
                Ipv4Cidr.parse(
                        " 192.168.001.077 / 024 "
                ).toString()
        );
    }

    @Test
    public void supportsDefaultRoutePrefixZero() {
        assertEquals(
                "0.0.0.0/0",
                Ipv4Cidr.parse(
                        "203.0.113.9/0"
                ).toString()
        );
    }

    @Test
    public void supportsHostRoutePrefixThirtyTwo() {
        assertEquals(
                "203.0.113.9/32",
                Ipv4Cidr.parse(
                        "203.0.113.9/32"
                ).toString()
        );
    }

    @Test
    public void canonicalEquivalentCidrsAreEqual() {
        assertEquals(
                Ipv4Cidr.parse(
                        "10.23.45.67/8"
                ),
                Ipv4Cidr.parse(
                        "10.0.0.0/8"
                )
        );

        assertFalse(
                Ipv4Cidr.parse(
                        "10.0.0.0/8"
                ).equals(
                        Ipv4Cidr.parse(
                                "10.0.0.0/9"
                        )
                )
        );
    }

    @Test
    public void rejectsIpv6() {
        expectIllegalArgument(
                () -> Ipv4Cidr.parse(
                        "2001:db8::/32"
                )
        );
    }

    @Test
    public void rejectsPrefixAboveThirtyTwo() {
        expectIllegalArgument(
                () -> Ipv4Cidr.parse(
                        "192.0.2.1/33"
                )
        );
    }

    @Test
    public void rejectsMissingPrefix() {
        expectIllegalArgument(
                () -> Ipv4Cidr.parse(
                        "192.0.2.1"
                )
        );
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

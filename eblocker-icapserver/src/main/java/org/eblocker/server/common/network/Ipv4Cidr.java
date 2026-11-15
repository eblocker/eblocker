package org.eblocker.server.common.network;

import org.eblocker.server.common.data.Ip4Address;

import java.util.Objects;

/**
 * Immutable canonical IPv4 CIDR value.
 *
 * Parsing deliberately reuses the project's existing Ip4Address parser.
 * Only the network-bit normalization is implemented here because the legacy
 * NetworkUtils netmask helpers intentionally accept only LAN-style prefixes
 * and therefore cannot represent the complete CIDR range /0 through /32.
 */
public final class Ipv4Cidr {

    private final String networkAddress;
    private final int prefixLength;

    private Ipv4Cidr(
            String networkAddress,
            int prefixLength) {

        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    public static Ipv4Cidr parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "IPv4 CIDR must not be null."
            );
        }

        String candidate = value.trim();
        String[] parts = candidate.split("/", -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid IPv4 CIDR."
            );
        }

        String addressText = parts[0].trim();
        String prefixText = parts[1].trim();

        if (addressText.isEmpty() || prefixText.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid IPv4 CIDR."
            );
        }

        final Ip4Address address;
        final int prefixLength;

        try {
            address = Ip4Address.parse(addressText);
            prefixLength = Integer.parseInt(prefixText);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid IPv4 CIDR.",
                    e
            );
        }

        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException(
                    "IPv4 CIDR prefix must be between 0 and 32."
            );
        }

        byte[] networkBytes =
                address.getAddress().clone();

        int remainingBits = prefixLength;

        for (int i = 0; i < networkBytes.length; i++) {
            final int mask;

            if (remainingBits >= 8) {
                mask = 0xff;
                remainingBits -= 8;
            } else if (remainingBits <= 0) {
                mask = 0;
            } else {
                mask =
                        (0xff << (8 - remainingBits))
                                & 0xff;
                remainingBits = 0;
            }

            networkBytes[i] =
                    (byte) (
                            (networkBytes[i] & 0xff)
                                    & mask
                    );
        }

        return new Ipv4Cidr(
                Ip4Address.of(networkBytes).toString(),
                prefixLength
        );
    }

    public String getNetworkAddress() {
        return networkAddress;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public String toString() {
        return networkAddress + "/" + prefixLength;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Ipv4Cidr)) {
            return false;
        }

        Ipv4Cidr that = (Ipv4Cidr) other;

        return prefixLength == that.prefixLength
                && networkAddress.equals(that.networkAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                networkAddress,
                prefixLength
        );
    }
}

package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.validation.NetworkConfigurationValidator;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointType;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.util.UrlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Creates WireGuard client configuration from persisted peer data and
 * an explicitly configured external endpoint.
 *
 * Security/product contract for the initial implementation:
 *
 * - IPv4 full tunnel only
 * - no IPv6 AllowedIPs
 * - no DNS directive until DNS on wg0 has been verified end-to-end
 * - no placeholder endpoint
 * - no fallback to OpenVPN-specific endpoint state
 */
@Singleton
public class WireGuardClientConfigurationService {

    static final int WIREGUARD_ENDPOINT_PORT = 51820;

    private static final Pattern WIREGUARD_KEY =
            Pattern.compile("^[A-Za-z0-9+/]{43}=$");

    private static final String PEER_NETWORK_PREFIX =
            "10.13.13.";

    private final DataSource dataSource;
    private final DynDnsService dynDnsService;
    private final WireGuardServerControlService controlService;

    @Inject
    public WireGuardClientConfigurationService(
            DataSource dataSource,
            DynDnsService dynDnsService,
            WireGuardServerControlService controlService) {

        this.dataSource = dataSource;
        this.dynDnsService = dynDnsService;
        this.controlService = controlService;
    }

    public String renderClientConfig(int peerId) {
        WireGuardPeer peer =
                dataSource.get(
                        WireGuardPeer.class,
                        peerId
                );

        if (peer == null) {
            throw new IllegalArgumentException(
                    "WireGuard peer not found."
            );
        }

        validatePeer(peer);

        String serverPublicKey =
                controlService.getPublicKey();

        if (!isValidWireGuardKey(serverPublicKey)) {
            throw new IllegalStateException(
                    "WireGuard server public key is invalid."
            );
        }

        String endpointHost =
                resolveEndpointHost();

        return "[Interface]\n"
                + "PrivateKey = "
                + peer.getPrivateKey()
                + "\n"
                + "Address = "
                + peer.getAllowedIp()
                + "\n"
                + "\n"
                + "[Peer]\n"
                + "PublicKey = "
                + serverPublicKey
                + "\n"
                + "PresharedKey = "
                + peer.getPresharedKey()
                + "\n"
                + "Endpoint = "
                + endpointHost
                + ":"
                + WIREGUARD_ENDPOINT_PORT
                + "\n"
                + "AllowedIPs = 0.0.0.0/0\n"
                + "PersistentKeepalive = 25\n";
    }

    /**
     * Renders the exact client configuration as a QR code PNG.
     *
     * The QR representation deliberately reuses renderClientConfig so
     * there is only one source of truth for sensitive client settings.
     */
    public byte[] renderClientConfigQrPng(
            int peerId) {

        String configuration =
                renderClientConfig(peerId);

        Map<EncodeHintType, Object> hints =
                new EnumMap<>(EncodeHintType.class);

        hints.put(
                EncodeHintType.CHARACTER_SET,
                "UTF-8"
        );

        hints.put(
                EncodeHintType.MARGIN,
                1
        );

        try {
            BitMatrix matrix =
                    new QRCodeWriter().encode(
                            configuration,
                            BarcodeFormat.QR_CODE,
                            360,
                            360,
                            hints
                    );

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(
                    matrix,
                    "PNG",
                    output
            );

            return output.toByteArray();

        } catch (WriterException
                 | IOException e) {

            throw new IllegalStateException(
                    "Could not render WireGuard client configuration QR code.",
                    e
            );
        }
    }

    public WireGuardEndpointConfig getEndpointConfig() {
        WireGuardEndpointConfig config =
                dataSource.get(
                        WireGuardEndpointConfig.class
                );

        if (config == null) {
            return new WireGuardEndpointConfig();
        }

        return config;
    }

    public WireGuardEndpointConfig setEndpointConfig(
            WireGuardEndpointConfig config) {

        if (config == null
                || config.getType() == null) {

            throw new IllegalArgumentException(
                    "WireGuard endpoint type is required."
            );
        }

        WireGuardEndpointConfig normalized;

        if (config.getType()
                == WireGuardEndpointType.EBLOCKER_DYN_DNS) {

            normalized =
                    new WireGuardEndpointConfig(
                            WireGuardEndpointType.EBLOCKER_DYN_DNS,
                            null
                    );

        } else {
            String host =
                    requireValidEndpointHost(
                            config.getHost()
                    );

            normalized =
                    new WireGuardEndpointConfig(
                            config.getType(),
                            host
                    );
        }

        dataSource.save(normalized);

        return normalized;
    }

    String resolveEndpointHost() {
        WireGuardEndpointConfig config =
                dataSource.get(
                        WireGuardEndpointConfig.class
                );

        if (config == null
                || config.getType() == null) {

            throw new IllegalStateException(
                    "WireGuard endpoint is not configured."
            );
        }

        if (config.getType()
                == WireGuardEndpointType.EBLOCKER_DYN_DNS) {

            if (!dynDnsService.isEnabled()) {
                throw new IllegalStateException(
                        "eBlocker DynDNS is not enabled."
                );
            }

            String hostname =
                    dynDnsService.getHostname();

            if (hostname == null
                    || !isValidDomainName(hostname)) {

                throw new IllegalStateException(
                        "eBlocker DynDNS hostname is not available."
                );
            }

            return hostname;
        }

        try {
            return requireValidEndpointHost(
                    config.getHost()
            );

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Configured WireGuard endpoint is invalid.",
                    e
            );
        }
    }

    private void validatePeer(
            WireGuardPeer peer) {

        if (!isValidWireGuardKey(
                peer.getPrivateKey())) {

            throw new IllegalStateException(
                    "WireGuard peer private key is invalid."
            );
        }

        if (!isValidWireGuardKey(
                peer.getPresharedKey())) {

            throw new IllegalStateException(
                    "WireGuard peer preshared key is invalid."
            );
        }

        if (!isValidPeerAddress(
                peer.getAllowedIp())) {

            throw new IllegalStateException(
                    "WireGuard peer address is invalid."
            );
        }
    }

    private boolean isValidWireGuardKey(
            String key) {

        return key != null
                && WIREGUARD_KEY.matcher(key).matches();
    }

    private boolean isValidPeerAddress(
            String allowedIp) {

        if (allowedIp == null
                || !allowedIp.startsWith(
                        PEER_NETWORK_PREFIX)
                || !allowedIp.endsWith("/32")) {

            return false;
        }

        String hostPart =
                allowedIp.substring(
                        PEER_NETWORK_PREFIX.length(),
                        allowedIp.length() - 3
                );

        int host;

        try {
            host = Integer.parseInt(hostPart);

        } catch (NumberFormatException e) {
            return false;
        }

        if (host < 2 || host > 254) {
            return false;
        }

        return allowedIp.equals(
                PEER_NETWORK_PREFIX
                        + host
                        + "/32"
        );
    }

    private String requireValidEndpointHost(
            String host) {

        if (host == null
                || host.isEmpty()
                || !host.equals(host.trim())) {

            throw new IllegalArgumentException(
                    "WireGuard endpoint host is invalid."
            );
        }

        if (NetworkConfigurationValidator
                .isValidIPv4Address(host)) {

            return host;
        }

        if (isValidDomainName(host)) {
            return host;
        }

        throw new IllegalArgumentException(
                "WireGuard endpoint host is invalid."
        );
    }

    private boolean isValidDomainName(
            String host) {

        if (host == null
                || host.isEmpty()
                || !host.equals(host.trim())) {

            return false;
        }

        String domain =
                UrlUtils.findDomainInString(
                        host,
                        false
                );

        return host.equals(domain);
    }
}

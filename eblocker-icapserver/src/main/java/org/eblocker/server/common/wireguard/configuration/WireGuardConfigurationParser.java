/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.wireguard.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WireGuardConfigurationParser {
    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[([A-Za-z]+)]$");
    private static final String SECTION_INTERFACE = "Interface";
    private static final String SECTION_PEER = "Peer";

    public WireGuardConfiguration parse(String config) throws ParseException {
        Builder builder = new Builder();
        Section currentSection = null;
        PeerBuilder currentPeer = null;
        String[] lines = config.split("\\r?\\n");

        for (int i = 0; i < lines.length; ++i) {
            int lineNumber = i + 1;
            String line = lines[i].trim();
            if (line.isEmpty() || isComment(line)) {
                continue;
            }

            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
            if (sectionMatcher.matches()) {
                if (currentPeer != null) {
                    builder.addPeer(currentPeer.build());
                    currentPeer = null;
                }
                String sectionName = sectionMatcher.group(1);
                if (SECTION_INTERFACE.equalsIgnoreCase(sectionName)) {
                    currentSection = Section.INTERFACE;
                } else if (SECTION_PEER.equalsIgnoreCase(sectionName)) {
                    currentSection = Section.PEER;
                    currentPeer = new PeerBuilder();
                } else {
                    throw new ParseException("unsupported section at line " + lineNumber + ": " + sectionName);
                }
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex < 0) {
                throw new ParseException("expected key/value option at line " + lineNumber);
            }
            if (currentSection == null) {
                throw new ParseException("option outside section at line " + lineNumber);
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (value.isEmpty()) {
                throw new ParseException("missing value for option at line " + lineNumber + ": " + key);
            }

            if (currentSection == Section.INTERFACE) {
                parseInterfaceOption(builder, key, value, lineNumber);
            } else {
                parsePeerOption(currentPeer, key, value, lineNumber);
            }
        }

        if (currentPeer != null) {
            builder.addPeer(currentPeer.build());
        }

        return builder.build();
    }

    private void parseInterfaceOption(Builder builder, String key, String value, int lineNumber) throws ParseException {
        if ("PrivateKey".equalsIgnoreCase(key)) {
            builder.privateKey = value;
        } else if ("Address".equalsIgnoreCase(key)) {
            builder.addresses = parseList(value);
        } else if ("DNS".equalsIgnoreCase(key)) {
            builder.dnsServers = parseList(value);
        } else if ("MTU".equalsIgnoreCase(key)) {
            builder.mtu = parseInteger(value, key, lineNumber);
        } else {
            throw new ParseException("unsupported interface option at line " + lineNumber + ": " + key);
        }
    }

    private void parsePeerOption(PeerBuilder peer, String key, String value, int lineNumber) throws ParseException {
        if ("PublicKey".equalsIgnoreCase(key)) {
            peer.publicKey = value;
        } else if ("PresharedKey".equalsIgnoreCase(key)) {
            peer.presharedKey = value;
        } else if ("Endpoint".equalsIgnoreCase(key)) {
            peer.endpoint = value;
        } else if ("AllowedIPs".equalsIgnoreCase(key)) {
            peer.allowedIps = parseList(value);
        } else if ("PersistentKeepalive".equalsIgnoreCase(key)) {
            peer.persistentKeepalive = parseInteger(value, key, lineNumber);
        } else {
            throw new ParseException("unsupported peer option at line " + lineNumber + ": " + key);
        }
    }

    private Integer parseInteger(String value, String key, int lineNumber) throws ParseException {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParseException("invalid integer for option at line " + lineNumber + ": " + key);
        }
    }

    private List<String> parseList(String value) {
        String[] parts = value.split(",");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private boolean isComment(String line) {
        return line.startsWith("#") || line.startsWith(";");
    }

    private enum Section {
        INTERFACE,
        PEER
    }

    private static class Builder {
        private String privateKey;
        private List<String> addresses = new ArrayList<>();
        private List<String> dnsServers = new ArrayList<>();
        private Integer mtu;
        private List<WireGuardPeer> peers = new ArrayList<>();

        void addPeer(WireGuardPeer peer) {
            peers.add(peer);
        }

        WireGuardConfiguration build() throws ParseException {
            if (privateKey == null) {
                throw new ParseException("missing interface private key");
            }
            return new WireGuardConfiguration(privateKey, addresses, dnsServers, mtu, peers);
        }
    }

    private static class PeerBuilder {
        private String publicKey;
        private String presharedKey;
        private String endpoint;
        private List<String> allowedIps = new ArrayList<>();
        private Integer persistentKeepalive;

        WireGuardPeer build() throws ParseException {
            if (publicKey == null) {
                throw new ParseException("missing peer public key");
            }
            return new WireGuardPeer(publicKey, presharedKey, endpoint, allowedIps, persistentKeepalive);
        }
    }

    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }
}

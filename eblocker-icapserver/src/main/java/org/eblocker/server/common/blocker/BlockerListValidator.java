/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.blocker;

import com.google.common.net.InternetDomainName;
import org.eblocker.server.common.blocker.parser.EtcHostsParser;
import org.eblocker.server.common.blocker.parser.SquidAclParser;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.eblocker.server.icap.filter.url.UrlLineParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Rejects unusable custom lists before they replace an installed definition. */
final class BlockerListValidator {
    // The current pattern parser loads the complete list into heap before creating its filters.
    static final long MAX_PATTERN_BYTES = 8L * 1024 * 1024;
    static final long MAX_DOMAIN_BYTES = 16L * 1024 * 1024;

    void validate(ExternalDefinition definition, Path path) throws IOException {
        long size = Files.size(path);
        if (size == 0) {
            throw invalid(definition, "the list is empty");
        }
        if (definition.getType() == Type.PATTERN && size > MAX_PATTERN_BYTES) {
            throw invalid(definition, "pattern list exceeds 8 MiB; the current parser cannot safely load it");
        }
        if (definition.getType() == Type.DOMAIN && size > MAX_DOMAIN_BYTES) {
            throw invalid(definition, "domain list exceeds 16 MiB; split or select a smaller list");
        }

        long entries = 0;
        long recognized = 0;
        long suspicious = 0;
        long plainDomains = 0;
        long lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("!") || value.startsWith("#") || value.startsWith("[")) {
                    continue;
                }
                entries++;
                if (value.equals("*") || value.equals("*$document") || value.equals("$document")) {
                    suspicious++;
                }
                try {
                    if (recognizes(definition, value)) recognized++;
                } catch (RuntimeException e) {
                    throw invalid(definition, "invalid rule at line " + lineNumber);
                }
                if (definition.getType() == Type.PATTERN && definition.getFormat() == Format.EASYLIST
                        && InternetDomainName.isValid(value)) {
                    plainDomains++;
                }
            }
        }
        if (suspicious > 0) {
            throw invalid(definition, suspicious + " rule(s) would match every website");
        }
        if (entries == 0 || recognized == 0) {
            throw invalid(definition, "no usable rules for the selected type and format");
        }
        if (definition.getType() == Type.PATTERN && definition.getFormat() == Format.EASYLIST
                && entries >= 10 && plainDomains * 4 >= entries * 3) {
            throw invalid(definition, "most entries are plain domains; add this as a Domain list instead");
        }
        if (definition.getType() == Type.DOMAIN && recognized * 2 < entries) {
            throw invalid(definition, "most entries are not domains in the selected format");
        }
    }

    private boolean recognizes(ExternalDefinition definition, String value) {
        if (definition.getType() == Type.DOMAIN) {
            if (definition.getFormat() == Format.ETC_HOSTS) {
                return new EtcHostsParser().parse(Stream.of(value)).findAny().isPresent();
            }
            if (definition.getFormat() == Format.DOMAINS || definition.getFormat() == Format.SQUID_ACL) {
                return new SquidAclParser().parse(Stream.of(value)).findAny().isPresent();
            }
            return false;
        }
        if (definition.getType() == Type.PATTERN) {
            if (definition.getFormat() == Format.EASYLIST) {
                return new EasyListLineParser().parseLine(value) != null;
            }
            if (definition.getFormat() == Format.URLS) {
                return new UrlLineParser().parseLine(value) != null;
            }
        }
        return false;
    }

    private IOException invalid(ExternalDefinition definition, String reason) {
        return new IOException("Custom list '" + definition.getName() + "' (ID " + definition.getId()
                + ", " + definition.getType() + "/" + definition.getFormat() + "): " + reason);
    }
}

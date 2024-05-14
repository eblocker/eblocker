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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class DhcpClientLeaseReader {
    private static final Logger log = LoggerFactory.getLogger(DhcpClientLeaseReader.class);

    private static final Pattern INTERFACE_PATTERN = Pattern.compile("interface \"(.*)\";");
    private static final Pattern FIXED_ADDRESS_PATTERN = Pattern.compile("fixed-address (.*);");
    private static final Pattern OPTION_PATTERN = Pattern.compile("option (.*) (.*);");

    private final String clientLeasesFileName;

    @Inject
    public DhcpClientLeaseReader(@Named("network.unix.dhclient.leases") String clientLeasesFileName) {
        this.clientLeasesFileName = clientLeasesFileName;
    }

    /**
     * Reads the last DHCP client lease from the configured leases file.
     * @return a DhcpClientLease object or null if the leases file does not exist or does not contain any leases.
     */
    public DhcpClientLease readLease() {
        SimpleResource leasesFile = new SimpleResource(clientLeasesFileName);
        if (!ResourceHandler.exists(leasesFile)) {
            log.warn("DHCP client leases file does not exist: {}", clientLeasesFileName);
            return null;
        }
        List<String> lines = ResourceHandler.readLines(leasesFile);
        int i = lines.lastIndexOf("lease {");
        if (i == -1) {
            return null;
        }

        String interfaceName = null;
        String fixedAddress = null;
        Map<String, String> options = new HashMap<>();
        for (i = i + 1; i < lines.size(); ++i) {
            String line = lines.get(i);

            if ("}".equals(line)) {
                break;
            }

            Matcher matcher = INTERFACE_PATTERN.matcher(line);
            if (matcher.find()) {
                interfaceName = matcher.group(1);
                continue;
            }

            matcher = FIXED_ADDRESS_PATTERN.matcher(line);
            if (matcher.find()) {
                fixedAddress = matcher.group(1);
                continue;
            }

            matcher = OPTION_PATTERN.matcher(line);
            if (matcher.find()) {
                options.put(matcher.group(1), matcher.group(2));
                continue;
            }
        }

        return new DhcpClientLease(interfaceName, fixedAddress, options);
    }
}

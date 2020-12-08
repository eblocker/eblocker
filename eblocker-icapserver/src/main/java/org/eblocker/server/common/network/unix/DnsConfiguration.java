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
import com.google.inject.name.Named;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides access to nameserver configuration of the system.
 */
public class DnsConfiguration {
    private final String resolvConfReadPath;
    private final String resolvConfWritePath;
    private static final Pattern nameserverPattern = Pattern.compile("^\\s*nameserver\\s+((\\d+\\.){3}(\\d+))$");

    @Inject
    public DnsConfiguration(@Named("network.unix.nameserver.config.read.path") String resolvConfReadPath,
                            @Named("network.unix.nameserver.config.write.path") String resolvConfWritePath) {
        this.resolvConfReadPath = resolvConfReadPath;
        this.resolvConfWritePath = resolvConfWritePath;
    }

    public List<String> getNameserverAddresses() throws IOException {
        List<String> nameserverAddresses = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(resolvConfReadPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = nameserverPattern.matcher(line);
                if (matcher.matches()) {
                    nameserverAddresses.add(matcher.group(1));
                }
            }
        }
        return nameserverAddresses;
    }

    public void setNameserverAddresses(List<String> nameserverAddresses) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resolvConfWritePath))) {
            for (String address : nameserverAddresses) {
                writer.append("nameserver ");
                writer.append(address);
                writer.newLine();
            }
        }
    }
}

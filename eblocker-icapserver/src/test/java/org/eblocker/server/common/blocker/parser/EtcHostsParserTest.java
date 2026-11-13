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
package org.eblocker.server.common.blocker.parser;

import com.google.common.base.Charsets;
import org.eblocker.server.common.blacklist.SingleFileFilter;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EtcHostsParserTest {

    @Test
    public void parse() {
        List<String> lines = Arrays.asList(
                "#comment\n",
                "127.0.0.1\tlocalhost\n",
                "\n",
                " 138.68.124.96 \t www.eblocker.com #another comment\n",
                "172.217.21.206 google.com  \n",
                "62.201.164.110 etracker.com\n"
        );

        List<String> parsedDomains = new EtcHostsParser()
                .parse(lines.stream())
                .collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("www.eblocker.com", "google.com", "etracker.com"), parsedDomains);
    }

    @Test
    public void parseMultipleAliasesAndRejectInvalidAddressOrDomain() {
        List<String> lines = Arrays.asList(
                "0.0.0.0 ads.example.com tracker.example.com # two hosts",
                "999.999.999.999 ignored.example.com",
                "127x0x0x1 ignored-too.example.com",
                "::1 ipv6.example.com",
                "127.0.0.1 localhost com invalid/host"
        );

        List<String> parsedDomains = new EtcHostsParser().parse(lines.stream()).collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("ads.example.com", "tracker.example.com", "ipv6.example.com"), parsedDomains);
    }

    @Test
    public void parsedHostsCanBeCompiledAndQueried() throws IOException {
        Path output = Files.createTempFile("hosts-filter", ".bin");
        Files.delete(output);
        try {
            SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, output, 1, "hosts-test",
                    () -> new EtcHostsParser().parse(Arrays.asList(
                            "0.0.0.0 ads.example.com tracker.example.com",
                            "0.0.0.0 localhost",
                            "invalid unrelated.example.com").stream()));
            Assert.assertTrue(filter.isBlocked("ads.example.com").isBlocked());
            Assert.assertTrue(filter.isBlocked("tracker.example.com").isBlocked());
            Assert.assertFalse(filter.isBlocked("unrelated.example.com").isBlocked());
            Assert.assertFalse(filter.isBlocked("example.com").isBlocked());
        } finally {
            Files.deleteIfExists(output);
        }
    }
}

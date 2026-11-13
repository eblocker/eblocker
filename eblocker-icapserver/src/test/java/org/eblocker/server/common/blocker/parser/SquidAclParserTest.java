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

public class SquidAclParserTest {

    @Test
    public void parse() {
        List<String> lines = Arrays.asList(
                "#comment\n",
                "192.168.1.2\n",
                "<html>\n",
                "-not-ok.com\n",
                "not-ok-.com\n",
                "..not-ok.com\n",
                ".is-ok.com\n",
                "häagen-dazs.com\n",
                " www.eblocker.com\n",
                " google.com  #comment\n",
                "etracker.com\n"
        );

        List<String> parsedDomains = new SquidAclParser()
                .parse(lines.stream())
                .collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("is-ok.com", "häagen-dazs.com", "www.eblocker.com", "google.com", "etracker.com"), parsedDomains);
    }

    @Test
    public void publicSuffixIsNotAUsableBlockEntry() {
        List<String> lines = Arrays.asList("com", "co.uk", ".com", "example.com", ".sub.example.co.uk");
        List<String> parsedDomains = new SquidAclParser().parse(lines.stream()).collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("example.com", "sub.example.co.uk"), parsedDomains);
    }

    @Test
    public void parsedDomainsCanBeCompiledAndQueried() throws IOException {
        Path output = Files.createTempFile("domains-filter", ".bin");
        Files.delete(output);
        try {
            SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, output, 1, "domains-test",
                    () -> new SquidAclParser().parse(Arrays.asList(
                            "com", ".ads.example.com", "tracker.example.com", "||unrelated.example.com^").stream()));
            Assert.assertTrue(filter.isBlocked("ads.example.com").isBlocked());
            Assert.assertTrue(filter.isBlocked("tracker.example.com").isBlocked());
            Assert.assertFalse(filter.isBlocked("unrelated.example.com").isBlocked());
            Assert.assertFalse(filter.isBlocked("example.com").isBlocked());
        } finally {
            Files.deleteIfExists(output);
        }
    }
}

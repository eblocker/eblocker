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

import org.junit.Assert;
import org.junit.Test;

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
}

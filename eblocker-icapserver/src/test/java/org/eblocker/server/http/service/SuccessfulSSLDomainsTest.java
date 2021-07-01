/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.service;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SuccessfulSSLDomainsTest {

    @Test
    public void testAddingRandomStrings() {
        SuccessfulSSLDomains testee = new SuccessfulSSLDomains(1000, 100, 0.001);

        List<String> domains = IntStream.range(1, 4000)
                .mapToObj(i -> Integer.toString(i).repeat(i) + "foo.com")
                .collect(Collectors.toList());

        domains.forEach(testee::recordDomain);

        assertTrue(testee.isDomainAlreadySucessful(domains.get(domains.size() - 10))); // some recently added domain
        assertFalse(testee.isDomainAlreadySucessful(domains.get(0)));
    }
}
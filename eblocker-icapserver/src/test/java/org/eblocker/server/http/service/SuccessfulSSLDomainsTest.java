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
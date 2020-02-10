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
package org.eblocker.server.http.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eblocker.server.common.data.TorCheckSite;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * This service provides a list of sites for checking whether a Tor connection is used
 */
public class TorCheckService {
    private static ObjectMapper mapper = new ObjectMapper();
    private List<TorCheckSite> allSites;

    @Inject
    public TorCheckService(@Named("tor.check.sites.file.path") String torCheckSitesFile) throws IOException {
        EblockerResource sitesResource = new SimpleResource(torCheckSitesFile);
        allSites = Arrays.asList(mapper.readValue(ResourceHandler.getInputStream(sitesResource), TorCheckSite[].class));
    }

    /**
     * Returns a list of Tor checking sites.
     * @param includeSSL set to false if only HTTP URLs shall be returned
     */
    public List<TorCheckSite> getSites(boolean includeSSL) {
        Predicate<TorCheckSite> siteFilter;
        if (includeSSL) {
            // accept all URLs
            siteFilter = (site) -> true;
        } else {
            // accept only HTTP URLs
            siteFilter = (site) -> site.getUrl().startsWith("http:");
        }
        return allSites.stream()
            .filter(siteFilter)
            .collect(Collectors.toList());
    }
}

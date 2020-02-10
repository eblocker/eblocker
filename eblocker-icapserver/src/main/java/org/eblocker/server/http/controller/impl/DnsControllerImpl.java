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
package org.eblocker.server.http.controller.impl;

import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.http.controller.DnsController;
import org.eblocker.server.http.service.DnsService;
import org.eblocker.server.http.utils.ControllerUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DnsControllerImpl implements DnsController {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(DnsControllerImpl.class);

    private final ObjectMapper objectMapper;
    private final DnsService dnsService;

    @Inject
    public DnsControllerImpl(ObjectMapper objectMapper,
                             DnsService dnsService) {
        this.objectMapper = objectMapper;
        this.dnsService = dnsService;
    }

    public DnsResolvers getDnsResolvers(Request request, Response response) {
        return dnsService.getDnsResolvers();
    }

    public DnsResolvers setDnsResolvers(Request request, Response response) {
        return dnsService.setDnsResolvers(request.getBodyAs(DnsResolvers.class));
    }

    @Override
    public List<LocalDnsRecord> getLocalDnsRecords(Request request, Response response) {
        return dnsService.getLocalDnsRecords();
    }

    @Override
    public List<LocalDnsRecord> setLocalDnsRecords(Request request, Response response) {
        try {
            List<LocalDnsRecord> records = objectMapper.readValue(request.getBodyAsStream(),
                    new TypeReference<List<LocalDnsRecord>>() {
                    });
            return dnsService.setLocalDnsRecords(records);
        } catch (IOException e) {
            throw new BadRequestException("failed to parse body", e);
        }
    }

    public boolean getStatus(Request request, Response response) {
        return dnsService.isEnabled();
    }

    public boolean setStatus(Request request, Response response) {
        Boolean status = request.getBodyAs(Boolean.class);
        return dnsService.setStatus(status);
    }

    public void flushCache(Request request, Response response) {
        dnsService.flushCache();
    }

    @Override
    public List<NameServerStats> testNameServer(Request request, Response response) {
        List<String> nameServers = request.getBodyAs(List.class);
        return dnsService.testNameServers(nameServers);
    }

    @Override
    public Object getResolverStats(Request request, Response response) {
        String resolver = ControllerUtils.getQueryParameter(request, "resolver",
                dnsService.getDnsResolvers().getDefaultResolver());
        int hours = Integer.parseInt(ControllerUtils.getQueryParameter(request, "hours", "4"));
        String lengthValue = request.getHeader("length");

        return dnsService.getResolverStats(resolver, hours, lengthValue);
    }

}

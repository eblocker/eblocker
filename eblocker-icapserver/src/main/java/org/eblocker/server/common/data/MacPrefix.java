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
package org.eblocker.server.common.data;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.icap.resources.DefaultEblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class MacPrefix {
    private static final Logger log = LoggerFactory.getLogger(MacPrefix.class);

    private Map<String, String> prefixToVendor;

    @Inject
    public MacPrefix() {
        prefixToVendor = new HashMap<>();

        try (InputStream inputStream = ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES)) {
            addInputStream(inputStream);
        } catch (IOException e) {
            log.error("Could not read MAC prefixes", e);
        }
    }

    public void addInputStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line = null;
        while ((line = reader.readLine()) != null) {
            String prefix = line.substring(0, 6);
            String vendor = line.substring(7);
            prefixToVendor.put(prefix, vendor);
        }
    }

    public String getVendor(String macAddress) {
        if (macAddress == null || macAddress.length() < 6) {
            return null;
        }
        return prefixToVendor.get(macAddress.substring(0, 6));
    }

}

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
package org.eblocker.server.common.network.unix.firewall;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Table {
    private String name;
    private Map<String, Chain> chainsByName = new LinkedHashMap<>();

    public Table(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Collection<Chain> getChains() {
        return chainsByName.values();
    }

    public Chain chain(String name) {
        if (!chainsByName.containsKey(name)) {
            chainsByName.put(name, new Chain(name));
        }
        return chainsByName.get(name);
    }
}

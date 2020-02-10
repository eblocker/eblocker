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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CloakedUserAgentConfig {

    private Map<CloakedUserAgentKey, String> cloakedUserAgentById;

    public CloakedUserAgentConfig() {
        this.cloakedUserAgentById = new HashMap<>();
    }

    public Map<CloakedUserAgentKey, String> getCloakedUserAgentById() {
        return cloakedUserAgentById;
    }

    public void setCloakedUserAgentById(Map<CloakedUserAgentKey, String> map) {
        this.cloakedUserAgentById = map;
    }

    public String put(CloakedUserAgentKey key, String value) {
        return this.cloakedUserAgentById.put(key, value);
    }

    public String get(CloakedUserAgentKey key) {
        return this.cloakedUserAgentById.get(key);
    }

    public Set<CloakedUserAgentKey> keySet() {
        return this.cloakedUserAgentById.keySet();
    }

    public String remove(CloakedUserAgentKey key) {
        return this.cloakedUserAgentById.remove(key);
    }

    public boolean contains(CloakedUserAgentKey key) {
        return cloakedUserAgentById.containsKey(key);
    }
}

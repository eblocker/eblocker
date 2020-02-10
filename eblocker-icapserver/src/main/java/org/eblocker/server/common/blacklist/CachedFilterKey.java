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
package org.eblocker.server.common.blacklist;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CachedFilterKey {
    private final int id;
    private final long version;

    @JsonCreator
    public CachedFilterKey(@JsonProperty("id") int id, @JsonProperty("version") long version) {
        this.id = id;
        this.version = version;
    }

    public int getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return id + "-v" + version;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CachedFilterKey)) {
            return false;
        }

        CachedFilterKey that = (CachedFilterKey) obj;
        return id == that.id && version == that.getVersion();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id) ^ Long.hashCode(version);
    }
}

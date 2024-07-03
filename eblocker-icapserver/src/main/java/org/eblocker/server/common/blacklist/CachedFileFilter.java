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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class CachedFileFilter {
    @Nonnull
    private final CachedFilterKey key;
    @Nonnull
    private final String bloomFilterFileName;
    @Nullable
    private final String fileFilterFileName;
    private final String format;
    private boolean deleted;

    @JsonCreator
    CachedFileFilter(@Nonnull @JsonProperty("key") CachedFilterKey key,
                     @Nonnull @JsonProperty("bloomFilterFileName") String bloomFilterFileName,
                     @Nullable @JsonProperty("fileFilterFileName") String fileFilterFileName,
                     @JsonProperty("format") String format,
                     @JsonProperty("deleted") boolean deleted) {
        this.key = key;
        this.bloomFilterFileName = bloomFilterFileName;
        this.fileFilterFileName = fileFilterFileName;
        this.format = format;
        this.deleted = deleted;
    }

    @Nonnull
    CachedFilterKey getKey() {
        return key;
    }

    @Nonnull
    String getBloomFilterFileName() {
        return bloomFilterFileName;
    }

    @Nullable
    String getFileFilterFileName() {
        return fileFilterFileName;
    }

    String getFormat() {
        return format;
    }

    boolean isDeleted() {
        return deleted;
    }

    void setDeleted() {
        this.deleted = true;
    }
}

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

class CachedFileFilter {
    private CachedFilterKey key;
    private String bloomFilterFileName;
    private String fileFilterFileName;
    private String format;
    private boolean deleted;

    public CachedFileFilter() {
    }

    public CachedFileFilter(CachedFilterKey key, String bloomFilterFileName, String fileFilterFileName, String format, boolean deleted) {
        this.key = key;
        this.bloomFilterFileName = bloomFilterFileName;
        this.fileFilterFileName = fileFilterFileName;
        this.format = format;
        this.deleted = deleted;
    }

    public CachedFilterKey getKey() {
        return key;
    }

    public void setKey(CachedFilterKey key) {
        this.key = key;
    }

    public String getBloomFilterFileName() {
        return bloomFilterFileName;
    }

    public void setBloomFilterFileName(String bloomFilterFileName) {
        this.bloomFilterFileName = bloomFilterFileName;
    }

    public String getFileFilterFileName() {
        return fileFilterFileName;
    }

    public void setFileFilterFileName(String fileFilterFileName) {
        this.fileFilterFileName = fileFilterFileName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

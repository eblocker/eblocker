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
package org.eblocker.server.icap.filter.bpjm;

import javax.xml.bind.DatatypeConverter;

public class BpjmEntry {
    private final byte[] domainHash;
    private final byte[] pathHash;
    private final int depth;

    public BpjmEntry(byte[] domainHash, byte[] pathHash, int depth) {
        this.domainHash = domainHash;
        this.pathHash = pathHash;
        this.depth = depth;
    }

    public byte[] getDomainHash() {
        return domainHash;
    }

    public byte[] getPathHash() {
        return pathHash;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        return "BpjmEntry{" +
            "domainHash=" + DatatypeConverter.printHexBinary(domainHash).toLowerCase() +
            ", pathHash=" + DatatypeConverter.printHexBinary(pathHash).toLowerCase() +
            ", depth=" + depth +
            '}';
    }
}

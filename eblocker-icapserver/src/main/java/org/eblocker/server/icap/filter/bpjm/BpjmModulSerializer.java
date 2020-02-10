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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BpjmModulSerializer {

    private static final byte[] MAGIC = "ebpjm".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 0x00;

    public BpjmModul read(InputStream in) throws IOException {
        DataInputStream dataIn = new DataInputStream(in);

        byte[] magic = new byte[MAGIC.length];
        dataIn.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("not in eblocker format");
        }

        byte version = dataIn.readByte();
        if (version != VERSION) {
            throw new IOException("unexpected version " + version);
        }

        long lastModified = dataIn.readLong();
        int size = dataIn.readInt();

        List<BpjmEntry> entries = new ArrayList<>(size);
        for(int i = 0; i < size; ++i) {
            byte[] domainHash = new byte[16];
            dataIn.readFully(domainHash);
            byte[] pathHash = new byte[16];
            dataIn.readFully(pathHash);
            byte depth = dataIn.readByte();
            entries.add(new BpjmEntry(domainHash, pathHash, depth));
        }

        return new BpjmModul(entries, lastModified);
    }

    public void write(BpjmModul bpjmModul, OutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.write(MAGIC);
        dataOut.write(VERSION);
        dataOut.writeLong(bpjmModul.getLastModified());
        dataOut.writeInt(bpjmModul.getEntries().size());
        for(BpjmEntry e : bpjmModul.getEntries()) {
            dataOut.write(e.getDomainHash());
            dataOut.write(e.getPathHash());
            dataOut.writeByte(e.getDepth());
        }
        dataOut.flush();
    }

}

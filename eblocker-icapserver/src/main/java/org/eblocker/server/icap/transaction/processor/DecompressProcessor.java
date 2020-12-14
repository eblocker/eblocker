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
package org.eblocker.server.icap.transaction.processor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.brotli.dec.BrotliInputStream;
import org.eblocker.server.icap.transaction.ContentEncoding;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DecompressProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DecompressProcessor.class);

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.isPreview()) {
            return true;
        }

        ContentEncoding encoding = getContentEncoding(transaction);
        transaction.setContentEncoding(encoding);

        if (encoding != ContentEncoding.UNKNOWN) {
            try {
                transaction.setContent(decodeBuffer(encoding, transaction.getResponse().content()));
            } catch (IOException e) {
                log.warn("Decompressing content failed", e);
                transaction.setContentEncoding(ContentEncoding.UNKNOWN);
            }
        }

        return true;
    }

    private ContentEncoding getContentEncoding(Transaction transaction) {
        HttpHeaders headers = transaction.getResponse().headers();
        if (!headers.contains(HttpHeaders.Names.CONTENT_ENCODING)) {
            return ContentEncoding.NONE;
        }

        String encoding = headers.get(HttpHeaders.Names.CONTENT_ENCODING);
        switch (encoding) {
            case HttpHeaders.Values.GZIP:
                return ContentEncoding.GZIP;
            case HttpHeaders.Values.DEFLATE:
                return getDeflateEncoding(transaction);
            case "br":
                return ContentEncoding.BROTLI;
            case HttpHeaders.Values.IDENTITY:
                return ContentEncoding.NONE;
            default:
                return ContentEncoding.UNKNOWN;
        }
    }

    private ContentEncoding getDeflateEncoding(Transaction transaction) {
        int zlibCompressionMethod = transaction.getResponse().content().getByte(0) & 0x0f;
        return zlibCompressionMethod == 8 ? ContentEncoding.DEFLATE : ContentEncoding.DEFLATE_NO_WRAP;
    }

    private StringBuilder decodeBuffer(ContentEncoding encoding, ByteBuf buffer) throws IOException {
        buffer.markReaderIndex();
        try (InputStream in = wrapDecompressionStream(encoding, new ByteBufInputStream(buffer))) {
            return toStringBuilder(in);
        } finally {
            buffer.resetReaderIndex();
        }
    }

    private static InputStream wrapDecompressionStream(ContentEncoding encoding, InputStream in) throws IOException {
        switch (encoding) {
            case DEFLATE:
                return new InflaterInputStream(in);
            case DEFLATE_NO_WRAP:
                return new InflaterInputStream(in, new Inflater(true));
            case GZIP:
                return new GZIPInputStream(in);
            case BROTLI:
                return new BrotliInputStream(in);
            default:
                return in;
        }
    }

    private StringBuilder toStringBuilder(InputStream in) throws IOException {
        StringBuilderWriter writer = new StringBuilderWriter();
        IOUtils.copy(in, writer, StandardCharsets.ISO_8859_1); // ISO_8859_1 encodes / decodes transparently to / from bytes
        return writer.getBuilder();
    }

}

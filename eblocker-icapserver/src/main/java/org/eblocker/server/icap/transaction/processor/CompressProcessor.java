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

import org.eblocker.server.common.data.CompressionMode;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.transaction.ContentEncoding;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

@Singleton
public class CompressProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompressProcessor.class);

    private final DeviceService deviceService;
    private final FeatureService featureService;

    @Inject
    public CompressProcessor(DeviceService deviceService, FeatureServiceSubscriber featureService) {
        this.deviceService = deviceService;
        this.featureService = featureService;
    }

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.isPreview()) {
            return true;
        }

        if (transaction.getContent() != null) {
            try {
                ContentEncoding encoding = selectResponseEncoding(transaction);
                ByteBuf encodedContent = encode(encoding, transaction.getContent());
                setHttpResponseContent(transaction, encoding, encodedContent);
            } catch (IOException e) {
                log.error("compressing response failed", e);
            }
        }

        return true;
    }

    private void setHttpResponseContent(Transaction transaction, ContentEncoding encoding, ByteBuf buffer) {
        FullHttpResponse httpResponse = transaction.getResponse().replace(buffer);
        int contentLength = buffer.readableBytes();
        HttpHeaders headers = httpResponse.headers();
        headers.set(HttpHeaders.Names.CONTENT_LENGTH, contentLength);

        switch (encoding) {
            case NONE:
                headers.remove(HttpHeaders.Names.CONTENT_ENCODING);
                break;
            case GZIP:
                headers.set(HttpHeaders.Names.CONTENT_ENCODING, HttpHeaders.Values.GZIP);
                break;
            case DEFLATE:
            case DEFLATE_NO_WRAP:
                headers.set(HttpHeaders.Names.CONTENT_ENCODING, HttpHeaders.Values.DEFLATE);
                break;
            case BROTLI:
            case UNKNOWN:
                throw new IllegalStateException("cannot encode " + encoding);        }

        transaction.getResponse().release();
        transaction.setResponse(httpResponse);
    }

    private ByteBuf encode(ContentEncoding encoding, StringBuilder content) throws IOException {
        ByteBuf channelBuffer = ByteBufAllocator.DEFAULT.buffer(content.length());
        try (OutputStream out = wrapCompressionStream(encoding, new ByteBufOutputStream(channelBuffer))) {
            out.write(content.toString().getBytes(StandardCharsets.ISO_8859_1));
            return channelBuffer;
        } catch (IOException e) {
            channelBuffer.release();
            throw e;
        }
    }

    private ContentEncoding selectResponseEncoding(Transaction transaction) {
        if (featureService.getCompressionMode() == CompressionMode.OFF) {
            return ContentEncoding.NONE;
        }

        if (featureService.getCompressionMode() == CompressionMode.VPN_CLIENTS_ONLY) {
            Device device = deviceService.getDeviceById(transaction.getSession().getDeviceId());
            if (!device.isVpnClient()) {
                return ContentEncoding.NONE;
            }
        }

        // no brotli compressor available so we just re-compress it as gzip or deflate if acceptable
        if (transaction.getContentEncoding() == ContentEncoding.BROTLI) {
            String acceptEncodingValue = transaction.getRequest().headers().get("Accept-Encoding");
            if (acceptEncodingValue == null) {
                return ContentEncoding.NONE;
            }

            String[] acceptedEncodings = acceptEncodingValue.split(",");
            for(String encoding : acceptedEncodings) {
                switch (encoding.trim()) {
                    case HttpHeaders.Values.GZIP:
                        return ContentEncoding.GZIP;
                    case HttpHeaders.Values.DEFLATE:
                        return ContentEncoding.DEFLATE;
                    case HttpHeaders.Values.IDENTITY:
                        return ContentEncoding.NONE;
                    default:
                }
            }
            return ContentEncoding.NONE;
        }

        return transaction.getContentEncoding();
    }

    private OutputStream wrapCompressionStream(ContentEncoding encoding, OutputStream out) throws IOException {
        switch (encoding) {
            case DEFLATE:
                return new DeflaterOutputStream(out);
            case DEFLATE_NO_WRAP:
                return new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
            case GZIP:
                return new GZIPOutputStream(out);
            default:
                return out;
        }
    }

}

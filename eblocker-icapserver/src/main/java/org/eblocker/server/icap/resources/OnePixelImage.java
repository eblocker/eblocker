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
package org.eblocker.server.icap.resources;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OnePixelImage {
    private static final Logger log = LoggerFactory.getLogger(OnePixelImage.class);

    public static final String MIME_TYPE = "image/svg+xml";

    private static final byte[] TARGET_PARAMETER = "@TARGET@".getBytes(StandardCharsets.UTF_8);

    private static final byte[] IMAGE_TEMPLATE;
    private static final int TARGET_PARAMETER_START_INDEX; // inclusive
    private static final int TARGET_PARAMETER_END_INDEX;  // exclusive

    static {
        try {
            IMAGE_TEMPLATE = ByteStreams.toByteArray(ResourceHandler.getInputStream(DefaultEblockerResource.ONE_PIXEL_SVG));
            TARGET_PARAMETER_START_INDEX = Bytes.indexOf(IMAGE_TEMPLATE, TARGET_PARAMETER);
            TARGET_PARAMETER_END_INDEX = TARGET_PARAMETER_START_INDEX + TARGET_PARAMETER.length;
        } catch (IOException e) {
            String msg = "Cannot load ONE_PIXEL_SVG resource: " + e.getMessage();
            log.error(msg, e);
            throw new EblockerException(msg, e);
        }
    }

    private OnePixelImage() {
    }

    public static byte[] get(String target) {
        byte[] encodedTarget = target.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[TARGET_PARAMETER_START_INDEX + encodedTarget.length + IMAGE_TEMPLATE.length - TARGET_PARAMETER_END_INDEX];
        System.arraycopy(IMAGE_TEMPLATE, 0, out, 0, TARGET_PARAMETER_START_INDEX);
        System.arraycopy(encodedTarget, 0, out, TARGET_PARAMETER_START_INDEX, encodedTarget.length);
        System.arraycopy(IMAGE_TEMPLATE, TARGET_PARAMETER_END_INDEX, out, TARGET_PARAMETER_START_INDEX + encodedTarget.length, IMAGE_TEMPLATE.length - TARGET_PARAMETER_END_INDEX);
        return out;
    }

}

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
package org.eblocker.server.common.data.messagecenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertNotNull;

public class MessageContainerTest {

    private static final Logger LOG = LoggerFactory.getLogger(MessageContainerTest.class);

    @Test
    public void test() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        MessageContainer messageContainer = new MessageContainer(
                new MessageCenterMessage(
                        815,
                        "TITLE_KEY",
                        "CONTENT_KEY",
                        "LABEL_KEY",
                        "URL_KEY",
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        new Date(),
                        true,
                        MessageSeverity.INFO
                ),
                new MessageVisibility(
                        815,
                        false,
                        Collections.emptySet()
                )
        );

        String json = objectMapper.writeValueAsString(messageContainer);
        LOG.info("JSON:\n{}", json);

        MessageContainer reloaded = objectMapper.readValue(json, MessageContainer.class);
        assertNotNull(reloaded);
    }
}

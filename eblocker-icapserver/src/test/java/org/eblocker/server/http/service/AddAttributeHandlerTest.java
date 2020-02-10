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
package org.eblocker.server.http.service;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AddAttributeHandlerTest {

    @Test
    public void test() {
        Map<AttributeKey, Object> attributes = new HashMap<>();
        attributes.put(AttributeKey.newInstance("attribute-1"), "value-1");
        attributes.put(AttributeKey.newInstance("attribute-2"), "value-2");
        attributes.put(AttributeKey.newInstance("attribute-3"), "value-3");
        AddAttributeHandler attributeHandler = new AddAttributeHandler(attributes);

        ChannelHandler assertHandler = new SimpleChannelInboundHandler<Object>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                attributes.forEach((k, v) -> Assert.assertEquals(v, ctx.channel().attr(k).get()));
            }
        };

        EmbeddedChannel embeddedChannel = new EmbeddedChannel(attributeHandler, assertHandler);
        embeddedChannel.writeInbound(new Object());
        embeddedChannel.checkException();
    }

}

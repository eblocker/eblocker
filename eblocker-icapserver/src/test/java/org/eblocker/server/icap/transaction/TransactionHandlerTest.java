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
package org.eblocker.server.icap.transaction;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.eblocker.server.common.recorder.TransactionRecorder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransactionHandlerTest {

    private TransactionProcessorsConfiguration configuration;
    private TransactionRecorder recorder;
    private TransactionHandler handler;

    private Transaction transaction;
    private ChannelHandlerContext channelHandlerContext;
    private Channel channel;

    private TransactionProcessor successProcessor;
    private TransactionProcessor failProcessor;

    @Before
    public void setup() {
        configuration = Mockito.mock(TransactionProcessorsConfiguration.class);
        recorder = Mockito.mock(TransactionRecorder.class);
        handler = new TransactionHandler(configuration, recorder);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getUrl()).thenReturn("unit test");

        channel = Mockito.mock(Channel.class);
        channelHandlerContext = Mockito.mock(ChannelHandlerContext.class);
        Mockito.when(channelHandlerContext.channel()).thenReturn(channel);

        successProcessor = Mockito.mock(TransactionProcessor.class);
        Mockito.when(successProcessor.process(Mockito.any(Transaction.class))).thenReturn(true);

        failProcessor = Mockito.mock(TransactionProcessor.class);
    }

    @Test
    public void testRequest() {
        Mockito.when(transaction.isRequest()).thenReturn(true);
        List<TransactionProcessor> requestProcessors = Arrays.asList(
                successProcessor,
                successProcessor,
                successProcessor
        );
        Mockito.when(configuration.getRequestProcessors()).thenReturn(requestProcessors);

        handler.channelRead(channelHandlerContext, transaction);

        Mockito.verify(successProcessor, Mockito.times(3)).process(transaction);
        Mockito.verify(channel).writeAndFlush(Mockito.any());
    }

    @Test
    public void testRequestAbort() {
        Mockito.when(transaction.isRequest()).thenReturn(true);
        List<TransactionProcessor> requestProcessors = Arrays.asList(
                successProcessor,
                failProcessor,
                successProcessor
        );
        Mockito.when(configuration.getRequestProcessors()).thenReturn(requestProcessors);

        handler.channelRead(channelHandlerContext, transaction);

        Mockito.verify(successProcessor, Mockito.times(1)).process(transaction);
        Mockito.verify(failProcessor, Mockito.times(1)).process(transaction);
        Mockito.verify(channel).writeAndFlush(Mockito.any());
    }

    @Test
    public void testRequestProcessorsRefresh() {
        Mockito.when(transaction.isRequest()).thenReturn(true);
        List<TransactionProcessor> requestProcessors = Collections.singletonList(successProcessor);
        Mockito.when(configuration.getRequestProcessors()).thenReturn(requestProcessors);

        int n = 10;
        for (int i = 0; i < n; ++i) {
            handler.channelRead(channelHandlerContext, transaction);
        }

        Mockito.verify(configuration, Mockito.times(n)).getRequestProcessors();
        Mockito.verify(successProcessor, Mockito.times(n)).process(transaction);
        Mockito.verify(channel, Mockito.times(n)).writeAndFlush(Mockito.any());
    }

    @Test
    public void testResponse() {
        Mockito.when(transaction.isResponse()).thenReturn(true);
        List<TransactionProcessor> responseProcessors = Arrays.asList(
                successProcessor,
                successProcessor,
                successProcessor
        );
        Mockito.when(configuration.getResponseProcessors()).thenReturn(responseProcessors);

        handler.channelRead(channelHandlerContext, transaction);

        Mockito.verify(successProcessor, Mockito.times(3)).process(transaction);
        Mockito.verify(channel).writeAndFlush(Mockito.any());
    }

    @Test
    public void testResponseAbort() {
        Mockito.when(transaction.isResponse()).thenReturn(true);
        List<TransactionProcessor> responseProcessors = Arrays.asList(
                successProcessor,
                failProcessor,
                successProcessor
        );
        Mockito.when(configuration.getResponseProcessors()).thenReturn(responseProcessors);

        handler.channelRead(channelHandlerContext, transaction);

        Mockito.verify(successProcessor, Mockito.times(1)).process(transaction);
        Mockito.verify(failProcessor, Mockito.times(1)).process(transaction);
        Mockito.verify(channel).writeAndFlush(Mockito.any());
    }

    @Test
    public void testResponseProcessorsRefresh() {
        Mockito.when(transaction.isResponse()).thenReturn(true);
        List<TransactionProcessor> responseProcessors = Collections.singletonList(successProcessor);
        Mockito.when(configuration.getResponseProcessors()).thenReturn(responseProcessors);

        int n = 10;
        for (int i = 0; i < n; ++i) {
            handler.channelRead(channelHandlerContext, transaction);
        }

        Mockito.verify(configuration, Mockito.times(n)).getResponseProcessors();
        Mockito.verify(successProcessor, Mockito.times(n)).process(transaction);
        Mockito.verify(channel, Mockito.times(n)).writeAndFlush(Mockito.any());
    }
}

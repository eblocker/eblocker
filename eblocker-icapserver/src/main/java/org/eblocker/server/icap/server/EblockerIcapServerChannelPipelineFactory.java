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
package org.eblocker.server.icap.server;

import ch.mimo.netty.handler.codec.icap.IcapChunkAggregator;
import ch.mimo.netty.handler.codec.icap.IcapChunkSeparator;
import ch.mimo.netty.handler.codec.icap.IcapRequestDecoder;
import ch.mimo.netty.handler.codec.icap.IcapResponseEncoder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.eblocker.server.icap.ch.mimo.icap.IcapRequestHandler;
import org.eblocker.server.icap.ch.mimo.icap.IcapResponseHandler;
import org.eblocker.server.icap.logging.EblockerLoggingHandler;
import org.eblocker.server.icap.transaction.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EblockerIcapServerChannelPipelineFactory extends ChannelInitializer {

    private static final Logger ICAP_LOG = LoggerFactory.getLogger("ICAP");

    // Configuration parameters:
    @Inject
    @Named("maxAggregationSize")
    long maxAggregationSize;
    @Inject
    @Named("maxInitialLineLength")
    int maxInitialLineLength;
    @Inject
    @Named("maxIcapHeaderSize")
    int maxIcapHeaderSize;
    @Inject
    @Named("maxHttpHeaderSize")
    int maxHttpHeaderSize;
    @Inject
    @Named("maxChunkSize")
    int maxChunkSize;

    private ChannelHandler logger = new EblockerLoggingHandler();
    private ChannelHandler icapRequestor = new IcapRequestHandler();
    private ChannelHandler icapResponder = new IcapResponseHandler();
    private ChannelHandler transactor;
    private IcapResponseEncoder icapResponseEncoder = new IcapResponseEncoder();
    private IcapChunkSeparator icapChunkSeparator;

    @Inject
    public EblockerIcapServerChannelPipelineFactory(TransactionHandler transactor, @Named("separationSize") int separationSize) {
        super();
        this.transactor = transactor;
        icapChunkSeparator = new IcapChunkSeparator(separationSize);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        if (ICAP_LOG.isDebugEnabled()) {
            pipeline.addLast("logger", logger);
        }

        pipeline.addLast("decoder", new IcapRequestDecoder(maxInitialLineLength, maxIcapHeaderSize, maxHttpHeaderSize, maxChunkSize));
        pipeline.addLast("chunkAggregator", new IcapChunkAggregator(maxAggregationSize));
        pipeline.addLast("encoder", icapResponseEncoder);
        pipeline.addLast("chunkSeparator", icapChunkSeparator);
        pipeline.addLast("icapRequestor", icapRequestor);
        pipeline.addLast("icapResponder", icapResponder);
        pipeline.addLast("transactor", transactor);
    }
}

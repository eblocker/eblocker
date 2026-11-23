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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.eblocker.server.common.recorder.TransactionRecorder;
import org.eblocker.server.common.system.FatalErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ChannelHandler.Sharable
@Singleton
public class TransactionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(TransactionHandler.class);
    private static final Logger OPTIMIZE_LOG = LoggerFactory.getLogger("OPTIMIZE");

    private final TransactionProcessorsConfiguration configuration;

    private final TransactionRecorder transactionRecorder;
    private final FatalErrorHandler fatalErrorHandler;

    public TransactionHandler(
            TransactionProcessorsConfiguration configuration,
            TransactionRecorder transactionRecorder
    ) {
        this(configuration, transactionRecorder, FatalErrorHandler.noOp());
    }

    @Inject
    public TransactionHandler(
            TransactionProcessorsConfiguration configuration,
            TransactionRecorder transactionRecorder,
            FatalErrorHandler fatalErrorHandler
    ) {
        log.info("Creating new TransactionHandler");
        this.configuration = configuration;
        this.transactionRecorder = transactionRecorder;
        this.fatalErrorHandler = fatalErrorHandler != null ? fatalErrorHandler : FatalErrorHandler.noOp();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Transaction)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Transaction transaction = (Transaction) msg;

        log.debug("Received transaction:\n>>>>---------------------------{}>>>>---------------------------\n", transaction);

        try {
            boolean outbound;
            if (transaction.isRequest()) {
                log.debug("Handling REQMOD Transaction for {}", transaction.getUrl());
                outbound = true;
                processRequest(transaction);
            } else {//isResponse
                log.debug("Handling RESPMOD Transaction for {}, ContentType={}{}", transaction.getUrl(), transaction.getContentType(), (transaction.isPreview() ? " [PREVIEW]" : ""));
                outbound = false;
                processResponse(transaction);
            }

            transactionRecorder.addTransaction(transaction, outbound);
        } catch (Error e) {
            log.error("Fatal error processing ICAP transaction for {}", transaction.getUrl(), e);
            transaction.setComplete(true);
            ctx.channel().writeAndFlush(transaction);
            fatalErrorHandler.onFatalError("ICAP TransactionHandler channelRead", e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to process ICAP transaction for {}, failing open", transaction.getUrl(), e);
            transaction.setComplete(true);
        }

        ctx.channel().writeAndFlush(transaction);
        log.debug("Processed transaction:\n>>>>---------------------------{}>>>>---------------------------\n", transaction);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled ICAP TransactionHandler exception", cause);
        if (cause instanceof Error) {
            fatalErrorHandler.onFatalError("ICAP TransactionHandler exceptionCaught", cause);
        }
        ctx.close();
    }

    private void processRequest(Transaction transaction) {
        OPTIMIZE_LOG.info("Processing request: {} ...", transaction.getUrl());
        process(transaction, configuration.getRequestProcessors());
    }

    private void processResponse(Transaction transaction) {
        OPTIMIZE_LOG.info("Processing response: {} ...", transaction.getUrl());
        process(transaction, configuration.getResponseProcessors());
    }

    private void process(Transaction transaction, List<TransactionProcessor> processors) {
        for (TransactionProcessor processor : processors) {
            if (!processor.process(transaction)) {
                return;
            }
        }
    }
}

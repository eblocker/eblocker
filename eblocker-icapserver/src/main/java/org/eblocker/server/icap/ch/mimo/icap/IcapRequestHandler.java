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
package org.eblocker.server.icap.ch.mimo.icap;

import ch.mimo.netty.handler.codec.icap.DefaultIcapResponse;
import ch.mimo.netty.handler.codec.icap.IcapHeaders;
import ch.mimo.netty.handler.codec.icap.IcapMethod;
import ch.mimo.netty.handler.codec.icap.IcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapResponse;
import ch.mimo.netty.handler.codec.icap.IcapResponseStatus;
import ch.mimo.netty.handler.codec.icap.IcapVersion;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.eblocker.server.common.util.DateUtil;
import org.eblocker.server.icap.server.EblockerIcapServerConstants;
import org.eblocker.server.icap.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ChannelHandler.Sharable
public class IcapRequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(IcapRequestHandler.class);

    protected static final String SUPPORTED_METHODS = "REQMOD, RESPMOD";

    /**
     * ICAP TTL reported in OPTIONS response
     */
    protected static final int OPTIONS_TTL = 3600;

    /**
     * ICAP preview size reported in OPTIONS response
     */
    protected static final int PREVIEW_SIZE = 0;

    /**
     * ICAP maximum concurrent connections reported in OPTIONS response
     */
    protected static final int MAX_CONNECTIONS = 1000;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof IcapRequest)) {
            log.warn("Expected incoming message of type IcapRequest, but found: {}", msg.getClass());
            ctx.fireChannelRead(msg);
            return;
        }

        IcapRequest icapRequest = (IcapRequest) msg;
        if (icapRequest.getMethod().equals(IcapMethod.OPTIONS)) {
            IcapResponse icapResponse = generateOptionsReponse(icapRequest);
            icapRequest.release();
            log.debug("Responding with direct IcapResponse without processing payload: {}", icapResponse);
            ctx.channel().writeAndFlush(icapResponse);
            return;
        }

        Transaction transaction = new IcapTransaction(icapRequest);
        ctx.fireChannelRead(transaction);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && "Connection reset by peer".equals(cause.getMessage())) {
            log.debug("Connection already closed", cause);
        } else {
            log.warn("Unexpected error in ICAP request handler: ", cause);
        }
        ctx.channel().close();
    }

    private IcapResponse generateOptionsReponse(IcapRequest icapRequest) {
        IcapResponse icapResponse = new DefaultIcapResponse(IcapVersion.ICAP_1_0, IcapResponseStatus.OK);
        icapResponse.addHeader(IcapHeaders.Names.METHODS, SUPPORTED_METHODS);
        icapResponse.addHeader(IcapHeaders.Names.OPTIONS_TTL, OPTIONS_TTL);
        icapResponse.addHeader(IcapHeaders.Names.MAX_CONNECTIONS, MAX_CONNECTIONS);
        icapResponse.addHeader(IcapHeaders.Names.ALLOW, "204, 206");
        icapResponse.addHeader(IcapHeaders.Names.SERVICE_ID, EblockerIcapServerConstants.SERVICE_ID);
        icapResponse.addHeader(IcapHeaders.Names.DATE, DateUtil.formatCurrentTime());
        icapResponse.addHeader(IcapHeaders.Names.ISTAG, EblockerIcapServerConstants.SERVICE_TAG);
        icapResponse.addHeader(IcapHeaders.Names.PREVIEW, PREVIEW_SIZE);
        icapResponse.addHeader(IcapHeaders.Names.TRANSFER_PREVIEW, "*");
        return icapResponse;
    }

}

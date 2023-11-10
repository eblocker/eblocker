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
import ch.mimo.netty.handler.codec.icap.IcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapResponse;
import ch.mimo.netty.handler.codec.icap.IcapResponseStatus;
import ch.mimo.netty.handler.codec.icap.IcapVersion;
import com.google.common.base.Splitter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.eblocker.server.common.util.DateUtil;
import org.eblocker.server.icap.server.EblockerIcapServerConstants;
import org.eblocker.server.icap.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class IcapResponseHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(IcapResponseHandler.class);

    private static final Splitter ALLOW_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof IcapTransaction)) {
            ctx.writeAndFlush(msg);
            return;
        }

        Transaction transaction = (Transaction) msg;
        IcapRequest icapRequest = ((IcapTransaction) transaction).getIcapRequest();
        IcapResponse icapResponse = createIcapResponse(transaction, icapRequest);
        if (icapResponse.getStatus() != IcapResponseStatus.CONTINUE) {
            icapRequest.release();
        }

        log.debug("Writing ICAP response: {}", icapResponse);
        ctx.writeAndFlush(icapResponse);
    }

    private IcapResponse createIcapResponse(Transaction transaction, IcapRequest icapRequest) {
        if (transaction.isPreview()) {
            if (transaction.isComplete()) {
                if (transaction.isContentChanged()) {
                    // Create response with modified content
                    return createIcapResponse(transaction, IcapResponseStatus.OK);
                } else if (transaction.isHeadersChanged() && isResponseAllowed(icapRequest, IcapResponseStatus.PARTIAL_CONTENT)) {
                    return createIcapResponse(transaction, IcapResponseStatus.PARTIAL_CONTENT);
                } else {
                    // Full content not needed
                    return createIcapResponse(transaction, IcapResponseStatus.NO_CONTENT);
                }
            } else {
                // Request full content from client
                return createIcapResponse(transaction, IcapResponseStatus.CONTINUE);
            }
        }
        if (!transaction.isHeadersChanged() && !transaction.isContentChanged()) {
            if (isResponseAllowed(icapRequest, IcapResponseStatus.NO_CONTENT)) {
                // Return empty message. Client will process unmodified content.
                return createIcapResponse(transaction, IcapResponseStatus.NO_CONTENT);
            }
        }
        // Create response with modified content
        return createIcapResponse(transaction, IcapResponseStatus.OK);
    }

    private IcapResponse createIcapResponse(Transaction transaction, IcapResponseStatus status) {
        IcapResponse icapResponse = new DefaultIcapResponse(IcapVersion.ICAP_1_0, status);
        icapResponse.addHeader(IcapHeaders.Names.SERVICE_ID, EblockerIcapServerConstants.SERVICE_ID);
        icapResponse.addHeader(IcapHeaders.Names.DATE, DateUtil.formatCurrentTime());
        icapResponse.addHeader(IcapHeaders.Names.ISTAG, EblockerIcapServerConstants.SERVICE_TAG);
        if (status.equals(IcapResponseStatus.OK)) {
            if (transaction.isRequest()) {
                transaction.getRequest().retain();
                icapResponse.setHttpRequest(transaction.getRequest());
            } else if (transaction.isResponse()) {
                transaction.getResponse().retain();
                icapResponse.setHttpResponse(transaction.getResponse());
            }
        }
        if (status.equals(IcapResponseStatus.PARTIAL_CONTENT)) {
            if (transaction.isRequest()) {
                icapResponse.setHttpRequest(transaction.getRequest().replace(Unpooled.EMPTY_BUFFER));
            } else if (transaction.isResponse()) {
                icapResponse.setHttpResponse(transaction.getResponse().replace(Unpooled.EMPTY_BUFFER));
            }
            icapResponse.setUseOriginalBody(0);
        }
        return icapResponse;
    }

    private boolean isResponseAllowed(IcapRequest request, IcapResponseStatus status) {
        if (!request.containsHeader(IcapHeaders.Names.ALLOW)) {
            return false;
        }

        return request
                .getHeaders(IcapHeaders.Names.ALLOW)
                .stream()
                .map(ALLOW_SPLITTER::splitToList)
                .anyMatch(l -> l.contains(String.valueOf(status.getCode())));
    }
}

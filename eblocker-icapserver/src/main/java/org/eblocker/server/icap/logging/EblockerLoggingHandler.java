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
package org.eblocker.server.icap.logging;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

@ChannelHandler.Sharable
public class EblockerLoggingHandler implements ChannelInboundHandler, ChannelOutboundHandler {

    private static final Logger ICAP_LOG = LoggerFactory.getLogger("ICAP");

    private enum Event { WRITE, READ, EXCEPTION }
    private enum StreamDirection { UP, DOWN }

    private static final int CONTENT_SIZE_ALLOWED = 1024*1024;
    private static final int CONTENT_SIZE_CUTOFF = 256;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        log(Event.WRITE, msg, StreamDirection.DOWN);
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logException(cause);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log(Event.READ, msg, StreamDirection.UP);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        ctx.fireUserEventTriggered(evt);
    }

    private void log(Event e, Object message, StreamDirection d) {
        if (ICAP_LOG.isDebugEnabled() && message instanceof ByteBuf) {
            String content = formatBuffer((ByteBuf) message, d);
            ICAP_LOG.debug("{}: {}", e, content);
        }
    }

    private void logException(Throwable cause) {
        ICAP_LOG.debug("{}", Event.EXCEPTION, cause);
    }

    private static String formatBuffer(ByteBuf buf, StreamDirection d) {
        int readableBytes = buf.readableBytes();
        byte[] buffer = new byte[(readableBytes > CONTENT_SIZE_ALLOWED ? CONTENT_SIZE_CUTOFF : readableBytes)];
        buf.getBytes(buf.readerIndex(), buffer);

        String direction = null;
        if (d == StreamDirection.UP) {
            direction = ">>>>>";
        } else if (d == StreamDirection.DOWN) {
            direction = "<<<<<";
        }

        String content = new String(buffer);

        return
            "\n"+direction+"------------------BEGIN MESSAGE---------------------------"+direction+"\n"+
            content+(readableBytes > buffer.length ? "[..."+(readableBytes-buffer.length)+" bytes removed...]" : "")+
            "\n"+direction+"------------------END MESSAGE-----------------------------"+direction+"\n";
    }

}

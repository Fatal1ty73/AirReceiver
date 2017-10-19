/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs RTSP requests and responses.
 */
public class RtspLoggingHandler extends ChannelDuplexHandler {
    private static final Logger s_logger = Logger.getLogger(RtspLoggingHandler.class.getName());

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        s_logger.info("Client " + ctx.channel().remoteAddress() + " connected on " + ctx.channel().localAddress());
    }


    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg)
            throws Exception {
        FullHttpRequest fullHttpRequest = (FullHttpRequest)msg;
        final Level level = Level.INFO;
        if (s_logger.isLoggable(level)) {
            String content = fullHttpRequest.content().toString(Charset.defaultCharset());


            final StringBuilder s = new StringBuilder();
            s.append(">");
            s.append(fullHttpRequest.method());
            s.append(" ");
            s.append(fullHttpRequest.uri());
            s.append("\n");
            for (final Map.Entry<String, String> header : fullHttpRequest.headers().entries()) {
                s.append("  ");
                s.append(header.getKey());
                s.append(": ");
                s.append(header.getValue());
                s.append("\n");
            }
            s.append(content);
            s_logger.log(Level.INFO, s.toString());
        }
        fullHttpRequest.retain();
        ctx.fireChannelRead(fullHttpRequest);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        final FullHttpResponse resp = (FullHttpResponse) msg;

        final Level level = Level.INFO;
        if (s_logger.isLoggable(level)) {
            final StringBuilder s = new StringBuilder();
            s.append("<");
            s.append(resp.status().code());
            s.append(" ");
            s.append(resp.status().reasonPhrase());
            s.append("\n");
            for (final Map.Entry<String, String> header : resp.headers().entries()) {
                s.append("  ");
                s.append(header.getKey());
                s.append(": ");
                s.append(header.getValue());
                s.append("\n");
            }
            s_logger.log(Level.INFO, s.toString());
        }

        super.write(ctx, msg, promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    s_logger.log(Level.WARNING, future.cause().getMessage());
                }
            }
        }));
        super.flush(ctx);
    }
}

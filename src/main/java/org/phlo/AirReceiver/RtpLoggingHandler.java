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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs incoming and outgoing RTP packets
 */
@ChannelHandler.Sharable
public class RtpLoggingHandler extends ChannelDuplexHandler {
    private static final Logger s_logger = Logger.getLogger(RtpLoggingHandler.class.getName());

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RtpPacket rtpPacket = (RtpPacket)msg;
        final Level level = getPacketLevel(rtpPacket);
        if (s_logger.isLoggable(level))
            s_logger.log(level, ctx.channel().remoteAddress() + "> " + rtpPacket.toString());
        ctx.fireChannelRead(rtpPacket);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof RtpPacket) {
            final RtpPacket packet = (RtpPacket) msg;

            final Level level = getPacketLevel(packet);
            if (s_logger.isLoggable(level))
                s_logger.log(level, ctx.channel().remoteAddress() + "< " + packet.toString());
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

    private Level getPacketLevel(final RtpPacket packet) {
        if (packet instanceof RaopRtpPacket.Audio)
            return Level.FINEST;
        else if (packet instanceof RaopRtpPacket.RetransmitRequest)
            return Level.FINEST;
        else if (packet instanceof RaopRtpPacket.Timing)
            return Level.FINEST;
        else
            return Level.FINE;
    }
}
